// AuthController.java
package info.prorabka.varamy.controller;

import info.prorabka.varamy.dto.request.*;
import info.prorabka.varamy.dto.response.ApiResponse;
import info.prorabka.varamy.dto.response.AuthResponse;
import info.prorabka.varamy.entity.VerificationCode;
import info.prorabka.varamy.service.AuthService;
import info.prorabka.varamy.service.UserService;
import info.prorabka.varamy.service.VerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Аутентификация", description = "Регистрация, вход, обновление токенов, сброс пароля")
public class AuthController {

    private final AuthService authService;
    private final VerificationService verificationService;
    private final UserService userService;  // ← ДОБАВИТЬ ЭТУ СТРОКУ

    @PostMapping("/register")
    @Operation(summary = "Регистрация нового пользователя")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {
        AuthResponse response = authService.register(request, httpRequest);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Пользователь успешно зарегистрирован", response));
    }

    @PostMapping("/login")
    @Operation(summary = "Вход в систему")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        AuthResponse response = authService.login(request, httpRequest);
        return ResponseEntity.ok(ApiResponse.success("Вход выполнен успешно", response));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Обновление access токена")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest) {
        AuthResponse response = authService.refresh(request.getRefreshToken(), httpRequest);
        return ResponseEntity.ok(ApiResponse.success("Токен обновлён", response));
    }

    @PostMapping("/logout")
    @Operation(summary = "Выход из системы")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success("Выход выполнен успешно", null));
    }

    @PostMapping("/send-registration-code")
    @Operation(summary = "Отправить код подтверждения для регистрации")
    public ResponseEntity<ApiResponse<Void>> sendRegistrationCode(
            @Valid @RequestBody SendVerificationCodeRequest request) {
        verificationService.sendVerificationCode(
                request.getPhone(),
                VerificationCode.VerificationPurpose.REGISTRATION);
        return ResponseEntity.ok(ApiResponse.success("Код подтверждения отправлен", null));
    }

    @PostMapping("/register-with-verification")
    @Operation(summary = "Регистрация с подтверждением кода")
    public ResponseEntity<ApiResponse<AuthResponse>> registerWithVerification(
            @Valid @RequestBody RegisterWithVerificationRequest request,
            HttpServletRequest httpRequest) {

        // Проверяем код
        verificationService.verifyCode(
                request.getPhone(),
                request.getCode(),
                VerificationCode.VerificationPurpose.REGISTRATION);

        // Регистрируем пользователя
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setPhone(request.getPhone());
        registerRequest.setPassword(request.getPassword());
        registerRequest.setCountryId(request.getCountryId());
        registerRequest.setRegionId(request.getRegionId());
        registerRequest.setCityId(request.getCityId());

        AuthResponse response = authService.register(registerRequest, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Пользователь успешно зарегистрирован", response));
    }

    @PostMapping("/send-password-reset-code")
    @Operation(summary = "Отправить код для сброса пароля")
    public ResponseEntity<ApiResponse<Void>> sendPasswordResetCode(
            @Valid @RequestBody SendVerificationCodeRequest request) {

        // Проверяем, существует ли пользователь с таким телефоном
        if (!userService.isPhoneExists(request.getPhone())) {
            throw new info.prorabka.varamy.exception.BadRequestException("Пользователь с таким телефоном не найден");
        }

        verificationService.sendVerificationCode(
                request.getPhone(),
                VerificationCode.VerificationPurpose.PASSWORD_RESET);
        return ResponseEntity.ok(ApiResponse.success("Код подтверждения отправлен", null));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Сброс пароля с подтверждением кода")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {

        // Проверяем код
        verificationService.verifyCode(
                request.getPhone(),
                request.getCode(),
                VerificationCode.VerificationPurpose.PASSWORD_RESET);

        // Сбрасываем пароль
        authService.resetPassword(request.getPhone(), request.getNewPassword());

        return ResponseEntity.ok(ApiResponse.success("Пароль успешно изменён", null));
    }
}