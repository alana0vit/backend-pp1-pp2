package br.com.conectaPro.Subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import br.com.conectaPro.model.subscription.Subscription;
import br.com.conectaPro.model.subscription.SubscriptionPlan;
import br.com.conectaPro.model.subscription.SubscriptionPlanRepository;
import br.com.conectaPro.model.subscription.SubscriptionRepository;
import br.com.conectaPro.model.subscription.SubscriptionService;
import br.com.conectaPro.model.subscription.SubscriptionStatus;
import br.com.conectaPro.model.user.User;
import br.com.conectaPro.model.user.UserRepository;
import br.com.conectaPro.model.user.UserType;
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
class SubscriptionServiceTest {

  @Mock private SubscriptionRepository repository;
  @Mock private SubscriptionPlanRepository planRepository;
  @Mock private UserRepository userRepository;
  @InjectMocks private SubscriptionService subscriptionService;

  @Test
  @DisplayName("Deve iniciar assinatura para um profissional")
  void deveIniciarAssinaturaComSucesso() {
    Long professionalId = 1L;
    Long planId = 2L;

    User professional = new User();
    professional.setId(professionalId);
    professional.setUserType(UserType.PROFESSIONAL);

    SubscriptionPlan plano = SubscriptionPlan.builder().name("Ouro").build();

    when(userRepository.findById(professionalId)).thenReturn(Optional.of(professional));
    when(planRepository.findById(planId)).thenReturn(Optional.of(plano));
    when(repository.save(any(Subscription.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Subscription subscription = subscriptionService.iniciarAssinatura(professionalId, planId);

    assertEquals(SubscriptionStatus.PENDENTE, subscription.getStatus());
    assertEquals(plano, subscription.getPlan());
  }

  @Test
  @DisplayName("Não deve permitir recomprar o mesmo plano ainda ativo")
  void erroAoRecomprarMesmoPlanoAtivo() {
    User professional = new User();
    professional.setId(1L);
    professional.setUserType(UserType.PROFESSIONAL);

    SubscriptionPlan plano = SubscriptionPlan.builder().name("Ouro").build();
    plano.setId(2L);

    Subscription ativa =
        Subscription.builder()
            .plan(plano)
            .status(SubscriptionStatus.ATIVA)
            .expiresAt(LocalDateTime.now().plusDays(10))
            .build();

    when(userRepository.findById(1L)).thenReturn(Optional.of(professional));
    when(planRepository.findById(2L)).thenReturn(Optional.of(plano));
    when(repository.findFirstByProfessionalIdAndStatusOrderByExpiresAtDesc(
            1L, SubscriptionStatus.ATIVA))
        .thenReturn(Optional.of(ativa));

    assertThrows(IllegalStateException.class, () -> subscriptionService.iniciarAssinatura(1L, 2L));
  }

  @Test
  @DisplayName("Não deve permitir cliente assinar um plano")
  void erroAoClienteTentarAssinar() {
    Long professionalId = 1L;

    User cliente = new User();
    cliente.setId(professionalId);
    cliente.setUserType(UserType.CLIENT);

    when(userRepository.findById(professionalId)).thenReturn(Optional.of(cliente));

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> subscriptionService.iniciarAssinatura(professionalId, 2L));

    assertEquals("Só profissionais podem assinar um plano.", exception.getMessage());
  }

  @Test
  @DisplayName("Deve ativar assinatura e atualizar os campos do profissional")
  void deveAtivarAssinatura() {
    Long subscriptionId = 3L;

    User professional = new User();
    professional.setId(1L);
    professional.setVerified(false);
    professional.setPriorityWeight(0);

    SubscriptionPlan plano =
        SubscriptionPlan.builder()
            .name("Ouro")
            .durationDays(30)
            .priorityWeight(3)
            .platformFeePercentage(0.07)
            .build();

    Subscription subscription =
        Subscription.builder()
            .professional(professional)
            .plan(plano)
            .status(SubscriptionStatus.PENDENTE)
            .build();
    subscription.setId(subscriptionId);

    when(repository.findById(subscriptionId)).thenReturn(Optional.of(subscription));
    when(repository.save(any(Subscription.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Subscription ativada = subscriptionService.ativar(subscriptionId);

    assertEquals(SubscriptionStatus.ATIVA, ativada.getStatus());
    assertEquals(Boolean.TRUE, professional.getVerified());
    assertEquals(3, professional.getPriorityWeight());
    assertEquals("Ouro", professional.getActivePlanName());
  }

  @Test
  @DisplayName("Deve expirar assinaturas vencidas e rebaixar o profissional")
  void deveExpirarAssinaturasVencidas() {
    User professional = new User();
    professional.setId(1L);
    professional.setVerified(true);
    professional.setPriorityWeight(2);
    professional.setActivePlanName("Prata");

    Subscription vencida =
        Subscription.builder()
            .professional(professional)
            .status(SubscriptionStatus.ATIVA)
            .expiresAt(LocalDateTime.now().minusDays(1))
            .build();

    when(repository.findByStatusAndExpiresAtBefore(
            org.mockito.ArgumentMatchers.eq(SubscriptionStatus.ATIVA), any(LocalDateTime.class)))
        .thenReturn(List.of(vencida));
    when(repository.save(any(Subscription.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    subscriptionService.expirarVencidas();

    assertEquals(SubscriptionStatus.EXPIRADA, vencida.getStatus());
    assertEquals(Boolean.FALSE, professional.getVerified());
    assertEquals(0, professional.getPriorityWeight());
    assertEquals(null, professional.getActivePlanName());
  }

  @Test
  @DisplayName("Deve retornar a taxa do plano ativo, ou a padrão se não houver assinatura")
  void deveRetornarTaxaCorreta() {
    Long professionalId = 1L;

    when(repository.findFirstByProfessionalIdAndStatusOrderByExpiresAtDesc(
            professionalId, SubscriptionStatus.ATIVA))
        .thenReturn(Optional.empty());

    Double taxa = subscriptionService.getPlatformFeePercentage(professionalId, 0.15);

    assertEquals(0.15, taxa);
  }

  @Test
  @DisplayName("Deve lançar exceção ao ativar assinatura inexistente")
  void erroAoAtivarAssinaturaInexistente() {
    Long subscriptionId = 99L;

    when(repository.findById(subscriptionId)).thenReturn(Optional.empty());

    assertThrows(
        NoSuchElementException.class, () -> subscriptionService.ativar(subscriptionId));
  }
}
