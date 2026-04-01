package info.prorabka.varamy.service;

import info.prorabka.varamy.dto.request.AdRequest;
import info.prorabka.varamy.dto.response.AdResponse;
import info.prorabka.varamy.entity.Ad;
import info.prorabka.varamy.entity.City;
import info.prorabka.varamy.entity.Profile;
import info.prorabka.varamy.entity.User;
import info.prorabka.varamy.exception.BadRequestException;
import info.prorabka.varamy.exception.ResourceNotFoundException;
import info.prorabka.varamy.exception.UnauthorizedException;
import info.prorabka.varamy.mapper.AdMapper;
import info.prorabka.varamy.repository.AdRepository;
import info.prorabka.varamy.repository.CityRepository;
import lombok.Builder;
import lombok.Value;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdService {

    private final AdRepository adRepository;
    private final CityRepository cityRepository;
    private final AdMapper adMapper;
    private final UserService userService;

    // ============= ПУБЛИЧНЫЕ МЕТОДЫ (ACTIVE и FILLED) =============

    public Page<AdResponse> getAds(Long cityId, Integer type, Integer subType,
                                   List<String> level, Pageable pageable) {
        if (cityId == null) {
            throw new BadRequestException("Параметр cityId обязателен");
        }
        return adRepository.findActiveAndFilledAds(cityId, type, subType, level, null, pageable)
                .map(adMapper::toResponse);
    }

    public Page<AdResponse> getAllActiveAds(Integer type, Integer subType, List<String> level, Pageable pageable) {
        return adRepository.findAllActiveAndFilledPublic(type, subType, level, pageable)
                .map(adMapper::toResponse);
    }

    public Page<AdResponse> getMainPageAds(Integer type, Integer subType, List<String> level, Pageable pageable) {
        return adRepository.findMainPageAdsPublic(
                        List.of(Ad.AdStatus.ACTIVE, Ad.AdStatus.FILLED),
                        type, subType, level, pageable)
                .map(adMapper::toResponse);
    }

    // ============= ПОЛУЧЕНИЕ ОБЪЯВЛЕНИЯ ПО ID =============

    public AdResponse getAdById(UUID id) {
        Ad ad = adRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Объявление не найдено"));
        return adMapper.toResponse(ad);
    }

    // ============= МЕТОДЫ ДЛЯ АВТОРА =============

    public Page<AdResponse> getMyAds(UUID userId, Pageable pageable) {
        User user = userService.getUserById(userId);
        return adRepository.findByAuthor(user, pageable)
                .map(adMapper::toResponse);
    }

    // ============= АДМИНИСТРАТИВНЫЕ МЕТОДЫ =============

    public Page<AdResponse> getAdsAdmin(Long cityId, Integer type, Integer subType,
                                        Ad.AdStatus status, List<String> level,
                                        UUID authorId, Pageable pageable) {
        return adRepository.findWithFiltersAdmin(cityId, type, subType, status, level, authorId, pageable)
                .map(adMapper::toResponse);
    }

    // ============= МЕТОДЫ ДЛЯ МОДЕРАЦИИ =============

    public Page<AdResponse> getAdsForModeration(Pageable pageable) {
        return adRepository.findByStatus(Ad.AdStatus.MODERATION, pageable)
                .map(adMapper::toResponse);
    }

    // ============= СОЗДАНИЕ ОБЪЯВЛЕНИЯ =============

    @Transactional
    public AdResponse createAd(UUID authorId, AdRequest request) {
        User author = userService.getUserById(authorId);
        Profile profile = author.getProfile();

        validateAdRequest(request);

        City city = cityRepository.findById(request.getCityId())
                .orElseThrow(() -> new ResourceNotFoundException("Город не найден"));

        Ad ad = adMapper.toEntity(request, city);
        ad.setAuthor(author);
        ad.setCity(city);
        ad.setStatus(Ad.AdStatus.ACTIVE);

        String fullName = (profile.getFirstName() != null ? profile.getFirstName() : "") +
                (profile.getLastName() != null ? " " + profile.getLastName() : "");
        ad.setContactName(fullName.trim().isEmpty() ? author.getPhone() : fullName.trim());
        ad.setContactPhone(author.getPhone());

        if (request.getTeam() == null && profile.getTeam() != null) {
            ad.setTeam(profile.getTeam());
        }

        if (request.getLevel() != null && !request.getLevel().isEmpty()) {
            List<String> validLevels = List.of("A", "B", "C", "D", "E", "F", "G", "H");
            for (String level : request.getLevel()) {
                if (!validLevels.contains(level)) {
                    throw new BadRequestException("Неверный уровень: " + level + ". Допустимые значения: A-H");
                }
            }
            ad.setLevels(request.getLevel());
        }

        if (request.getType() == 1) {
            if (request.getSubType() == 1) {
                ad.setGoaliesCount(request.getGoaliesCount());
            } else if (request.getSubType() == 2) {
                ad.setDefendersCount(request.getDefendersCount());
                ad.setForwardsCount(request.getForwardsCount());
            }
        }

        ad.setAcceptedGoaliesCount(0);
        ad.setAcceptedDefendersCount(0);
        ad.setAcceptedForwardsCount(0);
        ad.setAcceptedResponsesCount(0);

        if (request.getEndTime() == null && !requiresEndTime(request.getType(), request.getSubType())) {
            ad.setEndTime(request.getStartTime());
        } else {
            ad.setEndTime(request.getEndTime());
        }

        ad = adRepository.save(ad);
        return adMapper.toResponse(ad);
    }

    // ============= ОБНОВЛЕНИЕ ОБЪЯВЛЕНИЯ =============

    @Transactional
    public AdResponse updateAd(UUID adId, UUID userId, AdRequest request, boolean isAdmin) {
        Ad ad = adRepository.findById(adId)
                .orElseThrow(() -> new ResourceNotFoundException("Объявление не найдено"));

        if (!isAdmin && !ad.getAuthor().getId().equals(userId)) {
            throw new UnauthorizedException("Нет прав на редактирование этого объявления");
        }

        validateAdRequest(request);

        City city = cityRepository.findById(request.getCityId())
                .orElseThrow(() -> new ResourceNotFoundException("Город не найден"));

        adMapper.updateAd(ad, request, city);

        if (request.getLevel() != null && !request.getLevel().isEmpty()) {
            List<String> validLevels = List.of("A", "B", "C", "D", "E", "F", "G", "H");
            for (String level : request.getLevel()) {
                if (!validLevels.contains(level)) {
                    throw new BadRequestException("Неверный уровень: " + level + ". Допустимые значения: A-H");
                }
            }
            ad.setLevels(request.getLevel());
        } else {
            ad.setLevels(null);
        }

        if (request.getType() == 1) {
            if (request.getSubType() == 1) {
                ad.setGoaliesCount(request.getGoaliesCount());
            } else if (request.getSubType() == 2) {
                ad.setDefendersCount(request.getDefendersCount());
                ad.setForwardsCount(request.getForwardsCount());
            }
        }

        if (!ad.getAuthor().getId().equals(userId)) {
            ad.setStatus(Ad.AdStatus.MODERATION);
        }

        if (request.getEndTime() == null && !requiresEndTime(request.getType(), request.getSubType())) {
            ad.setEndTime(request.getStartTime());
        } else {
            ad.setEndTime(request.getEndTime());
        }

        ad = adRepository.save(ad);
        return adMapper.toResponse(ad);
    }

    // ============= УДАЛЕНИЕ =============

    @Transactional
    public void deleteAd(UUID adId, UUID userId, boolean isAdmin) {
        Ad ad = adRepository.findById(adId)
                .orElseThrow(() -> new ResourceNotFoundException("Объявление не найдено"));

        if (!isAdmin && !ad.getAuthor().getId().equals(userId)) {
            throw new UnauthorizedException("Нет прав на удаление этого объявления");
        }

        adRepository.delete(ad);
    }

    // ============= МОДЕРАЦИЯ =============

    @Transactional
    public AdResponse moderateAd(UUID adId, boolean approve) {
        Ad ad = adRepository.findById(adId)
                .orElseThrow(() -> new ResourceNotFoundException("Объявление не найдено"));

        if (approve) {
            ad.setStatus(Ad.AdStatus.ACTIVE);
        } else {
            ad.setStatus(Ad.AdStatus.ARCHIVED);
        }

        ad = adRepository.save(ad);
        return adMapper.toResponse(ad);
    }

    // ============= АВТОМАТИЧЕСКАЯ АРХИВАЦИЯ =============

    @Scheduled(cron = "0 */20 * * * *")
    @Transactional
    public void archiveExpiredAds() {
        LocalDateTime now = LocalDateTime.now();
        List<Ad> expiredAds = adRepository.findByEndTimeBeforeAndStatusNot(now, Ad.AdStatus.ARCHIVED);

        int archivedCount = 0;
        for (Ad ad : expiredAds) {
            if (ad.getEndTime().isBefore(now)) {
                log.info("Архивация объявления id={}, endTime={}, status={}",
                        ad.getId(), ad.getEndTime(), ad.getStatus());
                ad.setStatus(Ad.AdStatus.ARCHIVED);
                archivedCount++;
            }
        }

        if (archivedCount > 0) {
            adRepository.saveAll(expiredAds);
            log.info("Архивировано {} объявлений", archivedCount);
        }
    }

    // ============= ОЧИСТКА СТАРЫХ АРХИВНЫХ ОБЪЯВЛЕНИЙ =============

    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanupOldArchivedAds() {
        LocalDateTime fiveMonthsAgo = LocalDateTime.now().minusMonths(5);

        long countBefore = adRepository.countByStatus(Ad.AdStatus.ARCHIVED);
        List<Ad> oldArchivedAds = adRepository.findByStatusAndCreatedAtBefore(
                Ad.AdStatus.ARCHIVED, fiveMonthsAgo);

        int deletedCount = oldArchivedAds.size();

        if (deletedCount > 0) {
            log.info("Удаление {} старых архивных объявлений (созданы до {})",
                    deletedCount, fiveMonthsAgo);
            adRepository.deleteAll(oldArchivedAds);

            long countAfter = adRepository.countByStatus(Ad.AdStatus.ARCHIVED);
            log.info("Очистка завершена. Удалено {} объявлений. Осталось архивных: {}",
                    deletedCount, countAfter);
        } else {
            log.debug("Старых архивных объявлений для удаления не найдено");
        }
    }

    // ============= СТАТИСТИКА =============

    public AdStatistics getAdStatistics() {
        return AdStatistics.builder()
                .totalActive(adRepository.countByStatus(Ad.AdStatus.ACTIVE))
                .totalModeration(adRepository.countByStatus(Ad.AdStatus.MODERATION))
                .totalFilled(adRepository.countByStatus(Ad.AdStatus.FILLED))
                .totalArchived(adRepository.countByStatus(Ad.AdStatus.ARCHIVED))
                .build();
    }

    // ============= ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ВАЛИДАЦИИ =============

    private void validateAdRequest(AdRequest request) {
        if (request.getSubType() == null) {
            throw new BadRequestException("Подтип объявления (subType) обязателен");
        }

        switch (request.getType()) {
            case 1:
                if (request.getSubType() != 1 && request.getSubType() != 2) {
                    throw new BadRequestException("Для типа 1 допустимы подтипы 1 (вратарь) или 2 (полевой)");
                }
                break;
            case 2:
                if (request.getSubType() != 1 && request.getSubType() != 2) {
                    throw new BadRequestException("Для типа 2 допустимы подтипы 1 (вратарь) или 2 (полевой)");
                }
                break;
            case 3:
                if (request.getSubType() != 1 && request.getSubType() != 2) {
                    throw new BadRequestException("Для типа 3 допустимы подтипы 1 (ищу) или 2 (предлагаю)");
                }
                break;
            case 4:
                if (request.getSubType() < 1 || request.getSubType() > 4) {
                    throw new BadRequestException("Для типа 4 допустимы подтипы 1-4 (судья, фотограф, медик, тренер)");
                }
                break;
            default:
                throw new BadRequestException("Неверный тип объявления");
        }

        if (isSingleRinkType(request.getType(), request.getSubType())) {
            if (request.getRinkIds() != null && request.getRinkIds().size() > 1) {
                throw new BadRequestException("Для этого типа объявления можно выбрать только один ЛДС");
            }
        }

        if (requiresEndTime(request.getType(), request.getSubType())) {
            if (request.getEndTime() == null) {
                throw new BadRequestException("Для этого типа объявления необходимо указать время окончания");
            }
            if (request.getStartTime() != null && request.getEndTime().isBefore(request.getStartTime())) {
                throw new BadRequestException("Время окончания не может быть раньше времени начала");
            }
        } else {
            if (request.getEndTime() != null) {
                throw new BadRequestException("Для этого типа объявления время окончания не требуется");
            }
        }

        if (request.getType() == 1) {
            if (request.getSubType() == 1) {
                if (request.getGoaliesCount() == null) {
                    throw new BadRequestException("Не указано количество вратарей");
                }
                if (request.getGoaliesCount() != 1 && request.getGoaliesCount() != 2) {
                    throw new BadRequestException("Количество вратарей может быть 1 или 2");
                }
            } else if (request.getSubType() == 2) {
                if (request.getDefendersCount() == null || request.getForwardsCount() == null) {
                    throw new BadRequestException("Не указано количество защитников или нападающих");
                }
                if (request.getDefendersCount() < 0 || request.getForwardsCount() < 0) {
                    throw new BadRequestException("Количество игроков не может быть отрицательным");
                }
                if (request.getDefendersCount() + request.getForwardsCount() == 0) {
                    throw new BadRequestException("Должен быть указан хотя бы один игрок");
                }
            }
        } else {
            if (request.getGoaliesCount() != null || request.getDefendersCount() != null || request.getForwardsCount() != null) {
                throw new BadRequestException("Для этого типа объявления количество игроков не указывается");
            }
        }

        if (!requiresLevel(request.getType(), request.getSubType())) {
            if (request.getLevel() != null && !request.getLevel().isEmpty()) {
                throw new BadRequestException("Для этого типа объявления уровни не указываются");
            }
        }
    }

    private boolean isSingleRinkType(Integer type, Integer subType) {
        return type == 1 || (type == 3 && subType == 2) || type == 4;
    }

    private boolean requiresEndTime(Integer type, Integer subType) {
        return type == 2 || (type == 3 && subType == 1);
    }

    private boolean requiresLevel(Integer type, Integer subType) {
        return type != 4;
    }

    @Value
    @Builder
    public static class AdStatistics {
        long totalActive;
        long totalModeration;
        long totalFilled;
        long totalArchived;
    }
}