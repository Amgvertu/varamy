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

    // ============================== ПОЛУЧЕНИЕ ОБЪЯВЛЕНИЙ ==============================

    public Page<AdResponse> getAds(Long cityId, Integer type, Integer subType,
                                   Ad.AdStatus status, List<String> level,
                                   UUID authorId, Pageable pageable) {
        if (cityId == null) {
            throw new BadRequestException("Параметр cityId обязателен");
        }
        return adRepository.findWithFilters(cityId, type, subType, status, level, authorId, pageable)
                .map(adMapper::toResponse);
    }

    public Page<AdResponse> getAllActiveAds(Integer type, Integer subType, List<String> level, Pageable pageable) {
        return adRepository.findAllActive(type, subType, level, pageable)
                .map(adMapper::toResponse);
    }

    // Новый метод для главной страницы – показывает ACTIVE и FILLED
    public Page<AdResponse> getMainPageAds(Integer type, Integer subType, List<String> level, Pageable pageable) {
        return adRepository.findMainPageAds(List.of(Ad.AdStatus.ACTIVE, Ad.AdStatus.FILLED), type, subType, level, pageable)
                .map(adMapper::toResponse);
    }

    public AdResponse getAdById(UUID id) {
        Ad ad = adRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Объявление не найдено"));
        return adMapper.toResponse(ad);
    }

    // ============================== СОЗДАНИЕ ОБЪЯВЛЕНИЯ ==============================

    @Transactional
    public AdResponse createAd(UUID authorId, AdRequest request) {
        User author = userService.getUserById(authorId);
        Profile profile = author.getProfile();

        // 1. Валидация
        validateAdRequest(request);

        // 2. Город
        City city = cityRepository.findById(request.getCityId())
                .orElseThrow(() -> new ResourceNotFoundException("Город не найден"));

        // 3. Создание сущности
        Ad ad = adMapper.toEntity(request, city);
        ad.setAuthor(author);
        ad.setCity(city);
        ad.setStatus(Ad.AdStatus.ACTIVE);

        // 4. Контактные данные из профиля
        String fullName = (profile.getFirstName() != null ? profile.getFirstName() : "") +
                (profile.getLastName() != null ? " " + profile.getLastName() : "");
        ad.setContactName(fullName.trim().isEmpty() ? author.getPhone() : fullName.trim());
        ad.setContactPhone(author.getPhone());

        // 5. Команда из профиля (если не указана в запросе)
        if (request.getTeam() == null && profile.getTeam() != null) {
            ad.setTeam(profile.getTeam());
        }

        // 6. Уровни
        if (request.getLevel() != null && !request.getLevel().isEmpty()) {
            List<String> validLevels = List.of("A", "B", "C", "D", "E", "F", "G", "H");
            for (String level : request.getLevel()) {
                if (!validLevels.contains(level)) {
                    throw new BadRequestException("Неверный уровень: " + level + ". Допустимые значения: A-H");
                }
            }
            ad.setLevels(request.getLevel());
        }

        // 7. Количество игроков для типа 1
        if (request.getType() == 1) {
            if (request.getSubType() == 1) { // вратарь
                ad.setGoaliesCount(request.getGoaliesCount());
            } else if (request.getSubType() == 2) { // полевые
                ad.setDefendersCount(request.getDefendersCount());
                ad.setForwardsCount(request.getForwardsCount());
            }
        }

        // 8. Инициализация счётчиков принятых откликов
        ad.setAcceptedGoaliesCount(0);
        ad.setAcceptedDefendersCount(0);
        ad.setAcceptedForwardsCount(0);
        ad.setAcceptedResponsesCount(0);

        // 9. Дублирование start_time в end_time, если end_time не передан и не требуется
        if (request.getEndTime() == null && !requiresEndTime(request.getType(), request.getSubType())) {
            ad.setEndTime(request.getStartTime());
        } else {
            ad.setEndTime(request.getEndTime());
        }

        ad = adRepository.save(ad);
        return adMapper.toResponse(ad);
    }

    // ============================== ОБНОВЛЕНИЕ ОБЪЯВЛЕНИЯ ==============================

    @Transactional
    public AdResponse updateAd(UUID adId, UUID userId, AdRequest request, boolean isAdmin) {
        Ad ad = adRepository.findById(adId)
                .orElseThrow(() -> new ResourceNotFoundException("Объявление не найдено"));

        // Проверка прав
        if (!isAdmin && !ad.getAuthor().getId().equals(userId)) {
            throw new UnauthorizedException("Нет прав на редактирование этого объявления");
        }

        // Валидация нового запроса
        validateAdRequest(request);

        // Город
        City city = cityRepository.findById(request.getCityId())
                .orElseThrow(() -> new ResourceNotFoundException("Город не найден"));

        // Обновляем основные поля через маппер
        adMapper.updateAd(ad, request, city);

        // Уровни
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

        // Количество игроков – обновляем только если это типы 1.1 или 1.2
        if (request.getType() == 1) {
            if (request.getSubType() == 1) {
                ad.setGoaliesCount(request.getGoaliesCount());
            } else if (request.getSubType() == 2) {
                ad.setDefendersCount(request.getDefendersCount());
                ad.setForwardsCount(request.getForwardsCount());
            }
        }

        // Если меняется автор (админ), сбрасываем статус на модерацию
        if (!ad.getAuthor().getId().equals(userId)) {
            ad.setStatus(Ad.AdStatus.MODERATION);
        }

        // Дублирование start_time в end_time, если end_time не передан и не требуется
        if (request.getEndTime() == null && !requiresEndTime(request.getType(), request.getSubType())) {
            ad.setEndTime(request.getStartTime());
        } else {
            ad.setEndTime(request.getEndTime());
        }

        ad = adRepository.save(ad);
        return adMapper.toResponse(ad);
    }

    // ============================== УДАЛЕНИЕ ==============================

    @Transactional
    public void deleteAd(UUID adId, UUID userId, boolean isAdmin) {
        Ad ad = adRepository.findById(adId)
                .orElseThrow(() -> new ResourceNotFoundException("Объявление не найдено"));

        if (!isAdmin && !ad.getAuthor().getId().equals(userId)) {
            throw new UnauthorizedException("Нет прав на удаление этого объявления");
        }

        adRepository.delete(ad);
    }

    // ============================== МОИ ОБЪЯВЛЕНИЯ ==============================

    public Page<AdResponse> getMyAds(UUID userId, Pageable pageable) {
        User user = userService.getUserById(userId);
        return adRepository.findByAuthor(user, pageable)
                .map(adMapper::toResponse);
    }

    // ============================== МОДЕРАЦИЯ ==============================

    public Page<AdResponse> getAdsForModeration(Pageable pageable) {
        return adRepository.findByStatus(Ad.AdStatus.MODERATION, pageable)
                .map(adMapper::toResponse);
    }

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

    // ============================== АВТОМАТИЧЕСКАЯ АРХИВАЦИЯ ==============================

    @Scheduled(cron = "0 */20 * * * *") // каждые 20 минут
    @Transactional
    public void archiveExpiredAds() {
        LocalDateTime now = LocalDateTime.now();
        List<Ad> expiredAds = adRepository.findByEndTimeBeforeAndStatusNot(now, Ad.AdStatus.ARCHIVED);
        for (Ad ad : expiredAds) {
            log.info("Архивация объявления id={}, endTime={}", ad.getId(), ad.getEndTime());
            ad.setStatus(Ad.AdStatus.ARCHIVED);
            adRepository.save(ad);
        }
    }

    // ============================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ВАЛИДАЦИИ ==============================

    private void validateAdRequest(AdRequest request) {
        // Проверка подтипа
        if (request.getSubType() == null) {
            throw new BadRequestException("Подтип объявления (subType) обязателен");
        }

        switch (request.getType()) {
            case 1: // Ищу игрока
                if (request.getSubType() != 1 && request.getSubType() != 2) {
                    throw new BadRequestException("Для типа 1 допустимы подтипы 1 (вратарь) или 2 (полевой)");
                }
                break;
            case 2: // Ищу лёд
                if (request.getSubType() != 1 && request.getSubType() != 2) {
                    throw new BadRequestException("Для типа 2 допустимы подтипы 1 (вратарь) или 2 (полевой)");
                }
                break;
            case 3: // Товарищеский матч
                if (request.getSubType() != 1 && request.getSubType() != 2) {
                    throw new BadRequestException("Для типа 3 допустимы подтипы 1 (ищу) или 2 (предлагаю)");
                }
                break;
            case 4: // Ищу специалиста
                if (request.getSubType() < 1 || request.getSubType() > 4) {
                    throw new BadRequestException("Для типа 4 допустимы подтипы 1-4 (судья, фотограф, медик, тренер)");
                }
                break;
            default:
                throw new BadRequestException("Неверный тип объявления");
        }

        // ЛДС – для некоторых типов можно выбрать только один
        if (isSingleRinkType(request.getType(), request.getSubType())) {
            if (request.getRinkIds() != null && request.getRinkIds().size() > 1) {
                throw new BadRequestException("Для этого типа объявления можно выбрать только один ЛДС");
            }
        }

        // Время окончания
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

        // Количество игроков (только для типа 1)
        if (request.getType() == 1) {
            if (request.getSubType() == 1) { // вратарь
                if (request.getGoaliesCount() == null) {
                    throw new BadRequestException("Не указано количество вратарей");
                }
                if (request.getGoaliesCount() != 1 && request.getGoaliesCount() != 2) {
                    throw new BadRequestException("Количество вратарей может быть 1 или 2");
                }
            } else if (request.getSubType() == 2) { // полевой
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

        // Уровни – разрешены для всех типов, кроме 4 (ищу специалиста)
        if (!requiresLevel(request.getType(), request.getSubType())) {
            if (request.getLevel() != null && !request.getLevel().isEmpty()) {
                throw new BadRequestException("Для этого типа объявления уровни не указываются");
            }
        }
    }

    private boolean isSingleRinkType(Integer type, Integer subType) {
        // Типы, где можно выбрать только один ЛДС: 1, 3.2, 4
        return type == 1 || (type == 3 && subType == 2) || type == 4;
    }

    private boolean requiresEndTime(Integer type, Integer subType) {
        // Типы, где нужен диапазон времени (endTime): 2.x, 3.1
        return type == 2 || (type == 3 && subType == 1);
    }

    private boolean requiresLevel(Integer type, Integer subType) {
        // Уровни нужны для всех типов, кроме 4 (ищу специалиста)
        return type != 4;
    }
}