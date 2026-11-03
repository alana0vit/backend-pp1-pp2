package br.com.conectaPro.model.subscription;

import br.com.conectaPro.model.user.User;
import br.com.conectaPro.model.user.UserRepository;
import br.com.conectaPro.model.user.UserType;
import br.com.conectaPro.security.EmailService;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

@Service
public class SubscriptionService {

  @Autowired private SubscriptionRepository repository;

  @Autowired private SubscriptionPlanRepository planRepository;

  @Autowired private UserRepository userRepository;

  @Autowired private EmailService emailService;

  public List<SubscriptionPlan> listarPlanos() {
    return planRepository.findAll();
  }

  /**
   * Inicia a contratação de um plano: cria a Subscription em PENDENTE. A cobrança em si é gerada
   * separadamente pelo PaymentService (mesma tela/fluxo de checkout usada pelas demandas).
   */
  @Transactional
  public Subscription iniciarAssinatura(@NonNull Long professionalId, @NonNull Long planId) {
    User professional =
        userRepository
            .findById(professionalId)
            .orElseThrow(() -> new NoSuchElementException("Usuário não encontrado."));

    if (professional.getUserType() != UserType.PROFESSIONAL) {
      throw new IllegalStateException("Só profissionais podem assinar um plano.");
    }

    SubscriptionPlan plan =
        planRepository
            .findById(planId)
            .orElseThrow(() -> new NoSuchElementException("Plano não encontrado."));

    Subscription subscription =
        Subscription.builder()
            .professional(professional)
            .plan(plan)
            .status(SubscriptionStatus.PENDENTE)
            .build();
    subscription.setEnabled(Boolean.TRUE);

    return repository.save(subscription);
  }

  /**
   * Chamado pelo PaymentService quando o pagamento da assinatura é aprovado. Ativa o período e
   * atualiza os campos denormalizados no User (usados pela busca e pelo selo de verificado).
   */
  @Transactional
  public Subscription ativar(@NonNull Long subscriptionId) {
    Subscription subscription =
        repository
            .findById(subscriptionId)
            .orElseThrow(() -> new NoSuchElementException("Assinatura não encontrada."));

    if (subscription.getStatus() != SubscriptionStatus.PENDENTE) {
      throw new IllegalStateException("Só é possível ativar uma assinatura PENDENTE.");
    }

    LocalDateTime agora = LocalDateTime.now();
    SubscriptionPlan plan = subscription.getPlan();

    subscription.setStatus(SubscriptionStatus.ATIVA);
    subscription.setStartedAt(agora);
    subscription.setExpiresAt(agora.plusDays(plan.getDurationDays()));
    Subscription saved = repository.save(subscription);

    User professional = subscription.getProfessional();
    professional.setVerified(Boolean.TRUE);
    professional.setPriorityWeight(plan.getPriorityWeight());
    professional.setActivePlanName(plan.getName());
    userRepository.save(professional);

    try {
      emailService.notificarAssinaturaAtivada(saved);
    } catch (Exception e) {
      System.err.println("Erro ao notificar assinatura ativada: " + e.getMessage());
    }

    return saved;
  }

  /** Cancela uma assinatura ativa antes do fim do período (o profissional perde os benefícios). */
  @Transactional
  public Subscription cancelar(@NonNull Long subscriptionId) {
    Subscription subscription =
        repository
            .findById(subscriptionId)
            .orElseThrow(() -> new NoSuchElementException("Assinatura não encontrada."));

    if (subscription.getStatus() != SubscriptionStatus.ATIVA) {
      throw new IllegalStateException("Só é possível cancelar uma assinatura ATIVA.");
    }

    subscription.setStatus(SubscriptionStatus.CANCELADA);
    Subscription saved = repository.save(subscription);
    rebaixarProfissional(subscription.getProfessional());

    return saved;
  }

  /** Roda periodicamente (ver {@link SubscriptionExpirationScheduler}) marcando ATIVA→EXPIRADA. */
  @Transactional
  public void expirarVencidas() {
    List<Subscription> vencidas =
        repository.findByStatusAndExpiresAtBefore(SubscriptionStatus.ATIVA, LocalDateTime.now());

    for (Subscription subscription : vencidas) {
      subscription.setStatus(SubscriptionStatus.EXPIRADA);
      repository.save(subscription);
      rebaixarProfissional(subscription.getProfessional());

      try {
        emailService.notificarAssinaturaExpirada(subscription);
      } catch (Exception e) {
        System.err.println("Erro ao notificar assinatura expirada: " + e.getMessage());
      }
    }
  }

  private void rebaixarProfissional(User professional) {
    professional.setVerified(Boolean.FALSE);
    professional.setPriorityWeight(0);
    professional.setActivePlanName(null);
    userRepository.save(professional);
  }

  /** Usado pelo PaymentService para calcular a taxa de plataforma correta nas demandas pagas. */
  public Double getPlatformFeePercentage(@NonNull Long professionalId, double defaultPercentage) {
    return repository
        .findFirstByProfessionalIdAndStatusOrderByExpiresAtDesc(
            professionalId, SubscriptionStatus.ATIVA)
        .map(Subscription::getPlan)
        .map(SubscriptionPlan::getPlatformFeePercentage)
        .orElse(defaultPercentage);
  }
}
