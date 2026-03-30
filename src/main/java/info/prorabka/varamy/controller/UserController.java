package info.prorabka.varamy.controller;

import info.prorabka.varamy.dto.request.ChangePasswordRequest;
import info.prorabka.varamy.dto.response.ApiResponse;
import info.prorabka.varamy.dto.response.UserResponse;
import info.prorabka.varamy.entity.User;
import info.prorabka.varamy.security.SecurityUser;
import info.prorabka.varamy.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Tag(name = "Пользователи", description = "Управление пользователями")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "Получение данных текущего пользователя")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(
            @AuthenticationPrincipal SecurityUser currentUser) {
        UserResponse response = userService.getCurrentUserResponse(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/me/password")
    @Operation(summary = "Смена пароля")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal SecurityUser currentUser,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(currentUser.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Пароль успешно изменён", null));
    }

    @PutMapping("/me/phone")
    @Operation(summary = "Смена телефона")
    public ResponseEntity<ApiResponse<Void>> changePhone(
            @AuthenticationPrincipal SecurityUser currentUser,
            @RequestParam(name = "newPhone") String newPhone,
            @RequestParam(name = "password") String password) {
        userService.changePhone(currentUser.getId(), newPhone, password);
        return ResponseEntity.ok(ApiResponse.success("Телефон успешно изменён", null));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Получение списка пользователей (только для админа)")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> getUsers(
            @RequestParam(name = "phone", required = false) String phone,
            @RequestParam(name = "role", required = false) User.UserRole role,
            @RequestParam(name = "status", required = false) User.UserStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<UserResponse> users = userService.getUsers(phone, role, status, pageable);
        return ResponseEntity.ok(ApiResponse.success(users));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Получение пользователя по ID (только для админа)")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable UUID id) {
        UserResponse response = userService.getUserResponseById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Обновление пользователя (только для админа)")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable UUID id,
            @RequestParam(name = "phone", required = false) String phone,
            @RequestParam(name = "role", required = false) User.UserRole role,
            @RequestParam(name = "status", required = false) User.UserStatus status,
            @RequestParam(name = "password", required = false) String password) {
        UserResponse response = userService.updateUser(id, phone, role, status, password);
        return ResponseEntity.ok(ApiResponse.success("Пользователь обновлён", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Удаление пользователя (только для админа)")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @PathVariable UUID id,
            @RequestParam(name = "hardDelete", defaultValue = "false") boolean hardDelete) {
        userService.deleteUser(id, hardDelete);
        return ResponseEntity.ok(ApiResponse.success("Пользователь удалён", null));
    }
}
