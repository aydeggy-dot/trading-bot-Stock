package com.ngxbot.config;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Playwright configuration — launches Chromium browser as a Spring bean.
 * Only activates when meritrade.enabled=true to avoid launching a browser
 * during normal unit tests or when broker integration is disabled.
 *
 * Browser is a heavyweight singleton (Chromium process).
 * Page/BrowserContext are created per-session inside TroveBrowserAgent.
 *
 * Shutdown order matters: Browser must close before Playwright.
 * Spring destroys beans in reverse creation order, so this is handled automatically.
 */
@Configuration
@ConditionalOnProperty(name = "meritrade.enabled", havingValue = "true")
@Slf4j
public class PlaywrightConfig {

    @Bean(destroyMethod = "close")
    public Playwright playwright() {
        log.info("[PLAYWRIGHT] Creating Playwright instance");
        return Playwright.create();
    }

    @Bean(destroyMethod = "close")
    public Browser browser(Playwright playwright, MeritradeProperties config) {
        log.info("[PLAYWRIGHT] Launching Chromium — headless={}, slowMo={}ms",
                config.isHeadless(), config.getSlowMoMs());

        Browser browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions()
                        .setHeadless(config.isHeadless())
                        .setSlowMo(config.getSlowMoMs())
        );

        log.info("[PLAYWRIGHT] Chromium launched successfully");
        return browser;
    }
}
