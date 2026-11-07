package br.com.conectaPro.api.subscription;

import br.com.conectaPro.model.payment.Payment;
import br.com.conectaPro.model.payment.PaymentService;
import br.com.conectaPro.model.subscription.Subscription;
import br.com.conectaPro.model.subscription.SubscriptionPlan;
import br.com.conectaPro.model.subscription.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/subscriptions")
@CrossOrigin
@Tag(
    name = "Subscriptions",
    description = "Planos de assinatura/verificação do profissional (Bronze/Prata/Ouro)")
public class SubscriptionController {

  @Autowired private SubscriptionService subscriptionService;

  @Autowired private PaymentService paymentService;

  @Operation(summary = "Lista os planos disponíveis")
  @GetMapping("/plans")
  public ResponseEntity<List<SubscriptionPlan>> listarPlanos() {
    return ResponseEntity.ok(subscriptionService.listarPlanos());
  }

  @Operation(
      summary = "Profissional contrata um plano",
      description =
          "Cria a assinatura (PENDENTE) e já gera a cobrança, retornando o link de checkout — "
              + "mesmo fluxo usado para pagar uma demanda.")
  @PostMapping("/professional/{professionalId}/plan/{planId}/checkout")
  public ResponseEntity<?> assinar(@PathVariable Long professionalId, @PathVariable Long planId) {
    try {
      Subscription subscription = subscriptionService.iniciarAssinatura(professionalId, planId);
      Payment payment = paymentService.criarCheckoutAssinatura(subscription);
      String checkoutUrl = paymentService.getCheckoutUrl(payment);

      return ResponseEntity.ok(
          Map.of(
              "subscription", subscription,
              "payment", payment,
              "checkoutUrl", checkoutUrl));
    } catch (NoSuchElementException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    } catch (IllegalStateException e) {
      return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }
  }

  @Operation(
      summary = "Plano atual do profissional",
      description =
          "200 com {subscriptionId, planId, planName, status, startedAt, expiresAt} ou 204.")
  @GetMapping("/professional/{professionalId}/current")
  public ResponseEntity<?> planoAtual(@PathVariable Long professionalId) {
    return subscriptionService
        .obterAssinaturaAtiva(professionalId)
        .<ResponseEntity<?>>map(
            s ->
                ResponseEntity.ok(
                    Map.of(
                        "subscriptionId", s.getId(),
                        "planId", s.getPlan().getId(),
                        "planName", s.getPlan().getName(),
                        "status", s.getStatus(),
                        "startedAt", String.valueOf(s.getStartedAt()),
                        "expiresAt", String.valueOf(s.getExpiresAt()))))
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  @Operation(summary = "Cancela uma assinatura ativa")
  @PostMapping("/{subscriptionId}/cancelar")
  public ResponseEntity<?> cancelar(@PathVariable Long subscriptionId) {
    try {
      return ResponseEntity.ok(subscriptionService.cancelar(subscriptionId));
    } catch (NoSuchElementException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    } catch (IllegalStateException e) {
      return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }
  }
}
