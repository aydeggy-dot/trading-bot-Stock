package com.ngxbot.notification.repository;

import com.ngxbot.notification.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for {@link NotificationLog} entities.
 */
@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    /**
     * Finds notification logs by channel within a date range.
     * Uses the {@code created_at} column as the "sent at" timestamp.
     *
     * @param channel "WHATSAPP" or "TELEGRAM"
     * @param from    start of the range (inclusive)
     * @param to      end of the range (inclusive)
     * @return list of matching notification logs
     */
    List<NotificationLog> findByChannelAndCreatedAtBetween(String channel,
                                                           LocalDateTime from,
                                                           LocalDateTime to);

    /**
     * Finds notification logs by delivery status.
     *
     * @param sentSuccessfully true for successfully sent, false for failed
     * @return list of matching notification logs
     */
    List<NotificationLog> findBySentSuccessfully(Boolean sentSuccessfully);
}
