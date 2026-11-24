package br.com.jotave.wannawatch.shared;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /** Relógio no fuso do Brasil (usado, por exemplo, para variar as fileiras da Home a cada dia). */
    @Bean
    Clock clock() {
        return Clock.system(ZoneId.of("America/Sao_Paulo"));
    }
}
