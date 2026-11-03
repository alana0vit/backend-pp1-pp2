package br.com.conectaPro.dto;

import java.util.List;

/**
 * Resposta do chatbot.
 *
 * @param answer texto para exibir ao usuário
 * @param suggestedCategory nome da categoria de serviço sugerida (para link de busca) ou null
 * @param sources títulos dos guias usados na resposta
 * @param found false quando nada na base cobre a pergunta (resposta padrão de "não sei")
 */
public record ChatResponse(
    String answer, String suggestedCategory, List<String> sources, boolean found) {}
