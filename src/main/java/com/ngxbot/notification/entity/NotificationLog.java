package com.ngxbot.notification.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Persistent log of all notifications sent by the bot.
 * Maps to the {@code notification_log} table (Flyway V10).
 */
@Entity
@Table(name = "notification_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Event type / message category (e.g. "TRADE_ALERT", "CIRCUIT_BREAKER", "APPROVAL").
     */
    @Column(name = "event_type", nullable = false, length = 50)
    private String messageType;

    /**
     * Notification channel: "WHATSAPP" or "TELEGRAM".
     */
    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    /**
     * Recipient identifier (chat ID, phone number, etc.).
     */
    @Column(name = "recipient", length = 50)
    private String recipient;

    /**
     * Full message text or a summary for long messages.
     */
    @Column(name = "message_text", columnDefinition = "TEXT")
    private String messageSummary;

    /**
     * Whether the message included an image attachment.
     */
    @Column(name = "has_image")
    @Builder.Default
    private Boolean hasImage = false;

    /**
     * File path to the image attachment, if any.
     */
    @Column(name = "image_path", length = 255)
    private String imagePath;

    /**
     * Delivery status: true = sent successfully, false = failed.
     * Corresponds to logical status "SENT" / "FAILED".
     */
    @Column(name = "sent_successfully")
    @Builder.Default
    private Boolean sentSuccessfully = false;

    /**
     * Error details if delivery failed.
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Related trade order ID for linking notifications to orders.
     */
    @Column(name = "related_order_id", length = 50)
    private String relatedOrderId;

    /**
     * Timestamp when the notification was created / sent.
     */
    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Returns the logical status string ("SENT" or "FAILED") derived from the
     * {@code sentSuccessfully} flag.
     */
    @Transient
    public String getStatus() {
        return Boolean.TRUE.equals(sentSuccessfully) ? "SENT" : "FAILED";
    }

    /**
     * Alias for {@link #createdAt} — the time the notification was sent or attempted.
     */
    @Transient
    public LocalDateTime getSentAt() {
        return createdAt;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
