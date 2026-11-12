package br.com.conectaPro.api.rating;

import br.com.conectaPro.dto.FinishRatingDTO;
import br.com.conectaPro.dto.RatingResponseDTO;
import br.com.conectaPro.model.demand.Demand;
import br.com.conectaPro.model.demand.DemandService;
import br.com.conectaPro.model.rating.EvaluateStatus;
import br.com.conectaPro.model.rating.Rating;
import br.com.conectaPro.model.rating.RatingService;
import br.com.conectaPro.model.user.User;
import br.com.conectaPro.model.user.UserService;
import br.com.conectaPro.security.CustomUserDetails;
import br.com.conectaPro.util.Authz;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/rating")
@CrossOrigin
@Tag(name = "Rating", description = "Avaliação pós serviço")
public class RatingController {

  @Autowired private RatingService ratingService;

  @Autowired private DemandService demandService;

  @Autowired private UserService userService;

  @Operation(summary = "Criar entidade Avaliação")
  @PostMapping()
  public ResponseEntity<?> save(
      @RequestBody @Valid RatingRequest request,
      @AuthenticationPrincipal CustomUserDetails principal) {
    // o avaliador é sempre o usuário logado
    Authz.requireSelf(request.getEvaluatingPerson(), principal);

    try {
      User evaluator = userService.getById(request.getEvaluatingPerson());
      User evaluated = userService.getById(request.getPersonEvaluated());
      // só quem participa da demanda avalia (404 para estranhos)
      Demand service = demandService.getByIdForUser(request.getService(), principal.getUser().getId());

      Rating ratingNew = request.build();
      ratingNew.setService(service);
      ratingNew.setEvaluatingPerson(evaluator);
      ratingNew.setPersonEvaluated(evaluated);
      ratingNew.setStatus(EvaluateStatus.PENDENTE);

      Rating rating = ratingService.save(ratingNew);
      return new ResponseEntity<>(
          RatingResponseDTO.fromEntity(rating, principal.getUser().getId(), true),
          HttpStatus.CREATED);
    } catch (NoSuchElementException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body("Um dos IDs informados (Avaliador, Avaliado ou demanda) não existe.");
    } catch (IllegalStateException e) {
      return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }
  }

  @Operation(summary = "Lista todas as avaliações de um usuario")
  @GetMapping("/user/{userId}")
  public List<RatingResponseDTO> getByUser(
      @PathVariable Long userId, @AuthenticationPrincipal CustomUserDetails principal) {
    Long viewerId = Authz.userId(principal);
    // vitrine pública do profissional: só avaliações concluídas, sem dados do avaliador anônimo
    return ratingService.getByUser(userId).stream()
        .filter(r -> r.getStatus() == EvaluateStatus.COMPLETO)
        .map(r -> RatingResponseDTO.fromEntity(r, viewerId, false))
        .toList();
  }

  @Operation(summary = "Lista todas as avaliações feitas por um usuario")
  @GetMapping("/user/{userId}/evaluator")
  public List<RatingResponseDTO> getByEvaluator(
      @PathVariable Long userId, @AuthenticationPrincipal CustomUserDetails principal) {
    Authz.requireSelf(userId, principal); // "avaliações que eu fiz": só as minhas
    return ratingService.getByEvaluator(userId).stream()
        .map(r -> RatingResponseDTO.fromEntity(r, userId, true))
        .toList();
  }

  @Operation(summary = "Lista uma avaliação especifica de usuario")
  @GetMapping("/user/{userId}/rating/{ratingId}")
  public RatingResponseDTO getUserRating(
      @PathVariable Long userId,
      @PathVariable Long ratingId,
      @AuthenticationPrincipal CustomUserDetails principal) {
    return RatingResponseDTO.fromEntity(
        ratingService.getUserRating(userId, ratingId), Authz.userId(principal), false);
  }

  @Operation(
      summary = "Anexa fotos à avaliação (só o autor, até 3 fotos de 5 MB)",
      description = "multipart/form-data com o campo 'imagens' (pode repetir).")
  @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<RatingResponseDTO> addImages(
      @PathVariable Long id,
      @RequestParam("imagens") List<MultipartFile> imagens,
      @AuthenticationPrincipal CustomUserDetails principal) {
    Long userId = principal.getUser().getId();
    return ResponseEntity.ok(
        RatingResponseDTO.fromEntity(ratingService.addImages(id, userId, imagens), userId, true));
  }

  @Operation(
      summary = "Usuario preenche avaliação",
      description = "Em teoria, o user nunca atualiza a avaliação")
  @PutMapping("/{id}")
  public ResponseEntity<Void> finish(
      @PathVariable Long id,
      @RequestBody FinishRatingDTO request,
      @AuthenticationPrincipal CustomUserDetails principal) {
    ratingService.finishForUser(id, principal.getUser().getId(), request);
    return ResponseEntity.ok().build();
  }
}
