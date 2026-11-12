package br.com.conectaPro.dto;

import br.com.conectaPro.model.payment.Payment;
import br.com.conectaPro.model.payment.PaymentStatus;
import br.com.conectaPro.model.payment.PaymentType;
import java.time.LocalDateTime;

/** Pagamento sem os dados pessoais de cliente/profissional que a entidade carregava. */
public record PaymentResponseDTO(
    Long id,
    PaymentType type,
    Long demandId,
    Long subscriptionId,
    Double amount,
    Double platformFeeAmount,
    Double professionalAmount,
    Double platformFeePercentage,
    PaymentStatus status,
    LocalDateTime paidAt) {

  public static PaymentResponseDTO fromEntity(Payment p) {
    return new PaymentResponseDTO(
        p.getId(),
        p.getType(),
        p.getDemand() == null ? null : p.getDemand().getId(),
        p.getSubscription() == null ? null : p.getSubscription().getId(),
        p.getAmount(),
        p.getPlatformFeeAmount(),
        p.getProfessionalAmount(),
        p.getPlatformFeePercentage(),
        p.getStatus(),
        p.getPaidAt());
  }
}
