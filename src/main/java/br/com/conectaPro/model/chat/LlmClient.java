package br.com.conectaPro.model.chat;

import java.util.List;

/**
 * Abstração do provedor de LLM. Hoje: {@link CloudflareLlmClient} (Workers AI, free tier). Para
 * trocar de provedor (ex.: Claude), basta outra implementação e deixá-la como o bean ativo.
 */
public interface LlmClient {

  /**
   * Envia a conversa ao modelo e devolve o texto da resposta.
   *
   * @throws LlmUnavailableException se o provedor não estiver configurado ou falhar
   */
  String complete(List<LlmMessage> messages);
}
