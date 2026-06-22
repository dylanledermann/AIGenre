package dylanlederman.ai_genre.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;

import io.micrometer.core.instrument.MeterRegistry;

public class MetricsConfig {
    @Value("${spring.application.name}") private String applicationName;

    @Bean
    MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config().commonTags("application", applicationName);
    }    
}
