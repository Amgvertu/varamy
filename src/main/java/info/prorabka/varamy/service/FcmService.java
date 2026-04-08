package info.prorabka.varamy.service;

import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.UUID;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FcmService {
    private final FcmTokenService fcmTokenService;

    /**
     * Отправляет "тихое" data-уведомление, чтобы разбудить приложение.
     * @param userId ID пользователя
     */
    public void sendWakeUpNotification(UUID userId) {
        List<String> tokens = fcmTokenService.getActiveTokensForUser(userId);
        if (tokens.isEmpty()) {
            log.warn("No active FCM tokens for user {}", userId);
            return;
        }
        for (String token : tokens) {
            Message message = Message.builder()
                    .setToken(token)
                    .putData("type", "WAKE_UP")
                    .build();
            try {
                String response = FirebaseMessaging.getInstance().send(message);
                log.info("Wake-up notification sent to user {} (token: {}), response: {}", userId, token, response);
            } catch (FirebaseMessagingException e) {
                if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                    log.warn("FCM token unregistered: {}", token);
                    fcmTokenService.unregisterToken(userId, token);
                } else {
                    log.error("Failed to send FCM: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Пример отправки реального уведомления (опционально, как fallback).
     */
    public void sendRealNotification(UUID userId, String title, String body) {
        List<String> tokens = fcmTokenService.getActiveTokensForUser(userId);
        if (tokens.isEmpty()) return;
        for (String token : tokens) {
            Message message = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .putData("type", "REAL")
                    .build();
            try {
                FirebaseMessaging.getInstance().send(message);
            } catch (FirebaseMessagingException e) {
                log.error("Failed to send real notification: {}", e.getMessage());
            }
        }
    }
}