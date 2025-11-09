package ua.sumdu.dds.travelerapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class TravelerApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(TravelerApiApplication.class, args);
    }
}
