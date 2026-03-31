package info.prorabka.varamy.controller;

import info.prorabka.varamy.dto.request.ResponseRequest;
import info.prorabka.varamy.dto.response.ApiResponse;
import info.prorabka.varamy.dto.response.MyResponseAdResponse;
import info.prorabka.varamy.dto.response.ResponseResponse;
import info.prorabka.varamy.entity.Response;
import info.prorabka.varamy.security.SecurityUser;
import info.prorabka.varamy.service.ResponseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Отклики", description = "Управление откликами на объявления")
public class ResponseController {

    private final ResponseService responseService;

    @PostMapping("/ads/{adId}/responses")
    @Operation(summary = "Откликнуться на объявление")
    public ResponseEntity<ApiResponse<ResponseResponse>> createResponse(
            @AuthenticationPrincipal SecurityUser currentUser,
            @PathVariable UUID adId,
            @RequestBody(required = false) ResponseRequest request) {
        ResponseResponse response = responseService.createResponse(adId, currentUser.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Отклик отправлен", response));
    }

    @GetMapping("/ads/{adId}/responses")
    @Operation(summary = "Получение откликов на объявление")
    public ResponseEntity<ApiResponse<List<ResponseResponse>>> getResponsesForAd(
            @AuthenticationPrincipal SecurityUser currentUser,
            @PathVariable UUID adId) {
        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        List<ResponseResponse> responses = responseService.getResponsesForAd(
                adId, currentUser.getId(), isAdmin);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @PutMapping("/responses/{responseId}")
    public ResponseEntity<ApiResponse<ResponseResponse>> updateResponseStatus(
            @AuthenticationPrincipal SecurityUser currentUser,
            @PathVariable UUID responseId,
            @RequestParam Response.ResponseStatus status) {
        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        ResponseResponse response = responseService.updateResponseStatus(
                responseId, currentUser.getId(), status, isAdmin);
        return ResponseEntity.ok(ApiResponse.success("Статус отклика изменён", response));
    }

    @DeleteMapping("/responses/{responseId}")
    @Operation(summary = "Удаление отклика")
    public ResponseEntity<ApiResponse<Void>> deleteResponse(
            @AuthenticationPrincipal SecurityUser currentUser,
            @PathVariable UUID responseId) {
        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        responseService.deleteResponse(responseId, currentUser.getId(), isAdmin);
        return ResponseEntity.ok(ApiResponse.success("Отклик удалён", null));
    }


    @GetMapping("/responses/my")
    @Operation(summary = "Получение всех объявлений, на которые текущий пользователь откликнулся")
    public ResponseEntity<ApiResponse<Page<MyResponseAdResponse>>> getMyResponses(
        @AuthenticationPrincipal SecurityUser currentUser,
        @PageableDefault(size = 20) Pageable pageable) {
        Page<MyResponseAdResponse> responses = responseService.getMyResponses(currentUser.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }
}
