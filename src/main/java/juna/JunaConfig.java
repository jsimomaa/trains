package juna;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JunaConfig {

    @Value("${juna.servername}")
    private String servername;

    @Value("${juna.fromemail}")
    private String fromEmail;

    @Bean
    public Juna juna() {
        return new Juna(servername, fromEmail);
    }
}
