package info.prorabka.varamy.controller;

import info.prorabka.varamy.dto.request.AddSubscriptionRequest;
import info.prorabka.varamy.dto.request.UpdateNotificationSettingsRequest;
import info.prorabka.varamy.dto.response.ApiResponse;
import info.prorabka.varamy.dto.response.NotificationResponse;
import info.prorabka.varamy.dto.response.NotificationSettingsResponse;
import info.prorabka.varamy.security.SecurityUser;
import info.prorabka.varamy.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Уведомления", description = "Управление настройками и получение уведомлений")
public class NotificationController {

    private final NotificationService notificationService;

    // ========== НАСТРОЙКИ ==========
    @GetMapping("/settings")
    @Operation(summary = "Получить настройки уведомлений текущего пользователя")
    public ResponseEntity<ApiResponse<NotificationSettingsResponse>> getSettings(
            @AuthenticationPrincipal SecurityUser currentUser) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.getSettings(currentUser.getId())));
    }

    @PatchMapping("/settings")
    @Operation(summary = "Обновить настройки уведомлений (частично)")
    public ResponseEntity<ApiResponse<NotificationSettingsResponse>> updateSettings(
            @AuthenticationPrincipal SecurityUser currentUser,
            @Valid @RequestBody UpdateNotificationSettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.updateSettings(currentUser.getId(), request)));
    }

    // ========== ПОДПИСКИ ==========
    @PostMapping("/subscriptions")
    @Operation(summary = "Добавить подписку на тип/подтип объявления")
    public ResponseEntity<ApiResponse<Void>> addSubscription(
            @AuthenticationPrincipal SecurityUser currentUser,
            @Valid @RequestBody AddSubscriptionRequest request) {
        notificationService.addSubscription(currentUser.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Подписка добавлена", null));
    }

    @DeleteMapping("/subscriptions")
    @Operation(summary = "Удалить подписку на тип/подтип объявления")
    public ResponseEntity<ApiResponse<Void>> removeSubscription(
            @AuthenticationPrincipal SecurityUser currentUser,
            @RequestParam Integer type,
            @RequestParam Integer subType) {
        notificationService.removeSubscription(currentUser.getId(), type, subType);
        return ResponseEntity.ok(ApiResponse.success("Подписка удалена", null));
    }

    // ========== ПОЛУЧЕНИЕ УВЕДОМЛЕНИЙ ==========
    @GetMapping
    @Operation(summary = "Получить список уведомлений (с пагинацией)")
    public ResponseEntity<ApiResponse<Page<NotificationResponse>>> getNotifications(
            @AuthenticationPrincipal SecurityUser currentUser,
            @RequestParam(required = false) Boolean onlyUnread,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<NotificationResponse> page = notificationService.getNotifications(currentUser.getId(), pageable, onlyUnread);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Получить количество непрочитанных уведомлений")
    public ResponseEntity<ApiResponse<Long>> getUnreadCount(
            @AuthenticationPrincipal SecurityUser currentUser) {
        long count = notificationService.getUnreadCount(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(count));
    }

    @PatchMapping("/mark-read")
    @Operation(summary = "Отметить уведомления как прочитанные")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @AuthenticationPrincipal SecurityUser currentUser,
            @RequestBody List<Long> notificationIds) {
        notificationService.markAsRead(currentUser.getId(), notificationIds);
        return ResponseEntity.ok(ApiResponse.success("Уведомления отмечены прочитанными", null));
    }
}