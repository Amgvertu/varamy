package info.prorabka.varamy.controller;

import info.prorabka.varamy.dto.request.AdRequest;
import info.prorabka.varamy.dto.response.AdResponse;
import info.prorabka.varamy.dto.response.ApiResponse;
import info.prorabka.varamy.entity.Ad;
import info.prorabka.varamy.security.SecurityUser;
import info.prorabka.varamy.service.AdService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/ads")
@RequiredArgsConstructor
@Tag(name = "Объявления", description = "Управление объявлениями")
public class AdController {

    private final AdService adService;

    @GetMapping
    @Operation(summary = "Получение списка объявлений по городу (обязателен cityId)")
    public ResponseEntity<ApiResponse<Page<AdResponse>>> getAds(
            @Parameter(description = "ID города", required = true, example = "57")
            @RequestParam(name = "cityId") Long cityId,
            @Parameter(description = "Тип объявления (1-5)", example = "1")
            @RequestParam(name = "type", required = false) Integer type,
            @Parameter(description = "Подтип объявления", example = "1")
            @RequestParam(name = "subType", required = false) Integer subType,
            @Parameter(description = "Статус объявления", example = "ACTIVE")
            @RequestParam(name = "status", required = false) Ad.AdStatus status,
            @Parameter(description = "Уровни игроков", example = "[\"A\",\"B\"]")
            @RequestParam(name = "level", required = false) List<String> level,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<AdResponse> ads = adService.getAds(cityId, type, subType, status, level, null, pageable);
        return ResponseEntity.ok(ApiResponse.success(ads));
    }

    @GetMapping("/all")
    @Operation(summary = "Получение всех активных объявлений (без фильтрации по городу)")
    public ResponseEntity<ApiResponse<Page<AdResponse>>> getAllActiveAds(
            @Parameter(description = "Тип объявления (1-5)", example = "1")
            @RequestParam(name = "type", required = false) Integer type,
            @Parameter(description = "Подтип объявления", example = "1")
            @RequestParam(name = "subType", required = false) Integer subType,
            @Parameter(description = "Уровни игроков", example = "[\"A\",\"B\"]")
            @RequestParam(name = "level", required = false) List<String> level,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<AdResponse> ads = adService.getAllActiveAds(type, subType, level, pageable);
        return ResponseEntity.ok(ApiResponse.success(ads));
    }

    @PostMapping
    @Operation(summary = "Создание нового объявления")
    public ResponseEntity<ApiResponse<AdResponse>> createAd(
            @AuthenticationPrincipal SecurityUser currentUser,
            @Valid @RequestBody AdRequest request) {
        AdResponse response = adService.createAd(currentUser.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Объявление создано и отправлено на модерацию", response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получение объявления по ID")
    public ResponseEntity<ApiResponse<AdResponse>> getAdById(@PathVariable UUID id) {
        AdResponse response = adService.getAdById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Редактирование объявления")
    public ResponseEntity<ApiResponse<AdResponse>> updateAd(
            @AuthenticationPrincipal SecurityUser currentUser,
            @PathVariable UUID id,
            @Valid @RequestBody AdRequest request) {
        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        AdResponse response = adService.updateAd(id, currentUser.getId(), request, isAdmin);
        return ResponseEntity.ok(ApiResponse.success("Объявление обновлено", response));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Удаление объявления")
    public ResponseEntity<ApiResponse<Void>> deleteAd(
            @AuthenticationPrincipal SecurityUser currentUser,
            @PathVariable UUID id) {
        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        adService.deleteAd(id, currentUser.getId(), isAdmin);
        return ResponseEntity.ok(ApiResponse.success("Объявление удалено", null));
    }

    @GetMapping("/me")
    @Operation(summary = "Получение списка своих объявлений")
    public ResponseEntity<ApiResponse<Page<AdResponse>>> getMyAds(
            @AuthenticationPrincipal SecurityUser currentUser,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<AdResponse> ads = adService.getMyAds(currentUser.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.success(ads));
    }
}