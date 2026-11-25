package com.khoaphamlee.wallet.common.time;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the application clock.
 *
 * <p>Code that needs the current time injects {@link Clock} instead of calling {@code Instant.now()},
 * so tests can pass a fixed clock (for example to create an already expired token).
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
