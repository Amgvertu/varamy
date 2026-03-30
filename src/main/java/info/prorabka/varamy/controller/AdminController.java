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
    @Operation(summary = "Получение списка пользователей")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> getUsers(
            @RequestParam(name = "phone", required = false) String phone,
            @RequestParam(name = "role", required = false) User.UserRole role,
            @RequestParam(name = "status", required = false) User.UserStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<UserResponse> users = userService.getUsers(phone, role, status, pageable);
        return ResponseEntity.ok(ApiResponse.success(users));
    }

    @PutMapping("/users/{id}/role")
    @Operation(summary = "Смена роли пользователя")
    public ResponseEntity<ApiResponse<UserResponse>> changeUserRole(
            @PathVariable UUID id,
            @RequestParam(name = "role") User.UserRole role) {
        UserResponse response = userService.updateUser(id, null, role, null, null);
        return ResponseEntity.ok(ApiResponse.success("Роль пользователя изменена", response));
    }

    @PutMapping("/users/{id}/status")
    @Operation(summary = "Изменение статуса пользователя")
    public ResponseEntity<ApiResponse<UserResponse>> changeUserStatus(
            @PathVariable UUID id,
            @RequestParam(name = "status") User.UserStatus status) {
        UserResponse response = userService.updateUser(id, null, null, status, null);
        return ResponseEntity.ok(ApiResponse.success("Статус пользователя изменён", response));
    }

    @GetMapping("/ads")
    @Operation(summary = "Получение всех объявлений с фильтрацией (только для админа)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<AdResponse>>> getAllAds(
            @Parameter(description = "ID города", example = "57")
            @RequestParam(required = false) Long cityId,
            @Parameter(description = "Тип объявления (1-5)", example = "1")
            @RequestParam(required = false) Integer type,
            @Parameter(description = "Подтип объявления (1-2 для type=1,2,3; 1-4 для type=4)", example = "1")
            @RequestParam(required = false) Integer subType,
            @Parameter(description = "Статус объявления", example = "ACTIVE")
            @RequestParam(required = false) Ad.AdStatus status,
            @Parameter(description = "Уровни игроков", example = "[\"A\",\"B\"]")
            @RequestParam(required = false) List<String> level,
            @Parameter(description = "ID автора", example = "7e8a0d80-4778-4e88-a5e2-596c81b0f932")
            @RequestParam(required = false) UUID authorId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<AdResponse> ads = adService.getAds(cityId, type, subType, status, level, authorId, pageable);
        return ResponseEntity.ok(ApiResponse.success(ads));
    }
}