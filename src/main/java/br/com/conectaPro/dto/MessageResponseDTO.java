package br.com.conectaPro.dto;

import br.com.conectaPro.model.conversation.DemandMessage;
import java.time.LocalDateTime;

public record MessageResponseDTO(
    Long id,
    Long demandId,
    Long senderId,
    String senderName,
    String content,
    LocalDateTime sentAt,
    LocalDateTime readAt) {

  public static MessageResponseDTO fromEntity(DemandMessage m) {
    return new MessageResponseDTO(
        m.getId(),
        m.getDemand().getId(),
        m.getSender().getId(),
        m.getSender().getName(),
        m.getContent(),
        m.getSentAt(),
        m.getReadAt());
  }
}
