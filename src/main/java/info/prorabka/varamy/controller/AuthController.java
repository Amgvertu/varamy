package info.prorabka.varamy.controller;

import info.prorabka.varamy.dto.request.LoginRequest;
import info.prorabka.varamy.dto.request.RefreshTokenRequest;
import info.prorabka.varamy.dto.request.RegisterRequest;
import info.prorabka.varamy.dto.response.ApiResponse;
import info.prorabka.varamy.dto.response.AuthResponse;
import info.prorabka.varamy.service.AuthService;
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
@Tag(name = "Аутентификация", description = "Регистрация, вход, обновление токенов")
public class AuthController {

    private final AuthService authService;

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
}