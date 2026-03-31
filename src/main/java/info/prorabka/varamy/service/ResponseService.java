package info.prorabka.varamy.service;

import info.prorabka.varamy.dto.request.ResponseRequest;
import info.prorabka.varamy.dto.response.AdResponse;
import info.prorabka.varamy.dto.response.MyResponseAdResponse;
import info.prorabka.varamy.dto.response.ResponseResponse;
import info.prorabka.varamy.entity.Ad;
import info.prorabka.varamy.entity.Response;
import info.prorabka.varamy.entity.User;
import info.prorabka.varamy.exception.BadRequestException;
import info.prorabka.varamy.exception.ResourceNotFoundException;
import info.prorabka.varamy.exception.UnauthorizedException;
import info.prorabka.varamy.mapper.AdMapper;
import info.prorabka.varamy.mapper.ResponseMapper;
import info.prorabka.varamy.repository.AdRepository;
import info.prorabka.varamy.repository.ResponseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResponseService {

    private final ResponseRepository responseRepository;
    private final AdRepository adRepository;
    private final ResponseMapper responseMapper;
    private final UserService userService;
    private final AdMapper adMapper;

    // ============================== СОЗДАНИЕ ОТКЛИКА ==============================

    @Transactional
    public ResponseResponse createResponse(UUID adId, UUID userId, ResponseRequest request) {
        Ad ad = adRepository.findById(adId)
                .orElseThrow(() -> new ResourceNotFoundException("Объявление не найдено"));
        User user = userService.getUserById(userId);

        if (ad.getAuthor().getId().equals(userId)) {
            throw new BadRequestException("Нельзя откликнуться на своё объявление");
        }
        if (responseRepository.existsByAdAndUser(ad, user)) {
            throw new BadRequestException("Вы уже откликнулись на это объявление");
        }
        if (ad.getStatus() == Ad.AdStatus.FILLED) {
            throw new BadRequestException("Это объявление уже заполнено, отклик невозможен");
        }

        Response response = new Response();
        response.setAd(ad);
        response.setUser(user);
        response.setMessage(request != null ? request.getMessage() : null);
        response.setResponseRole(request != null ? request.getResponseRole() : null);
        response.setStatus(Response.ResponseStatus.PENDING);

        response = responseRepository.save(response);
        return responseMapper.toResponse(response);
    }

    // ============================== ПОЛУЧЕНИЕ ОТКЛИКОВ ==============================

    @Transactional(readOnly = true)
    public List<ResponseResponse> getResponsesForAd(UUID adId, UUID userId, boolean isAdmin) {
        Ad ad = adRepository.findById(adId)
                .orElseThrow(() -> new ResourceNotFoundException("Объявление не найдено"));

        // Используем метод с JOIN FETCH
        List<Response> responses = responseRepository.findByAdWithUserAndProfile(ad);

        if (!isAdmin && !ad.getAuthor().getId().equals(userId)) {
            responses = responses.stream()
                    .filter(r -> r.getStatus() == Response.ResponseStatus.APPROVED)
                    .collect(Collectors.toList());
        }

        return responses.stream()
                .map(responseMapper::toResponse)
                .collect(Collectors.toList());
    }

    // ============================== ИЗМЕНЕНИЕ СТАТУСА ОТКЛИКА ==============================

    @Transactional
    public ResponseResponse updateResponseStatus(UUID responseId, UUID userId,
                                                 Response.ResponseStatus status, boolean isAdmin) {
        Response response = responseRepository.findById(responseId)
                .orElseThrow(() -> new ResourceNotFoundException("Отклик не найден"));
        Ad ad = response.getAd();

        if (!isAdmin && !ad.getAuthor().getId().equals(userId)) {
            throw new UnauthorizedException("Нет прав на изменение статуса этого отклика");
        }

        if (response.getStatus() == status) {
            return responseMapper.toResponse(response);
        }

        // --- ПРИНЯТИЕ ОТКЛИКА (APPROVED) ---
        if (status == Response.ResponseStatus.APPROVED) {
            // [ИСПРАВЛЕНИЕ 1] Нельзя принять отклик, если объявление уже заполнено
            if (ad.getStatus() == Ad.AdStatus.FILLED) {
                throw new BadRequestException("Нельзя принять отклик на заполненное объявление");
            }

            // Далее идёт существующая логика проверки лимитов и ролей
            if (ad.getType() == 1 && ad.getSubType() == 1) { // вратарь
                if (response.getResponseRole() != Response.ResponseRole.GOALIE) {
                    throw new BadRequestException("Для этого объявления можно откликаться только вратарём");
                }
                if (ad.getAcceptedGoaliesCount() >= ad.getGoaliesCount()) {
                    throw new BadRequestException("Все места вратарей уже заняты");
                }
            } else if (ad.getType() == 1 && ad.getSubType() == 2) { // полевые
                Response.ResponseRole role = response.getResponseRole();
                if (role == Response.ResponseRole.DEFENDER) {
                    if (ad.getAcceptedDefendersCount() >= ad.getDefendersCount()) {
                        throw new BadRequestException("Все места защитников уже заняты");
                    }
                } else if (role == Response.ResponseRole.FORWARD) {
                    if (ad.getAcceptedForwardsCount() >= ad.getForwardsCount()) {
                        throw new BadRequestException("Все места нападающих уже заняты");
                    }
                } else {
                    throw new BadRequestException("Для этого объявления можно откликаться только защитником или нападающим");
                }
            } else {
                // Остальные типы (2,3,4)
                if (ad.getAcceptedResponsesCount() >= 1) {
                    throw new BadRequestException("На это объявление уже есть принятый отклик");
                }
            }

            response.setStatus(Response.ResponseStatus.APPROVED);
            updateAcceptedCounts(ad, response.getResponseRole(), true);
            checkAndUpdateAdStatus(ad);

            // --- ОТМЕНА ПРИНЯТИЯ (PENDING из APPROVED) ---
        } else if (status == Response.ResponseStatus.PENDING &&
                response.getStatus() == Response.ResponseStatus.APPROVED) {
            response.setStatus(Response.ResponseStatus.PENDING);
            updateAcceptedCounts(ad, response.getResponseRole(), false);
            checkAndUpdateAdStatus(ad);

            // --- ОТКЛОНЕНИЕ ОТКЛИКА (REJECTED) ---
        } else if (status == Response.ResponseStatus.REJECTED) {
            if (response.getStatus() == Response.ResponseStatus.APPROVED) {
                throw new BadRequestException("Нельзя отклонить принятый отклик, сначала отмените принятие");
            }
            response.setStatus(Response.ResponseStatus.REJECTED);
            // [ИСПРАВЛЕНИЕ 2] Удаляем некорректный блок изменения статуса объявления
            // (раньше здесь пытались вернуть FILLED -> ACTIVE, что неправильно)

        } else {
            throw new BadRequestException("Недопустимый статус");
        }

        response = responseRepository.save(response);
        return responseMapper.toResponse(response);
    }

    // ============================== УДАЛЕНИЕ ОТКЛИКА ==============================

    @Transactional
    public void deleteResponse(UUID responseId, UUID userId, boolean isAdmin) {
        Response response = responseRepository.findById(responseId)
                .orElseThrow(() -> new ResourceNotFoundException("Отклик не найден"));

        if (!isAdmin && !response.getUser().getId().equals(userId)) {
            throw new UnauthorizedException("Нет прав на удаление этого отклика");
        }

        Ad ad = response.getAd();
        boolean wasApproved = response.getStatus() == Response.ResponseStatus.APPROVED;
        responseRepository.delete(response);

        if (wasApproved) {
            updateAcceptedCounts(ad, response.getResponseRole(), false);
            checkAndUpdateAdStatus(ad);
        }
    }

    // ============================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ==============================

    private void updateAcceptedCounts(Ad ad, Response.ResponseRole role, boolean increment) {
        int delta = increment ? 1 : -1;
        if (ad.getType() == 1 && ad.getSubType() == 1) { // вратарь
            ad.setAcceptedGoaliesCount(ad.getAcceptedGoaliesCount() + delta);
        } else if (ad.getType() == 1 && ad.getSubType() == 2) { // полевые
            if (role == Response.ResponseRole.DEFENDER) {
                ad.setAcceptedDefendersCount(ad.getAcceptedDefendersCount() + delta);
            } else if (role == Response.ResponseRole.FORWARD) {
                ad.setAcceptedForwardsCount(ad.getAcceptedForwardsCount() + delta);
            }
        } else {
            // Остальные типы
            ad.setAcceptedResponsesCount(ad.getAcceptedResponsesCount() + delta);
        }
        adRepository.save(ad);
    }

    private void checkAndUpdateAdStatus(Ad ad) {
        boolean changed = false;
        if (ad.getType() == 1 && ad.getSubType() == 1) { // вратарь
            boolean isFull = ad.getAcceptedGoaliesCount() >= ad.getGoaliesCount();
            if (isFull && ad.getStatus() != Ad.AdStatus.FILLED) {
                ad.setStatus(Ad.AdStatus.FILLED);
                changed = true;
            } else if (!isFull && ad.getStatus() == Ad.AdStatus.FILLED) {
                ad.setStatus(Ad.AdStatus.ACTIVE);
                changed = true;
            }
        } else if (ad.getType() == 1 && ad.getSubType() == 2) { // полевые
            boolean isFull = ad.getAcceptedDefendersCount() >= ad.getDefendersCount() &&
                    ad.getAcceptedForwardsCount() >= ad.getForwardsCount();
            if (isFull && ad.getStatus() != Ad.AdStatus.FILLED) {
                ad.setStatus(Ad.AdStatus.FILLED);
                changed = true;
            } else if (!isFull && ad.getStatus() == Ad.AdStatus.FILLED) {
                ad.setStatus(Ad.AdStatus.ACTIVE);
                changed = true;
            }
        } else {
            // Остальные типы (2,3,4)
            boolean hasApproved = ad.getAcceptedResponsesCount() >= 1;
            if (hasApproved && ad.getStatus() != Ad.AdStatus.FILLED) {
                ad.setStatus(Ad.AdStatus.FILLED);
                changed = true;
            } else if (!hasApproved && ad.getStatus() == Ad.AdStatus.FILLED) {
                ad.setStatus(Ad.AdStatus.ACTIVE);
                changed = true;
            }
        }
        if (changed) {
            adRepository.save(ad);
        }
    }

    @Transactional(readOnly = true)
    public Page<MyResponseAdResponse> getMyResponses(UUID userId, Pageable pageable) {
        Page<Response> responses = responseRepository.findResponsesByUserIdWithAds(userId, pageable);

        return responses.map(response -> {
            MyResponseAdResponse dto = new MyResponseAdResponse();

            // Маппим объявление
            AdResponse adResponse = adMapper.toResponse(response.getAd());
            dto.setAd(adResponse);

            // Маппим отклик пользователя
            ResponseResponse responseResponse = responseMapper.toResponse(response);
            dto.setMyResponse(responseResponse);

            return dto;
        });
    }


}