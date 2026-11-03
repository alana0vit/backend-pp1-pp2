package br.com.conectaPro.model.chat;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Cliente do Cloudflare Workers AI (API REST). Precisa de um Account ID e de um API Token com a
 * permissão "Workers AI". Sem token configurado, lança {@link LlmUnavailableException} e o chat
 * responde só com o texto dos guias.
 */
@Component
public class CloudflareLlmClient implements LlmClient {

  @Value("${cloudflare.ai.account-id:}")
  private String accountId;

  @Value("${cloudflare.ai.api-token:}")
  private String apiToken;

  @Value("${cloudflare.ai.model:@cf/meta/llama-3.1-8b-instruct}")
  private String model;

  @Value("${cloudflare.ai.base-url:https://api.cloudflare.com/client/v4}")
  private String baseUrl;

  @Value("${cloudflare.ai.max-tokens:400}")
  private int maxTokens;

  @Value("${cloudflare.ai.timeout-seconds:20}")
  private int timeoutSeconds;

  private RestClient restClient;

  @PostConstruct
  void init() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(5));
    factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
    this.restClient = RestClient.builder().requestFactory(factory).build();

    if (!isConfigured()) {
      System.out.println(
          "Chatbot: CLOUDFLARE_AI_API_TOKEN/ACCOUNT_ID não configurados; "
              + "as respostas virão direto dos guias, sem LLM.");
    }
  }

  public boolean isConfigured() {
    return accountId != null && !accountId.isBlank() && apiToken != null && !apiToken.isBlank();
  }

  @Override
  public String complete(List<LlmMessage> messages) {
    if (!isConfigured()) {
      throw new LlmUnavailableException("Cloudflare Workers AI não configurado.");
    }

    Map<String, Object> body =
        Map.of(
            "messages",
            messages.stream().map(m -> Map.of("role", m.role(), "content", m.content())).toList(),
            "max_tokens",
            maxTokens,
            "temperature",
            0.2);

    try {
      JsonNode json =
          restClient
              .post()
              .uri(baseUrl + "/accounts/" + accountId + "/ai/run/" + model)
              .header("Authorization", "Bearer " + apiToken)
              .contentType(MediaType.APPLICATION_JSON)
              .body(body)
              .retrieve()
              .body(JsonNode.class);

      String text = json == null ? "" : json.path("result").path("response").asText("").trim();
      if (text.isEmpty()) {
        throw new LlmUnavailableException("Resposta vazia do Workers AI.");
      }
      return text;
    } catch (RestClientException e) {
      // A mensagem do RestClientException não inclui o token (ele só vai no header).
      throw new LlmUnavailableException("Falha ao chamar o Workers AI: " + e.getMessage(), e);
    }
  }
}
