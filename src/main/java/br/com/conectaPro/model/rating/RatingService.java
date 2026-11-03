package br.com.conectaPro.model.rating;

import br.com.conectaPro.dto.FinishRatingDTO;
import br.com.conectaPro.model.user.User;
import br.com.conectaPro.model.user.UserRepository;
import br.com.conectaPro.util.Util;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class RatingService {

  @Autowired private RatingRepository repository;

  @Autowired private UserRepository userRepository;

  @Transactional
  public Rating save(Rating rating) {
    // Impede avaliação duplicada para a mesma demanda pelo mesmo avaliador
    List<Rating> existentes =
        repository.findByServiceIdAndEvaluatingPersonId(
            rating.getService().getId(), rating.getEvaluatingPerson().getId());

    boolean jaAvaliou =
        existentes.stream()
            .anyMatch(
                r ->
                    r.getStatus() == EvaluateStatus.PENDENTE
                        || r.getStatus() == EvaluateStatus.COMPLETO);

    if (jaAvaliou) {
      throw new IllegalStateException("Você já enviou uma avaliação para esta demanda.");
    }

    rating.setEnabled(Boolean.TRUE);
    return repository.save(rating);
  }

  public List<Rating> getByUser(Long userId) {
    return repository.findRatingsByUser(userId);
  }

  public List<Rating> getByEvaluator(Long userId) {
    return repository.findRatingsByEvaluator(userId);
  }

  public Rating getUserRating(Long userId, Long ratingId) {
    Rating rating =
        repository
            .findById(ratingId)
            .orElseThrow(() -> new NoSuchElementException("Avaliação não encontrada"));
    if (!rating.getPersonEvaluated().getId().equals(userId)) {
      throw new IllegalStateException("Essa avaliação não pertence ao usuário informado");
    }
    return rating;
  }

  public static final int MAX_FOTOS = 3;
  private static final long MAX_TAMANHO_FOTO = 5L * 1024 * 1024;

  /**
   * Anexa fotos a uma avaliação. Só quem escreveu a avaliação pode anexar, até {@value #MAX_FOTOS}
   * fotos no total, só imagens de até 5 MB cada.
   */
  @Transactional
  public Rating addImages(Long ratingId, Long userId, List<MultipartFile> arquivos) {
    Rating rating =
        repository
            .findById(ratingId)
            .orElseThrow(() -> new NoSuchElementException("Avaliação não encontrada."));

    // Mesma resposta para "não existe" e "não é sua": não revela avaliações de outras pessoas.
    if (rating.getEvaluatingPerson() == null
        || !rating.getEvaluatingPerson().getId().equals(userId)) {
      throw new NoSuchElementException("Avaliação não encontrada.");
    }
    if (rating.getStatus() == EvaluateStatus.REJEITADO) {
      throw new IllegalStateException("Esta avaliação foi descartada.");
    }

    List<MultipartFile> validos = new ArrayList<>();
    for (MultipartFile arquivo : arquivos == null ? List.<MultipartFile>of() : arquivos) {
      if (arquivo == null || arquivo.isEmpty()) {
        continue;
      }
      String tipo = arquivo.getContentType();
      if (tipo == null || !tipo.startsWith("image/")) {
        throw new IllegalArgumentException("Só são aceitas imagens (JPG, PNG, WEBP...).");
      }
      if (arquivo.getSize() > MAX_TAMANHO_FOTO) {
        throw new IllegalArgumentException("Cada foto pode ter no máximo 5 MB.");
      }
      validos.add(arquivo);
    }
    if (validos.isEmpty()) {
      throw new IllegalArgumentException("Nenhuma imagem enviada.");
    }

    List<String> atuais =
        rating.getImgUrl() == null ? new ArrayList<>() : new ArrayList<>(rating.getImgUrl());
    if (atuais.size() + validos.size() > MAX_FOTOS) {
      throw new IllegalStateException(
          "Uma avaliação pode ter no máximo " + MAX_FOTOS + " fotos.");
    }

    for (MultipartFile arquivo : validos) {
      String nome = Util.fazerUploadImagem(arquivo);
      if (nome == null) {
        throw new IllegalStateException("Não foi possível salvar a imagem. Tente novamente.");
      }
      atuais.add(nome);
    }
    rating.setImgUrl(atuais);
    return repository.save(rating);
  }

  @Transactional
  public void finish(Long id, FinishRatingDTO request) {
    Rating rating =
        repository
            .findById(id)
            .orElseThrow(() -> new NoSuchElementException("Avaliação não encontrada."));
    if (rating.getStatus() != EvaluateStatus.PENDENTE) {
      throw new IllegalStateException("Avaliação já finalizada.");
    }
    if (request.getApproved() == null) {
      throw new IllegalArgumentException("approved é obrigatório.");
    }
    if (Boolean.FALSE.equals(request.getApproved())) {
      rating.setStatus(EvaluateStatus.REJEITADO);
      repository.save(rating);
      return;
    }
    if (request.getPoints() == null) {
      throw new IllegalArgumentException("Pontuação obrigatória.");
    }
    if (request.getPoints() < 1 || request.getPoints() > 5) {
      throw new IllegalArgumentException("Pontuação inválida.");
    }
    rating.setPoints(request.getPoints());
    rating.setDescription(request.getDescription());
    rating.setAnonymous(request.getAnonymous());
    rating.setStatus(EvaluateStatus.COMPLETO);
    repository.save(rating);

    User evaluated = rating.getPersonEvaluated();
    Double avg = repository.calculateAverageByUser(evaluated.getId());
    evaluated.setRating(avg != null ? avg : 0.0);
    userRepository.save(evaluated);
  }
}
