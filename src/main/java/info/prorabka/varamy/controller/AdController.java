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
    @Operation(summary = "Получение списка объявлений по городу",
            description = "Возвращает активные объявления (статус ACTIVE) для указанного города. " +
                    "Можно фильтровать по типу, подтипу, уровню. Для просмотра объявлений в других статусах используйте админские методы.")
    public ResponseEntity<ApiResponse<Page<AdResponse>>> getAds(
            @Parameter(description = "ID города", required = true, example = "57")
            @RequestParam(name = "cityId") Long cityId,
            @Parameter(description = "Тип объявления (1-5)", example = "1")
            @RequestParam(name = "type", required = false) Integer type,
            @Parameter(description = "Подтип объявления", example = "1")
            @RequestParam(name = "subType", required = false) Integer subType,
            @Parameter(description = "Статус объявления (по умолчанию ACTIVE)", example = "ACTIVE")
            @RequestParam(name = "status", required = false) Ad.AdStatus status,
            @Parameter(description = "Уровни игроков (список строк A-H)", example = "[\"A\",\"B\"]")
            @RequestParam(name = "level", required = false) List<String> level,
            @PageableDefault(size = 20) Pageable pageable) {

        if (status == null) {
            status = Ad.AdStatus.ACTIVE;
        }

        Page<AdResponse> ads = adService.getAds(cityId, type, subType, status, level, null, pageable);
        return ResponseEntity.ok(ApiResponse.success(ads));
    }

    @GetMapping("/all")
    @Operation(summary = "Получение всех активных объявлений (без фильтрации по городу)",
            description = "Возвращает все объявления со статусом ACTIVE, без привязки к городу.")
    public ResponseEntity<ApiResponse<Page<AdResponse>>> getAllActiveAds(
            @Parameter(description = "Тип объявления (1-5)", example = "1")
            @RequestParam(name = "type", required = false) Integer type,
            @Parameter(description = "Подтип объявления", example = "1")
            @RequestParam(name = "subType", required = false) Integer subType,
            @Parameter(description = "Уровни игроков (список строк A-H)", example = "[\"A\",\"B\"]")
            @RequestParam(name = "level", required = false) List<String> level,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<AdResponse> ads = adService.getAllActiveAds(type, subType, level, pageable);
        return ResponseEntity.ok(ApiResponse.success(ads));
    }

    @PostMapping
    @Operation(summary = "Создание нового объявления",
            description = "Создаёт объявление со статусом ACTIVE (модерация не требуется).")
    public ResponseEntity<ApiResponse<AdResponse>> createAd(
            @AuthenticationPrincipal SecurityUser currentUser,
            @Valid @RequestBody AdRequest request) {
        AdResponse response = adService.createAd(currentUser.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Объявление создано", response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получение объявления по ID",
            description = "Возвращает объявление независимо от его статуса. Доступно всем пользователям.")
    public ResponseEntity<ApiResponse<AdResponse>> getAdById(
            @Parameter(description = "UUID объявления", example = "ae286c07-5abf-4f05-be48-7835b8629909")
            @PathVariable UUID id) {
        AdResponse response = adService.getAdById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Редактирование объявления",
            description = "Редактировать может автор или администратор. При редактировании админом статус сбрасывается на MODERATION.")
    public ResponseEntity<ApiResponse<AdResponse>> updateAd(
            @AuthenticationPrincipal SecurityUser currentUser,
            @Parameter(description = "UUID объявления", example = "ae286c07-5abf-4f05-be48-7835b8629909")
            @PathVariable UUID id,
            @Valid @RequestBody AdRequest request) {
        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        AdResponse response = adService.updateAd(id, currentUser.getId(), request, isAdmin);
        return ResponseEntity.ok(ApiResponse.success("Объявление обновлено", response));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Удаление объявления",
            description = "Удалить может автор или администратор.")
    public ResponseEntity<ApiResponse<Void>> deleteAd(
            @AuthenticationPrincipal SecurityUser currentUser,
            @Parameter(description = "UUID объявления", example = "ae286c07-5abf-4f05-be48-7835b8629909")
            @PathVariable UUID id) {
        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        adService.deleteAd(id, currentUser.getId(), isAdmin);
        return ResponseEntity.ok(ApiResponse.success("Объявление удалено", null));
    }

    @GetMapping("/me")
    @Operation(summary = "Получение списка своих объявлений",
            description = "Возвращает все объявления автора, включая архивные и заполненные.")
    public ResponseEntity<ApiResponse<Page<AdResponse>>> getMyAds(
            @AuthenticationPrincipal SecurityUser currentUser,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<AdResponse> ads = adService.getMyAds(currentUser.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.success(ads));
    }
}