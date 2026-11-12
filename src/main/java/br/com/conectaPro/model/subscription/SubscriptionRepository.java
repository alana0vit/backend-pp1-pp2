package br.com.conectaPro.model.subscription;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

  Optional<Subscription> findFirstByProfessionalIdAndStatusOrderByExpiresAtDesc(
      Long professionalId, SubscriptionStatus status);

  List<Subscription> findByProfessionalIdAndStatus(Long professionalId, SubscriptionStatus status);

  List<Subscription> findByStatusAndExpiresAtBefore(
      SubscriptionStatus status, LocalDateTime moment);
}
