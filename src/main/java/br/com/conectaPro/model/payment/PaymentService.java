package br.com.conectaPro.model.payment;

import br.com.conectaPro.model.demand.Demand;
import br.com.conectaPro.model.demand.DemandService;
import br.com.conectaPro.model.demand.DemandStatus;
import br.com.conectaPro.model.payment.gateway.PaymentGateway;
import br.com.conectaPro.model.subscription.Subscription;
import br.com.conectaPro.model.subscription.SubscriptionPlan;
import br.com.conectaPro.model.subscription.SubscriptionService;
import br.com.conectaPro.model.subscription.SubscriptionStatus;
import br.com.conectaPro.security.EmailService;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

  @Autowired private PaymentRepository repository;

  @Autowired private DemandService demandService;

  @Autowired private SubscriptionService subscriptionService;

  @Autowired private PaymentGateway paymentGateway;

  @Autowired private EmailService emailService;

  @Value("${payment.platform.fee.percentage}")
  private Double defaultPlatformFeePercentage;

  /**
   * Cria uma nova cobrança para a demanda (deve estar em AGUARDANDO_PAGAMENTO) e retorna o Payment
   * já com a URL de checkout preenchida via {@link #getCheckoutUrl}. A taxa de plataforma usada é a
   * do plano ativo do profissional, se houver (ver {@link SubscriptionService}), senão a taxa
   * padrão configurada.
   */
  @Transactional
  public Payment criarCheckoutDemanda(@NonNull Long demandId) {
    Demand demand = demandService.getById(demandId);

    if (demand.getDemandStatus() != DemandStatus.AGUARDANDO_PAGAMENTO) {
      throw new IllegalStateException(
          "Só é possível gerar cobrança para uma demanda aguardando pagamento.");
    }

    if (demand.getFinalValue() == null) {
      throw new IllegalStateException("A demanda não possui um valor final definido.");
    }

    double amount = demand.getFinalValue();
    double feePercentage =
        subscriptionService.getPlatformFeePercentage(
            demand.getProfessionalId().getId(), defaultPlatformFeePercentage);
    double platformFeeAmount = arredondar(amount * feePercentage);
    double professionalAmount = arredondar(amount - platformFeeAmount);

    Payment payment =
        Payment.builder()
            .type(PaymentType.DEMANDA)
            .demand(demand)
            .amount(amount)
            .platformFeeAmount(platformFeeAmount)
            .professionalAmount(professionalAmount)
            .platformFeePercentage(feePercentage)
            .status(PaymentStatus.PENDENTE)
            .build();
    payment.setEnabled(Boolean.TRUE);

    return repository.save(payment);
  }

  /**
   * Cria a cobrança de uma assinatura de plano já iniciada (ver {@link
   * SubscriptionService#iniciarAssinatura}). Aqui não há taxa de plataforma — o valor integral vai
   * pro profissional assinante (a "taxa" nesse caso é o próprio valor do plano).
   */
  @Transactional
  public Payment criarCheckoutAssinatura(@NonNull Subscription subscription) {
    if (subscription.getStatus() != SubscriptionStatus.PENDENTE) {
      throw new IllegalStateException("Só é possível gerar cobrança para uma assinatura pendente.");
    }

    SubscriptionPlan plan = subscription.getPlan();
    double amount = plan.getPrice();

    Payment payment =
        Payment.builder()
            .type(PaymentType.ASSINATURA)
            .subscription(subscription)
            .amount(amount)
            .platformFeeAmount(0.0)
            .professionalAmount(amount)
            .platformFeePercentage(0.0)
            .status(PaymentStatus.PENDENTE)
            .build();
    payment.setEnabled(Boolean.TRUE);

    return repository.save(payment);
  }

  public String getCheckoutUrl(Payment payment) {
    return paymentGateway.criarCheckout(payment);
  }

  public Payment getById(@NonNull Long id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new NoSuchElementException("Pagamento não encontrado com ID: " + id));
  }

  /**
   * Aprova o pagamento (chamado pela tela/endpoint de simulação hoje; seria chamado pelo webhook de
   * um gateway real no futuro) e avança a demanda para AGUARDANDO, ou ativa a assinatura,
   * dependendo do {@link PaymentType}.
   */
  @Transactional
  public Payment aprovar(@NonNull Long paymentId) {
    Payment payment = getById(paymentId);

    if (payment.getStatus() != PaymentStatus.PENDENTE) {
      throw new IllegalStateException("Só é possível aprovar um pagamento PENDENTE.");
    }

    payment.setStatus(PaymentStatus.APROVADO);
    payment.setPaidAt(LocalDateTime.now());
    Payment saved = repository.save(payment);

    switch (payment.getType()) {
      case DEMANDA -> demandService.confirmarPagamento(payment.getDemand().getId());
      case ASSINATURA -> subscriptionService.ativar(payment.getSubscription().getId());
    }

    return saved;
  }

  @Transactional
  public Payment recusar(@NonNull Long paymentId) {
    Payment payment = getById(paymentId);

    if (payment.getStatus() != PaymentStatus.PENDENTE) {
      throw new IllegalStateException("Só é possível recusar um pagamento PENDENTE.");
    }

    payment.setStatus(PaymentStatus.RECUSADO);
    // A demanda/assinatura permanece pendente; dá pra gerar uma nova cobrança.
    Payment saved = repository.save(payment);

    if (payment.getType() == PaymentType.DEMANDA && payment.getDemand() != null) {
      try {
        emailService.notificarPagamentoRecusado(payment.getDemand());
      } catch (Exception e) {
        System.err.println("Erro ao notificar pagamento recusado: " + e.getMessage());
      }
    }
    return saved;
  }

  private double arredondar(double valor) {
    return Math.round(valor * 100.0) / 100.0;
  }
}
