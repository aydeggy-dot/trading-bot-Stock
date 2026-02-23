package com.ngxbot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "notification")
public class NotificationProperties {
    private WhatsApp whatsapp = new WhatsApp();
    private Telegram telegram = new Telegram();
    private Approval approval = new Approval();

    @Data
    public static class WhatsApp {
        private boolean enabled = true;
        private String wahaBaseUrl = "http://localhost:3000";
        private String wahaSession = "default";
        private String wahaApiKey = "";
        private String chatId;
    }

    @Data
    public static class Telegram {
        private boolean enabled = true;
        private String botToken;
        private String chatId;
    }

    @Data
    public static class Approval {
        private int timeoutMinutes = 5;
        private String defaultOnTimeout = "REJECT";
    }
}
