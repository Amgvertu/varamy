package info.prorabka.varamy.controller;

import info.prorabka.varamy.dto.request.*;
import info.prorabka.varamy.dto.response.ApiResponse;
import info.prorabka.varamy.dto.response.AuthResponse;
import info.prorabka.varamy.entity.VerificationCode;
import info.prorabka.varamy.service.AuthService;
import info.prorabka.varamy.service.UserService;
import info.prorabka.varamy.service.VerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
    private final UserService userService;

    @PostMapping("/register")
    @Operation(summary = "Регистрация нового пользователя (без SMS подтверждения)")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Пользователь успешно зарегистрирован"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Неверные данные или телефон уже существует")
    })
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
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Успешный вход"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Неверный телефон или пароль")
    })
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

    // ===================== НАПОМИНАНИЕ ПАРОЛЯ (СБРОС ПАРОЛЯ) =====================

    @PostMapping("/send-password-reset-code")
    @Operation(
            summary = "Отправить код для сброса пароля",
            description = """
            Отправляет SMS с 6-значным кодом подтверждения на указанный номер телефона.
            
            **Процесс сброса пароля:**
            1. Пользователь вводит номер телефона на странице "Забыли пароль?"
            2. Система отправляет SMS с кодом подтверждения
            3. Пользователь вводит полученный код и новый пароль
            4. Вызов /api/auth/reset-password для завершения смены пароля
            
            **Примечание:** В режиме разработки код выводится в логи и push-уведомления.
            Код действителен в течение 5 минут.
            """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Код подтверждения отправлен"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Неверный формат телефона или пользователь не найден"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Пользователь с таким телефоном не найден")
    })
    public ResponseEntity<ApiResponse<Void>> sendPasswordResetCode(
            @Valid @RequestBody SendVerificationCodeRequest request) {

        if (!userService.isPhoneExists(request.getPhone())) {
            throw new info.prorabka.varamy.exception.BadRequestException("Пользователь с таким телефоном не найден");
        }

        verificationService.sendVerificationCode(
                request.getPhone(),
                VerificationCode.VerificationPurpose.PASSWORD_RESET);
        return ResponseEntity.ok(ApiResponse.success("Код подтверждения отправлен", null));
    }

    @PostMapping("/reset-password")
    @Operation(
            summary = "Сброс пароля с подтверждением кода",
            description = """
            Завершает процесс сброса пароля.
            
            **Шаги:**
            1. Получить код через /api/auth/send-password-reset-code
            2. Вызвать этот метод с кодом и новым паролем
            
            После успешного сброса пароль пользователя изменяется.
            Рекомендуется после сброса пароля выполнить повторный вход.
            """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Пароль успешно изменён"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Неверный код подтверждения или истёк срок действия"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Пользователь не найден")
    })
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {

        verificationService.verifyCode(
                request.getPhone(),
                request.getCode(),
                VerificationCode.VerificationPurpose.PASSWORD_RESET);

        authService.resetPassword(request.getPhone(), request.getNewPassword());

        return ResponseEntity.ok(ApiResponse.success("Пароль успешно изменён", null));
    }

    // ===================== РЕГИСТРАЦИЯ С SMS ПОДТВЕРЖДЕНИЕМ =====================

    @PostMapping("/send-registration-code")
    @Operation(
            summary = "Отправить код подтверждения для регистрации",
            description = """
            Отправляет SMS с 6-значным кодом подтверждения на указанный номер телефона.
            
            **Процесс регистрации:**
            1. Пользователь вводит номер телефона на странице регистрации
            2. Система отправляет SMS с кодом подтверждения
            3. Пользователь вводит полученный код, пароль и данные профиля
            4. Вызов /api/auth/register-with-verification для завершения регистрации
            
            **Примечание:** Телефон должен быть свободен (не зарегистрирован ранее).
            Код действителен в течение 5 минут.
            """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Код подтверждения отправлен"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Неверный формат телефона или телефон уже зарегистрирован")
    })
    public ResponseEntity<ApiResponse<Void>> sendRegistrationCode(
            @Valid @RequestBody SendVerificationCodeRequest request) {
        verificationService.sendVerificationCode(
                request.getPhone(),
                VerificationCode.VerificationPurpose.REGISTRATION);
        return ResponseEntity.ok(ApiResponse.success("Код подтверждения отправлен", null));
    }

    @PostMapping("/register-with-verification")
    @Operation(
            summary = "Регистрация с подтверждением кода",
            description = """
            Завершает регистрацию пользователя после подтверждения телефона.
            
            **Шаги:**
            1. Получить код через /api/auth/send-registration-code
            2. Вызвать этот метод с кодом, паролем и данными профиля
            
            После успешной регистрации возвращаются accessToken и refreshToken.
            Пользователь получает роль USER и подроль PLAYER.
            """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Пользователь успешно зарегистрирован"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Неверный код подтверждения или данные")
    })
    public ResponseEntity<ApiResponse<AuthResponse>> registerWithVerification(
            @Valid @RequestBody RegisterWithVerificationRequest request,
            HttpServletRequest httpRequest) {

        verificationService.verifyCode(
                request.getPhone(),
                request.getCode(),
                VerificationCode.VerificationPurpose.REGISTRATION);

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
}