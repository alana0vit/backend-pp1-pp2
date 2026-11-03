package br.com.conectaPro.model.chat;

/** O provedor de LLM não está configurado ou falhou. O chat degrada para o texto do guia. */
public class LlmUnavailableException extends RuntimeException {

  public LlmUnavailableException(String message) {
    super(message);
  }

  public LlmUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
