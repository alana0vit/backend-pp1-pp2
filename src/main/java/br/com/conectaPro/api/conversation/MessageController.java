package br.com.conectaPro.api.conversation;

import br.com.conectaPro.dto.MessageResponseDTO;
import br.com.conectaPro.dto.SendMessageDTO;
import br.com.conectaPro.model.conversation.DemandMessageService;
import br.com.conectaPro.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lado REST do chat: histórico, não lidas e envio (usado como fallback quando o WebSocket está fora
 * do ar). Tempo real: conectar em ws://host/ws e assinar /topic/demand.{id}.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "Conversa cliente–profissional (liberada após o pagamento)")
public class MessageController {

  private final DemandMessageService messageService;

  @Operation(summary = "Histórico da conversa da demanda (últimas 200 mensagens)")
  @GetMapping("/demand/{demandId}/messages")
  public List<MessageResponseDTO> history(
      @PathVariable Long demandId, @AuthenticationPrincipal CustomUserDetails principal) {
    return messageService.history(demandId, principal.getUser().getId());
  }

  @Operation(summary = "Envia mensagem (fallback REST; também distribui em tempo real)")
  @PostMapping("/demand/{demandId}/messages")
  public MessageResponseDTO send(
      @PathVariable Long demandId,
      @Valid @RequestBody SendMessageDTO body,
      @AuthenticationPrincipal CustomUserDetails principal) {
    return messageService.send(demandId, principal.getUser().getId(), body.content());
  }

  @Operation(summary = "Marca como lidas as mensagens recebidas nesta demanda")
  @PostMapping("/demand/{demandId}/messages/read")
  public ResponseEntity<Map<String, Integer>> markAsRead(
      @PathVariable Long demandId, @AuthenticationPrincipal CustomUserDetails principal) {
    int updated = messageService.markAsRead(demandId, principal.getUser().getId());
    return ResponseEntity.ok(Map.of("updated", updated));
  }

  @Operation(summary = "Mensagens não lidas por demanda: { demandId: quantidade }")
  @GetMapping("/messages/unread")
  public Map<Long, Long> unread(@AuthenticationPrincipal CustomUserDetails principal) {
    return messageService.unreadByDemand(principal.getUser().getId());
  }
}
