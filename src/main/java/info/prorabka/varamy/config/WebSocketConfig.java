package info.prorabka.varamy.config;

import info.prorabka.varamy.service.JwtService;
import info.prorabka.varamy.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Slf4j
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwtService;
    private final UserService userService;
    private final AuthChannelInterceptor authChannelInterceptor;

    public WebSocketConfig(JwtService jwtService, UserService userService,
                           AuthChannelInterceptor authChannelInterceptor) {
        this.jwtService = jwtService;
        this.userService = userService;
        this.authChannelInterceptor = authChannelInterceptor;
    }

    @Bean
    public ThreadPoolTaskScheduler webSocketTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        log.info("Регистрация STOMP эндпоинта /ws");
        registry.addEndpoint("/ws")
                .setAllowedOrigins("*")
                .addInterceptors(new JwtHandshakeInterceptor(jwtService, userService));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue", "/topic")
                .setHeartbeatValue(new long[]{10000, 10000})   // клиент и сервер каждые 10 сек
                .setTaskScheduler(webSocketTaskScheduler());  // ОБЯЗАТЕЛЬНО!
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authChannelInterceptor);
    }
}