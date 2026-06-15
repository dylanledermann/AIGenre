package dylanlederman.ai_genre.config;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class DataSourceConfig {
    @Value("${spring.datasource.url}")
    private String datasourceUrl;
    @Value("${spring.datasource.username}")
    private String datasourceUsername;
    @Value("${spring.datasource.password}")
    private String datasourcePassword;

    @Value("${spring.flyway.location}")
    private String flywayLocation;

    @Bean
    public Flyway initFlyway(DataSource datasource) {
        log.info(String.format("Connecting to %s, %s:%s", datasourceUrl, datasourceUsername, datasourcePassword));
        Flyway flyway = Flyway
            .configure()
            .dataSource(datasource)
            .locations(flywayLocation)
            .load();
        flyway.migrate();
        return flyway;
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource datasource, Flyway flyway) {
        return new JdbcTemplate(datasource);
    }

    @Bean
    public NamedParameterJdbcTemplate namedJdbcTemplate(DataSource datasource, Flyway flyway) {
        return new NamedParameterJdbcTemplate(datasource);
    }
}
