package info.prorabka.varamy.config;

import info.prorabka.varamy.service.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            List<String> tokens = accessor.getNativeHeader("token");
            String token = (tokens != null && !tokens.isEmpty()) ? tokens.get(0) : null;
            if (token != null && jwtService.validateToken(token)) {
                String userId = jwtService.getUserIdFromToken(token);
                Principal user = () -> userId;
                accessor.setUser(user);
                log.info("STOMP authenticated user: {}", userId);
            } else {
                log.warn("STOMP connection rejected: invalid or missing token");
                return null; // отклоняем соединение
            }
        }
        return message;
    }
}