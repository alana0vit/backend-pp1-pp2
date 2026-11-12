package br.com.conectaPro.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageDTO(
    @NotBlank(message = "A mensagem não pode ficar vazia.")
        @Size(max = 1000, message = "A mensagem deve ter no máximo 1000 caracteres.")
        String content) {}
