package br.com.conectaPro.Rating;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import br.com.conectaPro.dto.RatingResponseDTO;
import br.com.conectaPro.model.demand.Demand;
import br.com.conectaPro.model.rating.EvaluateStatus;
import br.com.conectaPro.model.rating.Rating;
import br.com.conectaPro.model.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RatingResponseDTOTest {

  private Rating avaliacao(boolean anonima) {
    User avaliador = new User();
    avaliador.setId(1L);
    avaliador.setName("Maria");
    User avaliado = new User();
    avaliado.setId(2L);
    avaliado.setName("João");
    Demand demanda = new Demand();
    demanda.setId(7L);
    demanda.setTitle("Troca de torneira");

    return Rating.builder()
        .evaluatingPerson(avaliador)
        .personEvaluated(avaliado)
        .service(demanda)
        .points(5)
        .anonymous(anonima)
        .status(EvaluateStatus.COMPLETO)
        .build();
  }

  @Test
  @DisplayName("Avaliação anônima esconde o avaliador de terceiros, mas não dele mesmo")
  void anonimaEscondeAvaliador() {
    Rating r = avaliacao(true);

    assertNull(RatingResponseDTO.fromEntity(r, 99L, false).evaluatingPerson());
    assertNull(RatingResponseDTO.fromEntity(r, null, false).evaluatingPerson());
    assertEquals("Maria", RatingResponseDTO.fromEntity(r, 1L, true).evaluatingPerson().name());
  }

  @Test
  @DisplayName("Avaliação identificada mostra só id e nome; a demanda só vai para o avaliador")
  void identificadaMostraNomeSemDemanda() {
    Rating r = avaliacao(false);

    RatingResponseDTO publica = RatingResponseDTO.fromEntity(r, 99L, false);
    assertEquals("Maria", publica.evaluatingPerson().name());
    assertNull(publica.service());
    assertNotNull(RatingResponseDTO.fromEntity(r, 1L, true).service());
  }
}
