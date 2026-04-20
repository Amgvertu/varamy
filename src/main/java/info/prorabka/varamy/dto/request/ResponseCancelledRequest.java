package info.prorabka.varamy.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.UUID;

@Data
public class ResponseCancelledRequest {
    @NotNull(message = "adId обязателен")
    private UUID adId;

    @NotNull(message = "userId обязателен")
    private UUID userId;
}