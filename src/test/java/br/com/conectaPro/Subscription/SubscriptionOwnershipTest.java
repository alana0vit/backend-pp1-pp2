package br.com.conectaPro.Subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.conectaPro.model.subscription.Subscription;
import br.com.conectaPro.model.subscription.SubscriptionPlan;
import br.com.conectaPro.model.subscription.SubscriptionPlanRepository;
import br.com.conectaPro.model.subscription.SubscriptionRepository;
import br.com.conectaPro.model.subscription.SubscriptionService;
import br.com.conectaPro.model.subscription.SubscriptionStatus;
import br.com.conectaPro.model.user.User;
import br.com.conectaPro.model.user.UserRepository;
import br.com.conectaPro.security.EmailService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubscriptionOwnershipTest {

  @Mock private SubscriptionRepository repository;
  @Mock private SubscriptionPlanRepository planRepository;
  @Mock private UserRepository userRepository;
  @Mock private EmailService emailService;
  @InjectMocks private SubscriptionService subscriptionService;

  private Subscription assinatura(long id, User profissional, SubscriptionStatus status) {
    SubscriptionPlan plano =
        SubscriptionPlan.builder().name("Bronze").durationDays(30).priorityWeight(1).build();
    Subscription s =
        Subscription.builder().professional(profissional).plan(plano).status(status).build();
    s.setId(id);
    return s;
  }

  private User profissional(long id) {
    User u = new User();
    u.setId(id);
    return u;
  }

  @Test
  @DisplayName("Só o próprio profissional cancela a assinatura (B-03)")
  void naoDonoNaoCancela() {
    Subscription s = assinatura(5L, profissional(1L), SubscriptionStatus.ATIVA);
    when(repository.findById(5L)).thenReturn(Optional.of(s));

    assertThrows(NoSuchElementException.class, () -> subscriptionService.cancelar(5L, 99L));
    assertEquals(SubscriptionStatus.ATIVA, s.getStatus());
    verify(repository, never()).save(any());
  }

  @Test
  @DisplayName("O dono cancela a própria assinatura")
  void donoCancela() {
    Subscription s = assinatura(5L, profissional(1L), SubscriptionStatus.ATIVA);
    when(repository.findById(5L)).thenReturn(Optional.of(s));
    when(repository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));

    assertEquals(SubscriptionStatus.CANCELADA, subscriptionService.cancelar(5L, 1L).getStatus());
  }

  @Test
  @DisplayName("Ao ativar um plano novo, o anterior é encerrado (B-06)")
  void trocaDePlanoEncerraAnterior() {
    User pro = profissional(1L);
    Subscription anterior = assinatura(4L, pro, SubscriptionStatus.ATIVA);
    anterior.setExpiresAt(LocalDateTime.now().plusDays(10));
    Subscription nova = assinatura(5L, pro, SubscriptionStatus.PENDENTE);

    when(repository.findById(5L)).thenReturn(Optional.of(nova));
    when(repository.findByProfessionalIdAndStatus(1L, SubscriptionStatus.ATIVA))
        .thenReturn(List.of(anterior));
    when(repository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));

    Subscription ativada = subscriptionService.ativar(5L);

    assertEquals(SubscriptionStatus.ATIVA, ativada.getStatus());
    assertEquals(SubscriptionStatus.CANCELADA, anterior.getStatus());
  }
}
