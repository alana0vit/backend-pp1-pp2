package br.com.conectaPro.dto;

import br.com.conectaPro.model.rating.EvaluateStatus;
import br.com.conectaPro.model.rating.Rating;
import java.util.List;

/**
 * Avaliação sem dados pessoais. Em avaliações anônimas o avaliador só aparece para ele mesmo; para
 * os demais, {@code evaluatingPerson} vem nulo (o front já mostra "Anônimo" quando
 * {@code anonymous = true}).
 */
public record RatingResponseDTO(
    Long id,
    Integer points,
    String description,
    Boolean anonymous,
    EvaluateStatus status,
    List<String> imgUrl,
    ServiceRef service,
    PersonRef evaluatingPerson,
    PersonRef personEvaluated) {

  public record ServiceRef(Long id, String title) {}

  public record PersonRef(Long id, String name) {}

  /**
   * @param viewerId usuário que está consultando
   * @param includeService só o avaliador vê a qual demanda a avaliação se refere
   */
  public static RatingResponseDTO fromEntity(Rating r, Long viewerId, boolean includeService) {
    boolean viewerIsEvaluator =
        r.getEvaluatingPerson() != null
            && viewerId != null
            && viewerId.equals(r.getEvaluatingPerson().getId());
    boolean hideEvaluator = Boolean.TRUE.equals(r.getAnonymous()) && !viewerIsEvaluator;

    return new RatingResponseDTO(
        r.getId(),
        r.getPoints(),
        r.getDescription(),
        r.getAnonymous(),
        r.getStatus(),
        r.getImgUrl() == null ? List.of() : List.copyOf(r.getImgUrl()),
        includeService && r.getService() != null
            ? new ServiceRef(r.getService().getId(), r.getService().getTitle())
            : null,
        hideEvaluator || r.getEvaluatingPerson() == null
            ? null
            : new PersonRef(r.getEvaluatingPerson().getId(), r.getEvaluatingPerson().getName()),
        r.getPersonEvaluated() == null
            ? null
            : new PersonRef(r.getPersonEvaluated().getId(), r.getPersonEvaluated().getName()));
  }
}
