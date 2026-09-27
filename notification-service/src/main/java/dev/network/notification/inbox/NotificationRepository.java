package dev.network.notification.inbox;

import jakarta.persistence.LockModeType;

import java.util.*;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface NotificationRepository extends JpaRepository<Notification, String> {
    List<Notification> findByRecipientIdOrderByOccurredAtDescIdDesc(String recipient, Pageable page);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from Notification n where n.id=:id and n.recipientId=:recipient")
    Optional<Notification> owned(String id, String recipient);

    long countByRecipientIdAndReadAtIsNull(String recipient);

    long countByEventId(String eventId);
}
