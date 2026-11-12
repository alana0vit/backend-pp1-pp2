package br.com.conectaPro.model.conversation;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DemandMessageRepository extends JpaRepository<DemandMessage, Long> {

  /** Últimas mensagens da conversa (mais recentes primeiro; o serviço inverte a ordem). */
  @Query(
      "SELECT m FROM DemandMessage m JOIN FETCH m.sender "
          + "WHERE m.demand.id = :demandId ORDER BY m.id DESC")
  List<DemandMessage> findRecent(@Param("demandId") Long demandId, Pageable pageable);

  /** Não lidas por conversa: [demandId, quantidade]. */
  @Query(
      "SELECT m.demand.id, COUNT(m) FROM DemandMessage m "
          + "WHERE m.readAt IS NULL AND m.sender.id <> :userId "
          + "AND (m.demand.clientId.id = :userId OR m.demand.professionalId.id = :userId) "
          + "GROUP BY m.demand.id")
  List<Object[]> countUnreadByDemand(@Param("userId") Long userId);

  @Modifying
  @Query(
      "UPDATE DemandMessage m SET m.readAt = :now "
          + "WHERE m.demand.id = :demandId AND m.sender.id <> :userId AND m.readAt IS NULL")
  int markAsRead(
      @Param("demandId") Long demandId,
      @Param("userId") Long userId,
      @Param("now") LocalDateTime now);
}
