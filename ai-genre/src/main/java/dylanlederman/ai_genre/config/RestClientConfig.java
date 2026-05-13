package dylanlederman.ai_genre.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {
    @Bean
    public RestClient workerRestClient(
        @Value("${spring.celery.url}") String workerUrl
    ) {
        return RestClient.builder().baseUrl(workerUrl).build();
    }
}
