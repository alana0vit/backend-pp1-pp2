package br.com.conectaPro.api.conversation;

import br.com.conectaPro.dto.SendMessageDTO;
import br.com.conectaPro.model.conversation.DemandMessageService;
import br.com.conectaPro.security.CustomUserDetails;
import java.security.Principal;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

/** Envio de mensagens via STOMP: o cliente publica em /app/demand/{id}/send. */
@Controller
@RequiredArgsConstructor
public class MessageSocketController {

  private final DemandMessageService messageService;

  @MessageMapping("/demand/{demandId}/send")
  public void send(
      @DestinationVariable Long demandId, @Payload SendMessageDTO payload, Principal principal) {
    if (!(principal instanceof UsernamePasswordAuthenticationToken auth)
        || !(auth.getPrincipal() instanceof CustomUserDetails details)) {
      throw new IllegalStateException("Não autenticado.");
    }
    // salva e distribui (tópico da conversa + notificação ao destinatário)
    messageService.send(demandId, details.getUser().getId(), payload.content());
  }

  /** Erros de regra (chat bloqueado, mensagem vazia...) voltam só para quem enviou. */
  @MessageExceptionHandler
  @SendToUser("/queue/errors")
  public Map<String, String> handleError(Exception e) {
    return Map.of("message", e.getMessage() == null ? "Erro ao enviar mensagem." : e.getMessage());
  }
}
