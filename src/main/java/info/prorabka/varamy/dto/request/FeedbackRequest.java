package info.prorabka.varamy.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FeedbackRequest {
    @NotBlank(message = "Тема обязательна")
    private String subject;

    @NotBlank(message = "Сообщение обязательно")
    private String message;
}