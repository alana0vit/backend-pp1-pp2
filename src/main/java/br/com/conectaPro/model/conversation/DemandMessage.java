package br.com.conectaPro.model.conversation;

import br.com.conectaPro.model.demand.Demand;
import br.com.conectaPro.model.user.User;
import br.com.conectaPro.util.entity.BusinessEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Mensagem do chat entre cliente e profissional de uma demanda (liberado após o pagamento). */
@Entity
@Table(
    name = "demand_message",
    indexes = {@Index(name = "idx_demand_message_demand", columnList = "demand_id")})
@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DemandMessage extends BusinessEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private Demand demand;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private User sender;

  @Column(nullable = false, length = 1000)
  private String content;

  @Column(nullable = false)
  private LocalDateTime sentAt;

  /** Preenchido quando o destinatário abre a conversa. */
  @Column private LocalDateTime readAt;
}
