package br.com.conectaPro.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Pergunta ao chatbot. O front envia as últimas trocas em {@code history} para o bot entender
 * perguntas de continuação ("e o plano prata?"); o servidor não guarda conversa.
 */
public record ChatRequest(
    @NotBlank(message = "A mensagem é obrigatória.")
        @Size(max = 500, message = "A mensagem pode ter no máximo 500 caracteres.")
        String message,
    @Size(max = 6, message = "O histórico pode ter no máximo 6 mensagens.") @Valid
        List<Turn> history) {

  /** Uma mensagem anterior da conversa. {@code role} é "user" ou "assistant". */
  public record Turn(@NotBlank String role, @NotBlank @Size(max = 1000) String content) {}
}
