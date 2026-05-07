package dylanlederman.ai_genre.TestContainers;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public abstract class BaseTestContainers {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    @Container
    static GenericContainer<?> cacheRedis = new GenericContainer<>("redis:7")
        .withExposedPorts(6379);

    @Container
    static GenericContainer<?> brokerRedis = new GenericContainer<>("redis:7")
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("spring.data.redis.cache.host", cacheRedis::getHost);
        registry.add("spring.data.redis.cache.port", () -> cacheRedis.getMappedPort(6379));

        registry.add("spring.data.redis.broker.host", brokerRedis::getHost);
        registry.add("spring.data.redis.broker.port", () -> brokerRedis.getMappedPort(6379));
    }
}
