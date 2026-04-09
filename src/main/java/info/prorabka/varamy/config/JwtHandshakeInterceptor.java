package info.prorabka.varamy.config;

import info.prorabka.varamy.entity.User;
import info.prorabka.varamy.service.JwtService;
import info.prorabka.varamy.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtService jwtService;
    private final UserService userService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String token = servletRequest.getServletRequest().getParameter("token");
            System.out.println("=== JwtHandshakeInterceptor called, token present: " + (token != null));
            log.info("=== JwtHandshakeInterceptor called, token present: {}", token != null);
            if (token != null && jwtService.validateToken(token)) {
                try {
                    String userId = jwtService.getUserIdFromToken(token);
                    User user = userService.getUserById(UUID.fromString(userId));
                    if (user != null && user.getStatus() == User.UserStatus.ACTIVE) {
                        // КЛЮЧЕВЫЕ СТРОКИ:
                        attributes.put("userId", userId);
                        attributes.put(Principal.class.getName(), (Principal) () -> userId);
                        log.info("WebSocket authenticated for user: {}", userId);
                        return true;
                    }
                } catch (Exception e) {
                    log.error("Authentication error: ", e);
                }
            }
            log.warn("WebSocket authentication failed");
            return false;
        }
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // Ничего не делаем
    }
}