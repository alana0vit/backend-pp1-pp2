package br.com.conectaPro.model.conversation;

import br.com.conectaPro.dto.MessageResponseDTO;
import br.com.conectaPro.model.demand.Demand;
import br.com.conectaPro.model.demand.DemandRepository;
import br.com.conectaPro.model.demand.DemandStatus;
import br.com.conectaPro.model.user.User;
import br.com.conectaPro.model.user.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.lang.NonNull;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras do chat: só cliente e profissional da demanda conversam, e somente depois do pagamento
 * (demanda AGUARDANDO ou FECHADO — mesmo critério que libera o contato).
 */
@Service
@RequiredArgsConstructor
public class DemandMessageService {

  public static final int MAX_LENGTH = 1000;
  public static final int HISTORY_LIMIT = 200;
  private static final Set<DemandStatus> CHAT_ENABLED =
      Set.of(DemandStatus.AGUARDANDO, DemandStatus.FECHADO);

  public static String topicOf(Long demandId) {
    return "/topic/demand." + demandId;
  }

  private final DemandMessageRepository repository;
  private final DemandRepository demandRepository;
  private final UserRepository userRepository;
  private final SimpMessagingTemplate messagingTemplate;

  /** Garante que o usuário participa da demanda e que o chat já está liberado. */
  @Transactional(readOnly = true)
  public Demand assertCanChat(@NonNull Long demandId, @NonNull Long userId) {
    Demand demand =
        demandRepository
            .findById(demandId)
            .orElseThrow(() -> new NoSuchElementException("Demanda não encontrada."));

    if (!isParticipant(demand, userId)) {
      throw new AccessDeniedException("Você não participa desta conversa.");
    }
    if (!CHAT_ENABLED.contains(demand.getDemandStatus())) {
      throw new IllegalStateException("O chat é liberado somente após o pagamento do serviço.");
    }
    return demand;
  }

  private boolean isParticipant(Demand demand, Long userId) {
    return (demand.getClientId() != null && userId.equals(demand.getClientId().getId()))
        || (demand.getProfessionalId() != null
            && userId.equals(demand.getProfessionalId().getId()));
  }

  @Transactional
  public MessageResponseDTO send(@NonNull Long demandId, @NonNull Long senderId, String content) {
    Demand demand = assertCanChat(demandId, senderId);

    String text = content == null ? "" : content.trim();
    if (text.isEmpty()) {
      throw new IllegalArgumentException("A mensagem não pode ficar vazia.");
    }
    if (text.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "A mensagem deve ter no máximo " + MAX_LENGTH + " caracteres.");
    }

    User sender =
        userRepository
            .findById(senderId)
            .orElseThrow(() -> new NoSuchElementException("Usuário não encontrado."));

    DemandMessage message =
        DemandMessage.builder()
            .demand(demand)
            .sender(sender)
            .content(text)
            .sentAt(LocalDateTime.now())
            .build();
    message.setEnabled(Boolean.TRUE);

    MessageResponseDTO dto = MessageResponseDTO.fromEntity(repository.save(message));

    // Entrega ao vivo: quem está na conversa e o outro lado (badge de não lidas).
    messagingTemplate.convertAndSend(topicOf(demandId), dto);
    User recipient =
        senderId.equals(demand.getClientId().getId())
            ? demand.getProfessionalId()
            : demand.getClientId();
    if (recipient != null && recipient.getEmail() != null) {
      messagingTemplate.convertAndSendToUser(recipient.getEmail(), "/queue/notifications", dto);
    }
    return dto;
  }

  /** Histórico em ordem cronológica (as últimas {@value #HISTORY_LIMIT} mensagens). */
  @Transactional(readOnly = true)
  public List<MessageResponseDTO> history(@NonNull Long demandId, @NonNull Long userId) {
    assertCanChat(demandId, userId);
    List<DemandMessage> recent =
        new ArrayList<>(repository.findRecent(demandId, PageRequest.of(0, HISTORY_LIMIT)));
    Collections.reverse(recent);
    return recent.stream().map(MessageResponseDTO::fromEntity).toList();
  }

  @Transactional
  public int markAsRead(@NonNull Long demandId, @NonNull Long userId) {
    assertCanChat(demandId, userId);
    return repository.markAsRead(demandId, userId, LocalDateTime.now());
  }

  /** Mapa demandId → quantidade de mensagens não lidas do usuário. */
  @Transactional(readOnly = true)
  public Map<Long, Long> unreadByDemand(@NonNull Long userId) {
    Map<Long, Long> result = new HashMap<>();
    for (Object[] row : repository.countUnreadByDemand(userId)) {
      result.put((Long) row[0], (Long) row[1]);
    }
    return result;
  }
}
