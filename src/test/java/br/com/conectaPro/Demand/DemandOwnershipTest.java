package br.com.conectaPro.Demand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.conectaPro.model.demand.Demand;
import br.com.conectaPro.model.demand.DemandRepository;
import br.com.conectaPro.model.demand.DemandService;
import br.com.conectaPro.model.demand.DemandStatus;
import br.com.conectaPro.model.user.User;
import br.com.conectaPro.model.user.UserService;
import br.com.conectaPro.security.EmailService;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DemandOwnershipTest {

  @Mock private DemandRepository demandRepository;
  @Mock private UserService userService;
  @Mock private EmailService emailService;
  @InjectMocks private DemandService demandService;

  private Demand demand;

  @BeforeEach
  void setUp() {
    User client = new User();
    client.setId(1L);
    User professional = new User();
    professional.setId(2L);

    demand = new Demand();
    demand.setId(10L);
    demand.setClientId(client);
    demand.setProfessionalId(professional);
    demand.setDemandStatus(DemandStatus.ABERTO);
    demand.setImgUrl(new ArrayList<>(List.of("a.jpg", "b.jpg", "c.jpg")));
    when(demandRepository.findById(10L)).thenReturn(Optional.of(demand));
  }

  @Test
  @DisplayName("Quem não é o dono recebe 404 ao editar, apagar ou remover foto")
  void naoDonoRecebe404() {
    assertThrows(NoSuchElementException.class, () -> demandService.getByIdForOwner(10L, 99L));
    assertThrows(NoSuchElementException.class, () -> demandService.deleteForOwner(10L, 99L));
    assertThrows(
        NoSuchElementException.class, () -> demandService.removeImageForOwner(10L, 99L, "a.jpg"));
    // nem o profissional da demanda é "dono"
    assertThrows(NoSuchElementException.class, () -> demandService.deleteForOwner(10L, 2L));
    verify(demandRepository, never()).save(any());
  }

  @Test
  @DisplayName("Foto só pode ser removida enquanto a demanda está ABERTA")
  void naoRemoveFotoDeDemandaFechada() {
    demand.setDemandStatus(DemandStatus.FECHADO);

    assertThrows(
        IllegalStateException.class, () -> demandService.removeImageForOwner(10L, 1L, "a.jpg"));
    assertEquals(3, demand.getImgUrl().size());
  }

  @Test
  @DisplayName("Novas fotos são somadas às existentes (B-05)")
  void somaFotos() {
    when(demandRepository.save(any(Demand.class))).thenAnswer(inv -> inv.getArgument(0));

    demandService.updateForOwner(10L, 1L, new Demand(), List.of("d.jpg"));

    assertEquals(List.of("a.jpg", "b.jpg", "c.jpg", "d.jpg"), demand.getImgUrl());
  }

  @Test
  @DisplayName("Editar sem enviar fotos não mexe nas existentes")
  void semFotosNaoAltera() {
    when(demandRepository.save(any(Demand.class))).thenAnswer(inv -> inv.getArgument(0));

    Demand mudanca = new Demand();
    mudanca.setTitle("Novo título");
    demandService.updateForOwner(10L, 1L, mudanca, List.of());

    assertEquals("Novo título", demand.getTitle());
    assertEquals(3, demand.getImgUrl().size());
  }

  @Test
  @DisplayName("Só o profissional da demanda aceita/recusa/finaliza (404 para os demais)")
  void soProfissionalAge() {
    assertEquals(10L, demandService.getByIdForProfessional(10L, 2L).getId());
    assertThrows(NoSuchElementException.class, () -> demandService.getByIdForProfessional(10L, 1L));
    assertThrows(NoSuchElementException.class, () -> demandService.getByIdForProfessional(10L, 99L));
  }

  @Test
  @DisplayName("Respeita o teto de fotos por demanda")
  void respeitaTeto() {
    assertThrows(
        IllegalArgumentException.class, () -> demandService.assertCanAddImages(10L, 1L, 3));
    demandService.assertCanAddImages(10L, 1L, 2); // 3 + 2 = 5: ok
  }
}
