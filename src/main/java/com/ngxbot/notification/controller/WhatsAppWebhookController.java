package com.ngxbot.notification.controller;

import com.ngxbot.notification.service.TradeApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Receives incoming WhatsApp messages from WAHA webhooks and routes them
 * to the trade-approval workflow.
 * <p>
 * WAHA sends a POST to the configured webhook URL when a message is received.
 * The payload structure varies but typically contains:
 * <pre>
 * {
 *   "event": "message",
 *   "payload": {
 *     "from": "234xxx@c.us",
 *     "body": "YES"
 *   }
 * }
 * </pre>
 * or the simpler format:
 * <pre>
 * {
 *   "body": { "message": { "body": "YES" } },
 *   "text": "YES"
 * }
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/webhooks/whatsapp")
@RequiredArgsConstructor
public class WhatsAppWebhookController {

    private final TradeApprovalService tradeApprovalService;

    /**
     * Handles incoming WhatsApp messages from WAHA.
     *
     * @param payload the raw webhook payload
     * @return 200 OK
     */
    @PostMapping("/message")
    public ResponseEntity<Void> handleIncomingMessage(@RequestBody Map<String, Object> payload) {
        log.debug("WhatsApp webhook received: {}", payload);

        String messageText = extractMessageText(payload);

        if (messageText == null || messageText.isBlank()) {
            log.debug("No text content in webhook payload — ignoring");
            return ResponseEntity.ok().build();
        }

        log.info("WhatsApp incoming message: '{}'", messageText);

        // Check all pending approvals — route the reply to the first one found.
        // In practice there should be at most one pending at a time.
        String trimmed = messageText.trim().toUpperCase();
        if ("YES".equals(trimmed) || "NO".equals(trimmed)) {
            routeApprovalReply(trimmed);
        } else {
            log.debug("Message '{}' does not match an approval command — ignoring", messageText);
        }

        return ResponseEntity.ok().build();
    }

    /**
     * Extracts the message text from the WAHA webhook payload.
     * Supports multiple payload formats:
     * <ul>
     *   <li>{@code payload.body.message.body} (nested WAHA v2)</li>
     *   <li>{@code payload.payload.body} (WAHA event format)</li>
     *   <li>{@code payload.text} (flat format)</li>
     * </ul>
     *
     * @param payload the raw webhook payload map
     * @return extracted message text, or null
     */
    @SuppressWarnings("unchecked")
    private String extractMessageText(Map<String, Object> payload) {
        // Try: payload -> body -> message -> body
        try {
            Object bodyObj = payload.get("body");
            if (bodyObj instanceof Map<?, ?> bodyMap) {
                Object messageObj = bodyMap.get("message");
                if (messageObj instanceof Map<?, ?> messageMap) {
                    Object text = messageMap.get("body");
                    if (text instanceof String s && !s.isBlank()) {
                        return s;
                    }
                }
            }
        } catch (Exception ignored) {
            // fall through to next strategy
        }

        // Try: payload -> payload -> body (WAHA event format)
        try {
            Object payloadObj = payload.get("payload");
            if (payloadObj instanceof Map<?, ?> payloadMap) {
                Object text = payloadMap.get("body");
                if (text instanceof String s && !s.isBlank()) {
                    return s;
                }
            }
        } catch (Exception ignored) {
            // fall through
        }

        // Try: payload -> text (flat format)
        Object text = payload.get("text");
        if (text instanceof String s && !s.isBlank()) {
            return s;
        }

        return null;
    }

    /**
     * Routes an approval reply (YES/NO) to all pending approvals.
     * In practice there should be at most one pending approval at a time.
     *
     * @param reply "YES" or "NO"
     */
    private void routeApprovalReply(String reply) {
        if (!tradeApprovalService.hasAnyPendingApproval()) {
            log.debug("No pending approvals — ignoring reply '{}'", reply);
            return;
        }

        log.info("Routing approval reply '{}' to pending approval(s)", reply);
        tradeApprovalService.processAnyPendingReply(reply);
    }
}
