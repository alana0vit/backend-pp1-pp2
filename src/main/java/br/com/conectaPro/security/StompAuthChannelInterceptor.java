package br.com.conectaPro.security;

import br.com.conectaPro.model.conversation.DemandMessageService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * Autentica e autoriza o tráfego STOMP. O navegador não consegue mandar o header Authorization no
 * handshake do WebSocket, então o JWT vai no frame CONNECT. Depois disso, cada SUBSCRIBE é
 * conferido: só participantes de uma demanda com chat liberado entram no tópico dela.
 */
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

  private static final Pattern DEMAND_TOPIC = Pattern.compile("^/topic/demand\\.(\\d+)$");
  private static final String USER_QUEUE_PREFIX = "/user/queue/";

  private final JwtService jwtService;
  private final CustomUserDetailsService userDetailsService;
  // @Lazy evita ciclo: o interceptor faz parte da configuração do canal, e o serviço usa o
  // SimpMessagingTemplate que depende desse mesmo canal.
  @Autowired @Lazy private DemandMessageService messageService;

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (accessor == null || accessor.getCommand() == null) {
      return message;
    }

    StompCommand command = accessor.getCommand();

    if (StompCommand.CONNECT.equals(command)) {
      accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
    } else if (StompCommand.SUBSCRIBE.equals(command)) {
      authorizeSubscription(accessor);
    } else if (StompCommand.SEND.equals(command) && accessor.getUser() == null) {
      throw new MessagingException("Não autenticado.");
    }
    return message;
  }

  private Authentication authenticate(String authHeader) {
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      throw new MessagingException("Token ausente.");
    }
    try {
      String jwt = authHeader.substring(7);
      String email = jwtService.extractUsername(jwt);
      UserDetails userDetails = userDetailsService.loadUserByUsername(email);
      if (!jwtService.isTokenValid(jwt, userDetails)) {
        throw new MessagingException("Token inválido.");
      }
      return new UsernamePasswordAuthenticationToken(
          userDetails, null, userDetails.getAuthorities());
    } catch (MessagingException e) {
      throw e;
    } catch (Exception e) {
      throw new MessagingException("Token inválido ou expirado.");
    }
  }

  private void authorizeSubscription(StompHeaderAccessor accessor) {
    if (!(accessor.getUser() instanceof UsernamePasswordAuthenticationToken auth)
        || !(auth.getPrincipal() instanceof CustomUserDetails details)) {
      throw new MessagingException("Não autenticado.");
    }

    String destination = accessor.getDestination();
    if (destination == null) {
      throw new MessagingException("Destino ausente.");
    }

    Matcher matcher = DEMAND_TOPIC.matcher(destination);
    if (matcher.matches()) {
      try {
        messageService.assertCanChat(Long.valueOf(matcher.group(1)), details.getUser().getId());
      } catch (RuntimeException e) {
        throw new MessagingException("Sem permissão para esta conversa: " + e.getMessage());
      }
      return;
    }

    // Filas pessoais (notificações / erros) são entregues somente ao próprio usuário.
    if (destination.startsWith(USER_QUEUE_PREFIX)) {
      return;
    }

    throw new MessagingException("Destino não permitido.");
  }
}
