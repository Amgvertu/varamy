package info.prorabka.varamy.service;

import info.prorabka.varamy.dto.request.FeedbackRequest;
import info.prorabka.varamy.entity.FeedbackMessage;
import info.prorabka.varamy.entity.User;
import info.prorabka.varamy.exception.BadRequestException;
import info.prorabka.varamy.repository.FeedbackMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeedbackService {
    private final FeedbackMessageRepository feedbackRepository;
    private final JavaMailSender mailSender;
    private final UserService userService;

    @Value("${feedback.email.to:feedback@katok.pro}")
    private String feedbackEmail;

    @Transactional
    public void sendFeedback(UUID userId, FeedbackRequest request) {
        User user = userService.getUserById(userId);
        var profile = user.getProfile();
        if (profile == null) {
            throw new BadRequestException("Профиль пользователя не заполнен");
        }
        String fullName = (profile.getFirstName() != null ? profile.getFirstName() : "")
                + " " + (profile.getLastName() != null ? profile.getLastName() : "");
        if (fullName.isBlank()) {
            throw new BadRequestException("Имя и фамилия не заполнены в профиле");
        }
        String phone = user.getPhone();
        if (phone == null || phone.isBlank()) {
            throw new BadRequestException("Телефон не заполнен");
        }
        String email = profile.getEmail();
        if (email == null || email.isBlank()) {
            throw new BadRequestException("E-mail не заполнен в профиле");
        }

        // Сохраняем сообщение в БД
        FeedbackMessage msg = new FeedbackMessage();
        msg.setUser(user);
        msg.setFullName(fullName);
        msg.setPhone(phone);
        msg.setEmail(email);
        msg.setSubject(request.getSubject());
        msg.setMessage(request.getMessage());
        msg.setStatus(FeedbackMessage.FeedbackStatus.NEW);
        feedbackRepository.save(msg);

        // Отправляем email
        try {
            sendEmail(fullName, phone, email, request.getSubject(), request.getMessage());
            msg.setStatus(FeedbackMessage.FeedbackStatus.SENT);
            feedbackRepository.save(msg);
            log.info("Feedback sent for user {}: subject={}", userId, request.getSubject());
        } catch (Exception e) {
            log.error("Failed to send feedback email", e);
            msg.setStatus(FeedbackMessage.FeedbackStatus.FAILED);
            feedbackRepository.save(msg);
            throw new RuntimeException("Не удалось отправить сообщение, попробуйте позже", e);
        }
    }

    private void sendEmail(String fullName, String phone, String email, String subject, String message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setTo(feedbackEmail);
        mail.setSubject("Обратная связь: " + subject);
        String text = String.format("""
                Отправитель: %s
                Телефон: %s
                E-mail: %s
                
                Сообщение:
                %s
                """, fullName, phone, email, message);
        mail.setText(text);
        mailSender.send(mail);
    }
}