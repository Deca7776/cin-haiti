package ht.oni.cin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CinApplication {

    public static void main(String[] args) {
        SpringApplication.run(CinApplication.class, args);
    }
}
