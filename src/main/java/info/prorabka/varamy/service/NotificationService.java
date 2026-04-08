package info.prorabka.varamy.service;

import info.prorabka.varamy.dto.request.AddSubscriptionRequest;
import info.prorabka.varamy.dto.request.UpdateNotificationSettingsRequest;
import info.prorabka.varamy.dto.response.*;
import info.prorabka.varamy.entity.*;
import info.prorabka.varamy.exception.BadRequestException;
import info.prorabka.varamy.exception.ResourceNotFoundException;
import info.prorabka.varamy.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationSettingsRepository settingsRepository;
    private final UserNotificationSubscriptionRepository subscriptionRepository;
    private final NotificationRepository notificationRepository;
    private final UserService userService;
    private final CityRepository cityRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final SimpUserRegistry userRegistry;   // для проверки активных сессий
    private final FcmService fcmService;
    private final Map<UUID, Long> lastFcmSentTime = new ConcurrentHashMap<>();
    private static final long FCM_COOLDOWN_MS = 5 * 60 * 1000; // 5 минут (настройте под свои задачи)

    // ========== НАСТРОЙКИ ==========

    @Transactional
    public NotificationSettingsResponse getSettings(UUID userId) {
        NotificationSettings settings = settingsRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultSettings(userId));
        List<SubscriptionResponse> subscriptions = subscriptionRepository.findByIdUserId(userId).stream()
                .map(sub -> new SubscriptionResponse(sub.getId().getType(), sub.getId().getSubType()))
                .collect(Collectors.toList());

        CitySimpleResponse cityResp = null;
        if (settings.getNotificationCity() != null) {
            cityResp = new CitySimpleResponse(settings.getNotificationCity().getId(),
                    settings.getNotificationCity().getName());
        }

        return NotificationSettingsResponse.builder()
                .notifyOnResponseToMyAd(settings.isNotifyOnResponseToMyAd())
                .notifyOnMyResponseAccepted(settings.isNotifyOnMyResponseAccepted())
                .notifyNewAdsInCity(settings.isNotifyNewAdsInCity())
                .notificationCity(cityResp)
                .subscriptions(subscriptions)
                .build();
    }

    @Transactional
    public NotificationSettingsResponse updateSettings(UUID userId, UpdateNotificationSettingsRequest request) {
        log.debug("Updating settings for user: {}", userId);
        NotificationSettings settings = settingsRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultSettings(userId));

        if (request.getNotifyOnResponseToMyAd() != null) {
            settings.setNotifyOnResponseToMyAd(request.getNotifyOnResponseToMyAd());
        }
        if (request.getNotifyOnMyResponseAccepted() != null) {
            settings.setNotifyOnMyResponseAccepted(request.getNotifyOnMyResponseAccepted());
        }
        if (request.getNotifyNewAdsInCity() != null) {
            settings.setNotifyNewAdsInCity(request.getNotifyNewAdsInCity());
        }
        if (request.getNotificationCityId() != null) {
            City city = cityRepository.findById(request.getNotificationCityId())
                    .orElseThrow(() -> new ResourceNotFoundException("Город не найден"));
            settings.setNotificationCity(city);
        } else {
            settings.setNotificationCity(null);
        }
        settings.setUpdatedAt(LocalDateTime.now());
        settingsRepository.save(settings);
        return getSettings(userId);
    }

    private NotificationSettings createDefaultSettings(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        NotificationSettings settings = new NotificationSettings();
        settings.setUserId(userId);
        settings.setNotifyOnResponseToMyAd(true);
        settings.setNotifyOnMyResponseAccepted(true);
        settings.setNotifyNewAdsInCity(false);
        settings.setNotificationCity(null);
        settings.setUpdatedAt(LocalDateTime.now());
        log.debug("Creating new settings for user: {}", userId);
        return settingsRepository.save(settings);
    }

    // ========== ПОДПИСКИ НА ТИПЫ ==========

    @Transactional
    public void addSubscription(UUID userId, AddSubscriptionRequest request) {
        User user = userService.getUserById(userId);
        UserNotificationSubscription.SubscriptionId id =
                new UserNotificationSubscription.SubscriptionId(userId, request.getType(), request.getSubType());
        if (subscriptionRepository.existsById(id)) {
            throw new BadRequestException("Такая подписка уже существует");
        }
        UserNotificationSubscription subscription = new UserNotificationSubscription();
        subscription.setId(id);
        subscription.setUser(user);
        subscriptionRepository.save(subscription);
    }

    @Transactional
    public void removeSubscription(UUID userId, Integer type, Integer subType) {
        subscriptionRepository.deleteByUserIdAndTypeAndSubType(userId, type, subType);
    }

    // ========== УВЕДОМЛЕНИЯ (СОЗДАНИЕ И ОТПРАВКА) ==========

    @Transactional
    public void createAndSendNotification(UUID userId, String type, String content, UUID relatedEntityId) {
        log.info(">>> Creating notification for user {}: type={}, content={}", userId, type, content);

        // 1. Сохраняем уведомление в БД (ВСЕГДА, независимо от наличия сессии)
        User user = userService.getUserById(userId);
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setType(type);
        notification.setContent(content);
        notification.setRelatedEntityId(relatedEntityId);
        notification.setRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        notification = notificationRepository.save(notification);
        log.info("Notification saved to DB with id={}", notification.getId());

        // 2. Проверяем наличие активной WebSocket-сессии
        SimpUser simpUser = userRegistry.getUser(userId.toString());
        boolean hasSession = (simpUser != null && simpUser.hasSessions());

        if (hasSession) {
            // Сессия есть – отправляем уведомление через WebSocket немедленно
            NotificationResponse response = toResponse(notification);
            try {
                messagingTemplate.convertAndSendToUser(
                        userId.toString(),
                        "/queue/notifications",
                        response
                );
                log.info("WebSocket notification successfully sent to user {}", userId);
            } catch (Exception e) {
                log.error("Failed to send WebSocket notification to user {}: {}", userId, e.getMessage(), e);
            }
        } else {
            // Сессии нет – отправляем FCM "пробуждение" (с защитой от частых отправок)
            log.info("No active WebSocket session for user {}, sending FCM wake-up", userId);

            if (shouldSendFcm(userId)) {
                fcmService.sendWakeUpNotification(userId);
                lastFcmSentTime.put(userId, System.currentTimeMillis());
                log.info("FCM wake-up sent to user {}", userId);
            } else {
                log.debug("FCM wake-up already sent recently for user {}, skipping", userId);
            }
            // Уведомление сохранено в БД, клиент заберёт его через REST после пробуждения
        }
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .content(n.getContent())
                .relatedEntityId(n.getRelatedEntityId())
                .isRead(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }

    // ========== ПОЛУЧЕНИЕ УВЕДОМЛЕНИЙ (REST) ==========

    public Page<NotificationResponse> getNotifications(UUID userId, Pageable pageable, Boolean onlyUnread) {
        Page<Notification> page;
        if (Boolean.TRUE.equals(onlyUnread)) {
            page = notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId, pageable);
        } else {
            page = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        }
        return page.map(this::toResponse);
    }

    public long getUnreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public void markAsRead(UUID userId, List<Long> notificationIds) {
        if (notificationIds == null || notificationIds.isEmpty()) return;
        int updated = notificationRepository.markAsRead(userId, notificationIds);
        log.info("Marked {} notifications as read for user {}", updated, userId);
    }

    // ========== ЛОГИКА ГЕНЕРАЦИИ ПО СОБЫТИЯМ ==========

    public void onResponseCreated(UUID adAuthorId, UUID adId, UUID responseId, String responderName) {
        log.info("onResponseCreated: adAuthorId={}, responderName={}", adAuthorId, responderName);
        NotificationSettings settings = settingsRepository.findByUserId(adAuthorId)
                .orElseGet(() -> createDefaultSettings(adAuthorId));
        if (settings.isNotifyOnResponseToMyAd()) {
            String content = String.format("Пользователь %s откликнулся на ваше объявление", responderName);
            createAndSendNotification(adAuthorId, "RESPONSE", content, responseId);
        } else {
            log.debug("User {} has notifications for RESPONSE disabled", adAuthorId);
        }
    }

    public void onResponseAccepted(UUID responderId, UUID adId, UUID responseId, String adTitle) {
        log.info("onResponseAccepted: responderId={}, adTitle={}", responderId, adTitle);
        NotificationSettings settings = settingsRepository.findByUserId(responderId)
                .orElseGet(() -> createDefaultSettings(responderId));
        if (settings.isNotifyOnMyResponseAccepted()) {
            String content = String.format("Ваш отклик на объявление \"%s\" принят", adTitle);
            createAndSendNotification(responderId, "RESPONSE_ACCEPTED", content, responseId);
        } else {
            log.debug("User {} has notifications for RESPONSE_ACCEPTED disabled", responderId);
        }
    }

    public void onNewAdCreated(Ad newAd) {
        Long cityId = newAd.getCity().getId();
        Integer type = newAd.getType();
        Integer subType = newAd.getSubType();
        log.info("onNewAdCreated: cityId={}, type={}, subType={}", cityId, type, subType);

        List<User> interestedUsers = userService.findUsersForNewAdNotification(cityId, type, subType);
        log.info("Found {} interested users for new ad notification", interestedUsers.size());

        for (User user : interestedUsers) {
            String content = String.format("Новое объявление типа %d.%d в вашем городе: %s",
                    type, subType, newAd.getTeam() != null ? newAd.getTeam() : "без команды");
            createAndSendNotification(user.getId(), "NEW_AD", content, newAd.getId());
        }
    }

    public void sendTestNotification(UUID userId) {
        NotificationResponse test = NotificationResponse.builder()
                .id(0L)
                .type("TEST")
                .content("Тестовое уведомление от " + LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .isRead(false)
                .build();

        try {
            messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/notifications", test);
            log.info("Test notification sent to user {}", userId);
        } catch (Exception e) {
            log.error("Failed to send test notification to user {}: {}", userId, e.getMessage(), e);
        }

    }

    private boolean hasActiveWebSocketSession(UUID userId) {
        SimpUser simpUser = userRegistry.getUser(userId.toString());
        return simpUser != null && simpUser.hasSessions();
    }

    private boolean shouldSendFcm(UUID userId) {
        Long last = lastFcmSentTime.get(userId);
        if (last == null) {
            return true;
        }
        return System.currentTimeMillis() - last > FCM_COOLDOWN_MS;
    }
}