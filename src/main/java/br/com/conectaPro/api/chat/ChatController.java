package br.com.conectaPro.api.chat;

import br.com.conectaPro.dto.ChatRequest;
import br.com.conectaPro.dto.ChatResponse;
import br.com.conectaPro.model.chat.ChatRateLimiter;
import br.com.conectaPro.model.chat.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin
@Tag(
    name = "Chat",
    description = "Chatbot (RAG) que responde dúvidas sobre a plataforma e sugere categorias")
public class ChatController {

  @Autowired private ChatService chatService;

  @Autowired private ChatRateLimiter rateLimiter;

  /** Só ligar atrás de um proxy/load balancer confiável; senão o cliente poderia forjar o IP. */
  @Value("${chat.trust-forwarded-header:false}")
  private boolean trustForwardedHeader;

  @PostMapping
  @Operation(
      summary = "Pergunta ao chatbot (público, com limite de uso por IP)",
      description =
          "Responde com base nos guias e categorias da plataforma. 'history' é opcional "
              + "(até 6 mensagens anteriores, role user/assistant).")
  public ResponseEntity<?> chat(
      @Valid @RequestBody ChatRequest request, HttpServletRequest httpRequest) {

    ChatRateLimiter.Decision decision = rateLimiter.tryAcquire(clientKey(httpRequest));
    if (decision != ChatRateLimiter.Decision.ALLOWED) {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
      body.put(
          "message",
          decision == ChatRateLimiter.Decision.CLIENT_LIMIT
              ? "Você fez muitas perguntas em pouco tempo. Tente novamente mais tarde."
              : "O assistente atingiu o limite de uso de hoje. Tente novamente amanhã.");
      return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(body);
    }

    ChatResponse response = chatService.ask(request.message(), request.history());
    return ResponseEntity.ok(response);
  }

  private String clientKey(HttpServletRequest request) {
    if (trustForwardedHeader) {
      String forwarded = request.getHeader("X-Forwarded-For");
      if (forwarded != null && !forwarded.isBlank()) {
        return forwarded.split(",")[0].trim();
      }
    }
    return request.getRemoteAddr();
  }
}
