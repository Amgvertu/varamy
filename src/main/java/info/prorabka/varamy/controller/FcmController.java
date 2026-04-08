package info.prorabka.varamy.controller;

import info.prorabka.varamy.dto.request.FcmTokenRequest;
import info.prorabka.varamy.dto.response.ApiResponse;
import info.prorabka.varamy.security.SecurityUser;
import info.prorabka.varamy.service.FcmTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/fcm")
@RequiredArgsConstructor
public class FcmController {
    private final FcmTokenService fcmTokenService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> registerToken(
            @AuthenticationPrincipal SecurityUser currentUser,
            @Valid @RequestBody FcmTokenRequest request) {
        fcmTokenService.registerToken(currentUser.getId(), request.getToken());
        return ResponseEntity.ok(ApiResponse.success("FCM token registered", null));
    }

    @DeleteMapping("/unregister")
    public ResponseEntity<ApiResponse<Void>> unregisterToken(
            @AuthenticationPrincipal SecurityUser currentUser,
            @RequestParam String token) {
        fcmTokenService.unregisterToken(currentUser.getId(), token);
        return ResponseEntity.ok(ApiResponse.success("FCM token unregistered", null));
    }
}