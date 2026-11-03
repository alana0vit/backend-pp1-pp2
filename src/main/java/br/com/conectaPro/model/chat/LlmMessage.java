package br.com.conectaPro.model.chat;

/** Mensagem enviada ao modelo. {@code role} é "system", "user" ou "assistant". */
public record LlmMessage(String role, String content) {}
