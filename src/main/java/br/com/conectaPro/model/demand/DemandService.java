package br.com.conectaPro.model.demand;

import br.com.conectaPro.model.user.User;
import br.com.conectaPro.model.user.UserService;
import br.com.conectaPro.security.EmailService;
import br.com.conectaPro.util.Util;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

@Service
public class DemandService {

  @Autowired private DemandRepository repository;

  @Autowired private UserService userService;

  @Autowired private EmailService emailService;

  @Transactional
  public Demand save(Demand demand) {
    demand.setEnabled(Boolean.TRUE);
    demand.setOpenedAt(LocalDateTime.now()); // marca quando foi aberta para o cron

    Demand saved = repository.save(demand);

    // Notifica o profissional sobre a nova solicitação
    try {
      emailService.notificarNovaDemanda(saved);
    } catch (Exception e) {
      System.err.println("Erro ao notificar nova demanda: " + e.getMessage());
    }

    return saved;
  }

  public List<Demand> getAll() {
    return repository.findAll();
  }

  /** Demandas em que o usuário é o cliente ou o profissional (nunca as dos outros). */
  public List<Demand> getAllForUser(Long userId) {
    return repository.findAll().stream().filter(d -> isParticipant(d, userId)).toList();
  }

  /** Igual a getById, mas devolve "não encontrada" (404) se o usuário não participa da demanda. */
  public Demand getByIdForUser(@NonNull Long id, Long userId) {
    Demand demand = getById(id);
    if (!isParticipant(demand, userId)) {
      throw new NoSuchElementException("Demanda não encontrada com ID: " + id);
    }
    return demand;
  }

  private boolean isParticipant(Demand demand, Long userId) {
    if (userId == null) {
      return false;
    }
    User client = demand.getClientId();
    User professional = demand.getProfessionalId();
    return (client != null && userId.equals(client.getId()))
        || (professional != null && userId.equals(professional.getId()));
  }

  public Demand getById(@NonNull Long id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new NoSuchElementException("Demanda não encontrada com ID: " + id));
  }

  @Transactional
  public void update(@NonNull Long id, Demand demandChanged) {
    Demand demand =
        repository
            .findById(id)
            .orElseThrow(() -> new NoSuchElementException("Solicitação não encontrada."));

    if (demand.getDemandStatus() != DemandStatus.ABERTO) {
      throw new IllegalStateException("Não é permitido editar um serviço aceito ou finalizado!");
    }

    // PATCH parcial: só altera o que veio preenchido (title/description são NOT NULL no banco)
    if (demandChanged.getTitle() != null) {
      demand.setTitle(demandChanged.getTitle());
    }
    if (demandChanged.getDescription() != null) {
      demand.setDescription(demandChanged.getDescription());
    }
    if (demandChanged.getSuggestedValue() != null) {
      demand.setSuggestedValue(demandChanged.getSuggestedValue());
    }
    if (demandChanged.getSuggestedDate() != null) {
      demand.setSuggestedDate(demandChanged.getSuggestedDate());
    }

    if (demandChanged.getImgUrl() != null) {
      demand.setImgUrl(demandChanged.getImgUrl());
    }
    if (demandChanged.getAddressId() != null) {
      demand.setAddressId(demandChanged.getAddressId());
    }
    if (demandChanged.getCategoryId() != null) {
      demand.setCategoryId(demandChanged.getCategoryId());
    }
    if (demandChanged.getProfessionalId() != null) {
      demand.setProfessionalId(demandChanged.getProfessionalId());
    }

    repository.save(demand);
  }

  @Transactional
  public Demand reassign(@NonNull Long demandId, Long newProfessionalId) {
    Demand demand =
        repository
            .findById(demandId)
            .orElseThrow(() -> new NoSuchElementException("Demanda não encontrada"));

    // Permite reatribuição tanto de REJEITADO quanto de EXPIRADO
    if (demand.getDemandStatus() != DemandStatus.REJEITADO
        && demand.getDemandStatus() != DemandStatus.EXPIRADO) {
      throw new IllegalStateException(
          "Apenas demandas rejeitadas ou expiradas podem ser reatribuídas.");
    }

    User newProfessional = userService.getById(newProfessionalId);
    demand.setProfessionalId(newProfessional);
    demand.setDemandStatus(DemandStatus.ABERTO);
    demand.setOpenedAt(LocalDateTime.now()); // reinicia o contador de 1 hora

    Demand saved = repository.save(demand);

    // Notifica o novo profissional
    try {
      emailService.notificarNovaDemanda(saved);
    } catch (Exception e) {
      System.err.println("Erro ao notificar reatribuição: " + e.getMessage());
    }

    return saved;
  }

  @Transactional
  public Demand acceptWithValue(@NonNull Long demandId, Double finalValue) {
    Demand demand =
        repository
            .findById(demandId)
            .orElseThrow(() -> new NoSuchElementException("Demanda não encontrada."));

    if (demand.getDemandStatus() != DemandStatus.ABERTO) {
      throw new IllegalStateException(
          "Só é possível aceitar uma demanda que esteja com status ABERTO.");
    }

    // Se o profissional não informar um valor, usa o valor sugerido pelo cliente
    Double valorFinal = finalValue != null ? finalValue : demand.getSuggestedValue();
    if (valorFinal == null) {
      throw new IllegalStateException(
          "É necessário informar um valor final (a demanda não possui valor sugerido).");
    }

    demand.setFinalValue(valorFinal);
    demand.setDemandStatus(DemandStatus.AGUARDANDO_PAGAMENTO);
    Demand saved = repository.save(demand);

    try {
      emailService.notificarAguardandoPagamento(saved);
    } catch (Exception e) {
      System.err.println("Erro ao notificar aguardando pagamento: " + e.getMessage());
    }

    return saved;
  }

  /**
   * Chamado internamente pelo PaymentService quando um pagamento é aprovado. Não é exposto
   * diretamente ao cliente/profissional via controller — só o fluxo de pagamento pode levar a
   * demanda de AGUARDANDO_PAGAMENTO para AGUARDANDO.
   */
  @Transactional
  public Demand confirmarPagamento(@NonNull Long demandId) {
    Demand demand =
        repository
            .findById(demandId)
            .orElseThrow(() -> new NoSuchElementException("Demanda não encontrada."));

    if (demand.getDemandStatus() != DemandStatus.AGUARDANDO_PAGAMENTO) {
      throw new IllegalStateException(
          "Só é possível confirmar pagamento de uma demanda aguardando pagamento.");
    }

    demand.setDemandStatus(DemandStatus.AGUARDANDO);
    Demand saved = repository.save(demand);

    try {
      emailService.notificarDemandaAceita(saved);
      emailService.notificarPagamentoAprovadoProfissional(saved);
    } catch (Exception e) {
      System.err.println("Erro ao notificar pagamento aprovado: " + e.getMessage());
    }

    return saved;
  }

  @Transactional
  public Demand updateStatus(Long id, DemandStatus status) {
    Demand demand =
        repository
            .findById(id)
            .orElseThrow(() -> new NoSuchElementException("Demanda não encontrada."));

    DemandStatus currentStatus = demand.getDemandStatus();

    if (status == DemandStatus.ABERTO) {
      throw new IllegalStateException(
          "Não é permitido reabrir uma demanda por este endpoint. Use /reassign.");
    }

    switch (currentStatus) {
      case ABERTO:
        if (status != DemandStatus.REJEITADO) {
          throw new IllegalStateException("Transição inválida: ABERTO → " + status);
        }
        break;

      case AGUARDANDO_PAGAMENTO:
        throw new IllegalStateException(
            "O status de uma demanda aguardando pagamento só muda através do fluxo de "
                + "pagamento (endpoints /api/payments).");

      case AGUARDANDO:
        if (status != DemandStatus.FECHADO) {
          throw new IllegalStateException("Transição inválida: AGUARDANDO → " + status);
        }
        break;

      case FECHADO:
        throw new IllegalStateException("Não é possível alterar o status de uma demanda fechada.");

      case REJEITADO:
        throw new IllegalStateException(
            "Não é permitido alterar o status desta demanda por este endpoint. Use /reassign.");

      case EXPIRADO:
        throw new IllegalStateException(
            "Não é permitido alterar o status desta demanda por este endpoint. Use /reassign.");
    }

    demand.setDemandStatus(status);
    Demand saved = repository.save(demand);

    try {
      switch (status) {
        case REJEITADO -> emailService.notificarDemandaRejeitada(saved);
        case FECHADO -> emailService.notificarDemandaFechada(saved);
        default -> {}
      }
    } catch (Exception e) {
      System.err.println(
          "Erro ao enviar notificação de status [" + status + "]: " + e.getMessage());
    }

    return saved;
  }

  @Transactional
  public Demand removeImage(@NonNull Long id, @NonNull String nomeArquivo) {
    Demand demand =
        repository
            .findById(id)
            .orElseThrow(() -> new NoSuchElementException("Demanda não encontrada com ID: " + id));

    if (demand.getImgUrl() == null || !demand.getImgUrl().remove(nomeArquivo)) {
      throw new NoSuchElementException("Imagem não encontrada nesta demanda: " + nomeArquivo);
    }

    Util.apagarImagem(nomeArquivo);

    return repository.save(demand);
  }

  @Transactional
  public void delete(@NonNull Long id) {
    Demand demand =
        repository
            .findById(id)
            .orElseThrow(() -> new NoSuchElementException("Demanda não encontrada com ID: " + id));
    demand.setEnabled(Boolean.FALSE);
    repository.save(demand);
  }
}
