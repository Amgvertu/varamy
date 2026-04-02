package info.prorabka.varamy.controller;

import info.prorabka.varamy.dto.request.ChangePasswordRequest;
import info.prorabka.varamy.dto.request.ChangePhoneRequest;
import info.prorabka.varamy.dto.request.SendVerificationCodeRequest;
import info.prorabka.varamy.dto.response.ApiResponse;
import info.prorabka.varamy.dto.response.UserResponse;
import info.prorabka.varamy.entity.VerificationCode;
import info.prorabka.varamy.entity.User;
import info.prorabka.varamy.security.SecurityUser;
import info.prorabka.varamy.service.UserService;
import info.prorabka.varamy.service.VerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Tag(name = "Пользователи", description = "Управление пользователями")
public class UserController {

    private final UserService userService;
    private final VerificationService verificationService;

    @Value("${sms.mock-mode:true}")
    private boolean mockMode;

    // ===================== ЛИЧНЫЙ КАБИНЕТ =====================

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

    // ===================== СМЕНА НОМЕРА ТЕЛЕФОНА =====================

    @PostMapping("/me/send-phone-change-code")
    @Operation(
            summary = "Отправить код для смены номера телефона",
            description = """
            Отправляет SMS с 6-значным кодом подтверждения на НОВЫЙ номер телефона.
            В режиме разработки (sms.mock-mode=true) код также возвращается в поле data.code.
            """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Код подтверждения отправлен"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Неверный формат телефона или номер уже занят")
    })
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, String>>> sendPhoneChangeCode(
            @AuthenticationPrincipal SecurityUser currentUser,
            @Valid @RequestBody SendVerificationCodeRequest request) {

        String code = verificationService.sendVerificationCode(
                request.getPhone(),
                VerificationCode.VerificationPurpose.PHONE_CHANGE);

        Map<String, String> data = null;
        if (mockMode) {
            data = new HashMap<>();
            data.put("code", code);
        }

        return ResponseEntity.ok(ApiResponse.success("Код подтверждения отправлен на новый номер", data));
    }

    @PutMapping("/me/phone-with-verification")
    @Operation(
            summary = "Смена номера телефона с подтверждением кода",
            description = """
            Завершает смену номера телефона после подтверждения.
            """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Телефон успешно изменён"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Неверный код подтверждения или номер уже занят"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Пользователь не авторизован")
    })
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> changePhoneWithVerification(
            @AuthenticationPrincipal SecurityUser currentUser,
            @Valid @RequestBody ChangePhoneRequest request) {

        verificationService.verifyCode(
                request.getNewPhone(),
                request.getCode(),
                VerificationCode.VerificationPurpose.PHONE_CHANGE);

        userService.changePhone(currentUser.getId(), request.getNewPhone(), null);

        return ResponseEntity.ok(ApiResponse.success("Телефон успешно изменён", null));
    }

    // ===================== АДМИНИСТРАТИВНЫЕ МЕТОДЫ =====================

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Получение списка пользователей (только для админа)")
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

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Получение пользователя по ID (только для админа)")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(
            @Parameter(description = "UUID пользователя", example = "7e8a0d80-4778-4e88-a5e2-596c81b0f932")
            @PathVariable UUID id) {
        UserResponse response = userService.getUserResponseById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Обновление пользователя (только для админа)")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @Parameter(description = "UUID пользователя", example = "7e8a0d80-4778-4e88-a5e2-596c81b0f932")
            @PathVariable UUID id,
            @Parameter(description = "Новый номер телефона", example = "+79001234567")
            @RequestParam(name = "phone", required = false) String phone,
            @Parameter(description = "Новая роль", example = "MODERATOR")
            @RequestParam(name = "role", required = false) User.UserRole role,
            @Parameter(description = "Новый статус", example = "BLOCKED")
            @RequestParam(name = "status", required = false) User.UserStatus status,
            @Parameter(description = "Новый пароль (будет захеширован)")
            @RequestParam(name = "password", required = false) String password) {
        UserResponse response = userService.updateUser(id, phone, role, status, password);
        return ResponseEntity.ok(ApiResponse.success("Пользователь обновлён", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Удаление пользователя (только для админа)",
            description = "Если hardDelete=true — полное удаление из БД, иначе блокировка.")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @Parameter(description = "UUID пользователя", example = "7e8a0d80-4778-4e88-a5e2-596c81b0f932")
            @PathVariable UUID id,
            @Parameter(description = "Полное удаление (true) или блокировка (false)", example = "false")
            @RequestParam(name = "hardDelete", defaultValue = "false") boolean hardDelete) {
        userService.deleteUser(id, hardDelete);
        return ResponseEntity.ok(ApiResponse.success("Пользователь удалён", null));
    }
}