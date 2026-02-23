package com.ngxbot.execution.service;

import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.MimeMultipart;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads OTP codes from Gmail via IMAP.
 * Trove sends a 6-digit OTP to the user's email on every login.
 *
 * Requires a Google App Password (not the regular Gmail password).
 * To set up: Google Account → Security → 2-Step Verification → App Passwords.
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "otp.email.enabled", havingValue = "true")
public class EmailOtpReader {

    @Value("${otp.email.imap-host:imap.gmail.com}")
    private String imapHost;

    @Value("${otp.email.imap-port:993}")
    private int imapPort;

    @Value("${otp.email.username}")
    private String emailUsername;

    @Value("${otp.email.password}")
    private String emailPassword;

    @Value("${otp.email.sender-filter:trove}")
    private String senderFilter;

    @Value("${otp.email.poll-interval-seconds:5}")
    private int pollIntervalSeconds;

    @Value("${otp.email.max-wait-seconds:90}")
    private int maxWaitSeconds;

    /** Pattern to match a 6-digit OTP code. */
    private static final Pattern OTP_PATTERN = Pattern.compile("\\b(\\d{6})\\b");

    /**
     * Polls Gmail IMAP for a recent OTP email from Trove.
     * Waits up to maxWaitSeconds, polling every pollIntervalSeconds.
     *
     * @return the 6-digit OTP code
     * @throws Exception if OTP cannot be read within the timeout
     */
    public String readOtpFromEmail() throws Exception {
        log.info("[EMAIL-OTP] Polling Gmail for OTP (max {}s, every {}s, sender filter='{}')",
                maxWaitSeconds, pollIntervalSeconds, senderFilter);

        Instant startTime = Instant.now();
        Instant deadline = startTime.plusSeconds(maxWaitSeconds);

        // Wait a few seconds for email delivery before first poll
        Thread.sleep(3000);

        while (Instant.now().isBefore(deadline)) {
            try {
                String otp = pollForOtp();
                if (otp != null) {
                    log.info("[EMAIL-OTP] OTP found: {}", otp.replaceAll(".", "*"));
                    return otp;
                }
            } catch (Exception e) {
                log.warn("[EMAIL-OTP] Poll attempt failed: {}", e.getMessage());
            }

            long remainingMs = deadline.toEpochMilli() - Instant.now().toEpochMilli();
            if (remainingMs > 0) {
                long sleepMs = Math.min(pollIntervalSeconds * 1000L, remainingMs);
                Thread.sleep(sleepMs);
            }
        }

        throw new Exception("OTP not found in email within " + maxWaitSeconds + " seconds");
    }

    /**
     * Connects to Gmail IMAP, reads the latest messages, and extracts OTP.
     * Returns null if no OTP found in recent messages.
     */
    private String pollForOtp() throws MessagingException {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", imapHost);
        props.put("mail.imaps.port", String.valueOf(imapPort));
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.connectiontimeout", "10000");
        props.put("mail.imaps.timeout", "10000");

        Session session = Session.getInstance(props);
        Store store = null;
        Folder inbox = null;

        try {
            store = session.getStore("imaps");
            store.connect(imapHost, imapPort, emailUsername, emailPassword);

            inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);

            int messageCount = inbox.getMessageCount();
            if (messageCount == 0) {
                log.debug("[EMAIL-OTP] Inbox is empty");
                return null;
            }

            // Check the last 5 messages (most recent first)
            int start = Math.max(1, messageCount - 4);
            Message[] messages = inbox.getMessages(start, messageCount);

            for (int i = messages.length - 1; i >= 0; i--) {
                Message msg = messages[i];

                // Only look at messages from the last 5 minutes
                if (msg.getReceivedDate() != null) {
                    long ageMs = System.currentTimeMillis() - msg.getReceivedDate().getTime();
                    if (ageMs > 5 * 60 * 1000) {
                        log.debug("[EMAIL-OTP] Skipping old message ({}min old): {}",
                                ageMs / 60000, msg.getSubject());
                        continue;
                    }
                }

                // Check if the sender or subject matches Trove
                String from = msg.getFrom() != null && msg.getFrom().length > 0
                        ? msg.getFrom()[0].toString().toLowerCase() : "";
                String subject = msg.getSubject() != null ? msg.getSubject().toLowerCase() : "";

                if (!from.contains(senderFilter.toLowerCase())
                        && !subject.contains("otp")
                        && !subject.contains("verification")
                        && !subject.contains("code")) {
                    log.debug("[EMAIL-OTP] Skipping non-OTP message from '{}': '{}'", from, subject);
                    continue;
                }

                log.debug("[EMAIL-OTP] Checking message from '{}': '{}'", from, subject);

                // Extract body text
                String body = extractTextContent(msg);
                if (body == null) continue;

                // Find 6-digit OTP
                Matcher matcher = OTP_PATTERN.matcher(body);
                if (matcher.find()) {
                    String otp = matcher.group(1);
                    log.info("[EMAIL-OTP] Found 6-digit code in email '{}' from '{}'", subject, from);
                    return otp;
                }
            }

            log.debug("[EMAIL-OTP] No OTP found in recent messages");
            return null;

        } finally {
            if (inbox != null && inbox.isOpen()) {
                try { inbox.close(false); } catch (Exception ignored) {}
            }
            if (store != null && store.isConnected()) {
                try { store.close(); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Extracts plain text content from a message (handles multipart).
     */
    private String extractTextContent(Message message) {
        try {
            Object content = message.getContent();
            if (content instanceof String) {
                return (String) content;
            }
            if (content instanceof MimeMultipart multipart) {
                StringBuilder text = new StringBuilder();
                for (int i = 0; i < multipart.getCount(); i++) {
                    var part = multipart.getBodyPart(i);
                    if (part.isMimeType("text/plain")) {
                        text.append(part.getContent().toString());
                    } else if (part.isMimeType("text/html")) {
                        // Strip HTML tags as fallback
                        String html = part.getContent().toString();
                        text.append(html.replaceAll("<[^>]+>", " "));
                    }
                }
                return text.toString();
            }
        } catch (Exception e) {
            log.debug("[EMAIL-OTP] Could not extract text from message: {}", e.getMessage());
        }
        return null;
    }
}
