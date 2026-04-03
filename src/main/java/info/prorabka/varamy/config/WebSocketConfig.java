package info.prorabka.varamy.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Эндпоинт, к которому будет подключаться клиент
        // .withSockJS() нужен для поддержки fallback-опций в браузерах
        registry.addEndpoint("/ws").setAllowedOrigins("*").withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Префикс для каналов, на которые клиент будет подписываться
        registry.enableSimpleBroker("/topic", "/queue");
        // Префикс для запросов от клиента к серверу
        registry.setApplicationDestinationPrefixes("/app");
        // Префикс для личных уведомлений
        registry.setUserDestinationPrefix("/user");
    }
}
