package dylanlederman.ai_genre.config;

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
    @Value("${spring.data.redis.cache-host")
    private String cacheHost;
    @Value("${spring.data.redis.broker-host")
    private String brokerHost;
    @Value("${spring.data.redis.port")
    private int port;

    @Bean("cacheRedisTemplate")
    @Primary
    public RedisTemplate<String, String> cacheRedisFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(cacheHost);
        config.setPort(port);
        config.setDatabase(0);

        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();

        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        return template;
    }

    @Bean("brokerRedisTemplate")
    public RedisTemplate<String, String> brokerRedisTemplate() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(brokerHost);
        config.setPort(port);
        config.setDatabase(1);

        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();

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
    public RedisMessageListenerContainer redistListenerContainer(
        RedisConnectionFactory factory,
        CeleryResultSubscriber sub,
        ChannelTopic topic
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener(sub, topic);
        return container;
    }
}
