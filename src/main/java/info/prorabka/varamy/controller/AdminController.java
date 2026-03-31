package info.prorabka.varamy.controller;

import info.prorabka.varamy.dto.response.AdResponse;
import info.prorabka.varamy.dto.response.ApiResponse;
import info.prorabka.varamy.dto.response.UserResponse;
import info.prorabka.varamy.entity.Ad;
import info.prorabka.varamy.entity.User;
import info.prorabka.varamy.service.AdService;
import info.prorabka.varamy.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Администрирование", description = "Административные функции")
public class AdminController {

    private final UserService userService;
    private final AdService adService;

    @GetMapping("/users")
    @Operation(summary = "Получение списка пользователей с фильтрацией")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> getUsers(
            @Parameter(description = "Часть номера телефона для поиска", example = "7900")
            @RequestParam(name = "phone", required = false) String phone,
            @Parameter(description = "Роль пользователя", example = "USER")
            @RequestParam(name = "role", required = false) User.UserRole role,
            @Parameter(description = "Статус пользователя", example = "ACTIVE")
            @RequestParam(name = "status", required = false) User.UserStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<UserResponse> users = userService.getUsers(phone, role, status, pageable);
        return ResponseEntity.ok(ApiResponse.success(users));
    }

    @PutMapping("/users/{id}/role")
    @Operation(summary = "Смена роли пользователя")
    public ResponseEntity<ApiResponse<UserResponse>> changeUserRole(
            @Parameter(description = "UUID пользователя", example = "7e8a0d80-4778-4e88-a5e2-596c81b0f932")
            @PathVariable UUID id,
            @Parameter(description = "Новая роль", example = "MODERATOR")
            @RequestParam(name = "role") User.UserRole role) {
        UserResponse response = userService.updateUser(id, null, role, null, null);
        return ResponseEntity.ok(ApiResponse.success("Роль пользователя изменена", response));
    }

    @PutMapping("/users/{id}/status")
    @Operation(summary = "Изменение статуса пользователя (блокировка/разблокировка)")
    public ResponseEntity<ApiResponse<UserResponse>> changeUserStatus(
            @Parameter(description = "UUID пользователя", example = "7e8a0d80-4778-4e88-a5e2-596c81b0f932")
            @PathVariable UUID id,
            @Parameter(description = "Новый статус", example = "BLOCKED")
            @RequestParam(name = "status") User.UserStatus status) {
        UserResponse response = userService.updateUser(id, null, null, status, null);
        return ResponseEntity.ok(ApiResponse.success("Статус пользователя изменён", response));
    }

    @GetMapping("/ads")
    @Operation(summary = "Получение всех объявлений с фильтрацией (включая архивные)",
            description = "Административный метод, возвращает объявления в любом статусе.")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<AdResponse>>> getAllAdsAdmin(
            @Parameter(description = "ID города", example = "57")
            @RequestParam(required = false) Long cityId,
            @Parameter(description = "Тип объявления (1-5)", example = "1")
            @RequestParam(required = false) Integer type,
            @Parameter(description = "Подтип объявления", example = "1")
            @RequestParam(required = false) Integer subType,
            @Parameter(description = "Статус объявления", example = "ACTIVE")
            @RequestParam(required = false) Ad.AdStatus status,
            @Parameter(description = "Уровни игроков (список строк A-H)", example = "[\"A\",\"B\"]")
            @RequestParam(required = false) List<String> level,
            @Parameter(description = "ID автора объявления", example = "7e8a0d80-4778-4e88-a5e2-596c81b0f932")
            @RequestParam(required = false) UUID authorId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<AdResponse> ads = adService.getAdsAdmin(cityId, type, subType, status, level, authorId, pageable);
        return ResponseEntity.ok(ApiResponse.success(ads));
    }

    @GetMapping("/ads/statistics")
    @Operation(summary = "Получение статистики по объявлениям",
            description = "Возвращает количество объявлений в каждом статусе.")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdService.AdStatistics>> getAdStatistics() {
        AdService.AdStatistics stats = adService.getAdStatistics();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @PostMapping("/ads/cleanup")
    @Operation(summary = "Принудительный запуск очистки старых архивных объявлений",
            description = "Удаляет объявления, находившиеся в статусе ARCHIVED более 5 месяцев.")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> runCleanup() {
        adService.cleanupOldArchivedAds();
        return ResponseEntity.ok(ApiResponse.success("Очистка старых архивных объявлений запущена"));
    }

    @PostMapping("/ads/archive")
    @Operation(summary = "Принудительный запуск архивации просроченных объявлений",
            description = "Переводит объявления с истекшим endTime в статус ARCHIVED.")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> runArchive() {
        adService.archiveExpiredAds();
        return ResponseEntity.ok(ApiResponse.success("Архивация просроченных объявлений запущена"));
    }
}