package br.com.conectaPro.model.rating;

import br.com.conectaPro.model.demand.Demand;
import br.com.conectaPro.model.user.User;
import br.com.conectaPro.util.entity.AudibleEntity;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "Rating")
@SQLRestriction("enabled = true")
@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Rating extends AudibleEntity {

  @ManyToOne
  @JoinColumn(name = "service_id")
  private Demand service;

  @ManyToOne
  @JoinColumn(name = "evaluating_person_id")
  private User evaluatingPerson;

  @ManyToOne
  @JoinColumn(name = "person_evaluated_id")
  private User personEvaluated;

  @Column private Integer points;

  @Column(length = 500)
  private String description;

  @Column private Boolean anonymous;

  @Enumerated(EnumType.STRING)
  private EvaluateStatus status;

  /** Nomes das fotos anexadas à avaliação (servidas por GET /api/images/{nome}). */
  @Builder.Default
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "rating_images", joinColumns = @JoinColumn(name = "rating_id"))
  @Column(name = "img_url")
  private List<String> imgUrl = new ArrayList<>();
}
