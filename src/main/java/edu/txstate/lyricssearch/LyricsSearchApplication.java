package edu.txstate.lyricssearch;
import edu.txstate.lyricssearch.service.IndexService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
@SpringBootApplication
public class LyricsSearchApplication {
    public static void main(String[] args) {
        // Starts the Spring Boot application
        SpringApplication.run(LyricsSearchApplication.class, args);
    }
    /**
     * This 'init' bean runs immediately after the application context loads.
     * We use it to ensure the Lucene search index is ready before any users
     * try to search, which is especially helpful for the first run on a hosting service.
     */
    @Bean
    CommandLineRunner init(IndexService indexService) {
        // Automatically builds the search index when the app starts up
        return args -> indexService.buildIndexIfNeeded();
    }
}