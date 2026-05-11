package dylanlederman.ai_genre.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import dylanlederman.ai_genre.services.CeleryResultSubscriber;

@Configuration
public class RedisConfig {
    @Bean("cacheRedisTemplate")
    @Primary
    public RedisTemplate<String, String> cacheRedisFactory(
        @Value("${spring.data.redis.cache.host}") String cacheHost,
        @Value("${spring.data.redis.cache.port}") int port,
        @Value("${spring.data.redis.cache.database}") int database
    ) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(cacheHost);
        config.setPort(port);
        config.setDatabase(database);

        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();

        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        return template;
    }

    @Bean("brokerRedisFactory")
    public RedisConnectionFactory brokerConnectionFactory(
        @Value("${spring.data.redis.broker.host}") String brokerHost,
        @Value("${spring.data.redis.broker.port}") int port,
        @Value("${spring.data.redis.broker.database}") int database
    ) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(brokerHost);
        config.setPort(port);
        config.setDatabase(database);

        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();
        return factory;
    }

    @Bean("brokerRedisTemplate")
    public RedisTemplate<String, String> brokerRedisTemplate(
        RedisConnectionFactory factory
    ) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        return template;
    }

    @Bean
    public ChannelTopic celeryResultTopic() {
        return new ChannelTopic("celery:results");
    }

    @Bean
    public RedisMessageListenerContainer redisListenerContainer(
        @Qualifier("brokerRedisFactory") LettuceConnectionFactory factory,
        CeleryResultSubscriber sub,
        ChannelTopic topic
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener(sub, topic);
        return container;
    }
}
