package br.com.conectaPro.config;

import br.com.conectaPro.security.StompAuthChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Chat em tempo real (WebSocket + STOMP) com broker em memória: suficiente para uma única
 * instância. Para escalar para várias instâncias seria preciso trocar por um broker externo.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private final StompAuthChannelInterceptor authInterceptor;

  @Value("${app.frontend.url:http://localhost:5173}")
  private String frontendUrl;

  @Value("${websocket.heartbeat-ms:15000}")
  private long heartbeatMs;

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    String front =
        frontendUrl.endsWith("/")
            ? frontendUrl.substring(0, frontendUrl.length() - 1)
            : frontendUrl;
    registry
        .addEndpoint("/ws")
        .setAllowedOriginPatterns(front, "http://localhost:*", "http://127.0.0.1:*");
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("ws-heartbeat-");
    scheduler.initialize();

    // heartbeat evita que proxies/hospedagem derrubem conexões ociosas
    registry
        .enableSimpleBroker("/topic", "/queue")
        .setHeartbeatValue(new long[] {heartbeatMs, heartbeatMs})
        .setTaskScheduler(scheduler);
    registry.setApplicationDestinationPrefixes("/app");
    registry.setUserDestinationPrefix("/user");
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(authInterceptor);
  }
}
