package com.ngxbot.notification;

import com.ngxbot.config.NotificationProperties;
import com.ngxbot.notification.service.MessageFormatter;
import com.ngxbot.notification.service.NotificationRouter;
import com.ngxbot.notification.service.TradeApprovalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradeApprovalServiceTest {

    @Mock private NotificationRouter notificationRouter;
    @Mock private MessageFormatter messageFormatter;
    @Mock private NotificationProperties notificationProperties;
    @Mock private NotificationProperties.Approval approvalConfig;

    private TradeApprovalService tradeApprovalService;

    private static final String APPROVAL_ID = "approval-001";
    private static final String TEST_MESSAGE = "test approval message";

    @BeforeEach
    void setUp() {
        when(notificationProperties.getApproval()).thenReturn(approvalConfig);
        when(approvalConfig.getTimeoutMinutes()).thenReturn(1);
        when(approvalConfig.getDefaultOnTimeout()).thenReturn("REJECT");
        when(messageFormatter.formatApprovalRequest(
                anyString(), anyString(), anyInt(),
                any(BigDecimal.class), anyString(),
                any(BigDecimal.class), anyInt()
        )).thenReturn(TEST_MESSAGE);
        doNothing().when(notificationRouter).sendWhatsAppOnly(anyString());

        tradeApprovalService = new TradeApprovalService(
                notificationRouter, messageFormatter, notificationProperties
        );
    }

    // ---- requestApproval sends WhatsApp message ----

    @Test
    @DisplayName("requestApproval sends WhatsApp message and returns true on YES reply")
    void requestApproval_sendsWhatsAppMessage()
            throws InterruptedException, ExecutionException, TimeoutException {

        // requestApproval blocks, so run it in a separate thread
        CompletableFuture<Boolean> approvalFuture = CompletableFuture.supplyAsync(() ->
                tradeApprovalService.requestApproval(
                        APPROVAL_ID, "BUY", "ZENITHBANK", 500,
                        new BigDecimal("35.50"), "NGN",
                        new BigDecimal("0.015")
                )
        );

        // Give the async thread time to register the pending approval and send the message
        Thread.sleep(200);

        // Verify the WhatsApp message was sent
        verify(notificationRouter).sendWhatsAppOnly(TEST_MESSAGE);

        // Simulate user replying YES
        tradeApprovalService.processReply(APPROVAL_ID, "YES");

        // Verify the approval returned true
        Boolean result = approvalFuture.get(5, TimeUnit.SECONDS);
        assertThat(result).isTrue();
    }

    // ---- processReply YES approves ----

    @Test
    @DisplayName("processReply with YES completes the approval future with true")
    void processReply_yes_approvesRequest()
            throws InterruptedException, ExecutionException, TimeoutException {

        CompletableFuture<Boolean> approvalFuture = CompletableFuture.supplyAsync(() ->
                tradeApprovalService.requestApproval(
                        APPROVAL_ID, "BUY", "GTCO", 200,
                        new BigDecimal("42.00"), "NGN",
                        new BigDecimal("0.018")
                )
        );

        Thread.sleep(200);

        tradeApprovalService.processReply(APPROVAL_ID, "YES");

        Boolean result = approvalFuture.get(5, TimeUnit.SECONDS);
        assertThat(result).isTrue();
    }

    // ---- processReply NO rejects ----

    @Test
    @DisplayName("processReply with NO completes the approval future with false")
    void processReply_no_rejectsRequest()
            throws InterruptedException, ExecutionException, TimeoutException {

        CompletableFuture<Boolean> approvalFuture = CompletableFuture.supplyAsync(() ->
                tradeApprovalService.requestApproval(
                        APPROVAL_ID, "SELL", "DANGCEM", 100,
                        new BigDecimal("280.00"), "NGN",
                        new BigDecimal("0.012")
                )
        );

        Thread.sleep(200);

        tradeApprovalService.processReply(APPROVAL_ID, "NO");

        Boolean result = approvalFuture.get(5, TimeUnit.SECONDS);
        assertThat(result).isFalse();
    }

    // ---- hasPendingApproval ----

    @Test
    @DisplayName("hasPendingApproval returns true when approval is pending")
    void hasPendingApproval_returnsTrueWhenPending() throws InterruptedException {

        // Start approval in a background thread (it will block waiting for reply)
        CompletableFuture.supplyAsync(() ->
                tradeApprovalService.requestApproval(
                        APPROVAL_ID, "BUY", "ACCESSCORP", 300,
                        new BigDecimal("18.50"), "NGN",
                        new BigDecimal("0.010")
                )
        );

        // Give the async thread time to register the pending approval
        Thread.sleep(200);

        assertThat(tradeApprovalService.hasPendingApproval(APPROVAL_ID)).isTrue();

        // Clean up: complete the pending approval so it doesn't hang
        tradeApprovalService.processReply(APPROVAL_ID, "NO");
    }

    // ---- processReply case insensitive ----

    @Test
    @DisplayName("processReply with lowercase 'yes' still approves the request")
    void processReply_caseInsensitive()
            throws InterruptedException, ExecutionException, TimeoutException {

        CompletableFuture<Boolean> approvalFuture = CompletableFuture.supplyAsync(() ->
                tradeApprovalService.requestApproval(
                        APPROVAL_ID, "BUY", "MTNN", 150,
                        new BigDecimal("195.00"), "NGN",
                        new BigDecimal("0.016")
                )
        );

        Thread.sleep(200);

        tradeApprovalService.processReply(APPROVAL_ID, "yes");

        Boolean result = approvalFuture.get(5, TimeUnit.SECONDS);
        assertThat(result).isTrue();
    }
}
