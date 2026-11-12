package br.com.conectaPro.Conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.conectaPro.dto.MessageResponseDTO;
import br.com.conectaPro.model.conversation.DemandMessage;
import br.com.conectaPro.model.conversation.DemandMessageRepository;
import br.com.conectaPro.model.conversation.DemandMessageService;
import br.com.conectaPro.model.demand.Demand;
import br.com.conectaPro.model.demand.DemandRepository;
import br.com.conectaPro.model.demand.DemandStatus;
import br.com.conectaPro.model.user.User;
import br.com.conectaPro.model.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class DemandMessageServiceTest {

  @Mock private DemandMessageRepository repository;
  @Mock private DemandRepository demandRepository;
  @Mock private UserRepository userRepository;
  @Mock private SimpMessagingTemplate messagingTemplate;
  @InjectMocks private DemandMessageService service;

  private User client;
  private User professional;
  private Demand demand;

  @BeforeEach
  void setUp() {
    client = new User();
    client.setId(1L);
    client.setName("Cliente");
    client.setEmail("cliente@teste.com");

    professional = new User();
    professional.setId(2L);
    professional.setName("Profissional");
    professional.setEmail("pro@teste.com");

    demand = new Demand();
    demand.setId(10L);
    demand.setClientId(client);
    demand.setProfessionalId(professional);
    demand.setDemandStatus(DemandStatus.AGUARDANDO);
  }

  @Test
  @DisplayName("Participante envia mensagem: salva, publica no tópico e notifica o outro lado")
  void enviaMensagem() {
    when(demandRepository.findById(10L)).thenReturn(Optional.of(demand));
    when(userRepository.findById(1L)).thenReturn(Optional.of(client));
    when(repository.save(any(DemandMessage.class)))
        .thenAnswer(
            inv -> {
              DemandMessage m = inv.getArgument(0);
              m.setId(99L);
              return m;
            });

    MessageResponseDTO dto = service.send(10L, 1L, "  Olá!  ");

    assertEquals("Olá!", dto.content());
    assertEquals(1L, dto.senderId());
    verify(messagingTemplate).convertAndSend(eq("/topic/demand.10"), any(MessageResponseDTO.class));
    verify(messagingTemplate)
        .convertAndSendToUser(
            eq("pro@teste.com"), eq("/queue/notifications"), any(MessageResponseDTO.class));
  }

  @Test
  @DisplayName("Chat bloqueado antes do pagamento")
  void bloqueadoAntesDoPagamento() {
    demand.setDemandStatus(DemandStatus.AGUARDANDO_PAGAMENTO);
    when(demandRepository.findById(10L)).thenReturn(Optional.of(demand));

    assertThrows(IllegalStateException.class, () -> service.send(10L, 1L, "oi"));
    verify(repository, never()).save(any());
  }

  @Test
  @DisplayName("Quem não participa da demanda não pode conversar")
  void estranhoNaoConversa() {
    when(demandRepository.findById(10L)).thenReturn(Optional.of(demand));

    assertThrows(AccessDeniedException.class, () -> service.send(10L, 77L, "oi"));
    assertThrows(AccessDeniedException.class, () -> service.history(10L, 77L));
    verify(repository, never()).save(any());
  }

  @Test
  @DisplayName("Mensagem vazia ou longa demais é recusada")
  void validaConteudo() {
    when(demandRepository.findById(10L)).thenReturn(Optional.of(demand));

    assertThrows(IllegalArgumentException.class, () -> service.send(10L, 1L, "   "));
    assertThrows(IllegalArgumentException.class, () -> service.send(10L, 1L, "x".repeat(1001)));
    verify(repository, never()).save(any());
  }
}
