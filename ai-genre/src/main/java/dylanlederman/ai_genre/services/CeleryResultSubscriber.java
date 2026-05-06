package dylanlederman.ai_genre.services;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
@Slf4j
public class CeleryResultSubscriber implements MessageListener {
    private final SimpMessagingTemplate wsTemplate;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public CeleryResultSubscriber(
        SimpMessagingTemplate wsTemplate,
        @Qualifier("brokerRedisTemplate") RedisTemplate<String, String> redisTemplate,
        ObjectMapper objectMapper
    ) {
        this.wsTemplate = wsTemplate;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte @Nullable [] pattern) {
        try {
            String body = new String(message.getBody());
            Map<String, Object> result = objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});

            String job_id = (String) result.get("job_id");
            String file_hash = (String) result.get("file_hash");

            redisTemplate.opsForValue().set(
                "result:" + file_hash,
                objectMapper.writeValueAsString(result.get("results")),
                Duration.ofHours(1)
            );

            wsTemplate.convertAndSend("/topic/status/" + job_id, (Object) result);
        } catch (Exception e) {
            e.printStackTrace();
            log.error("Error occurred sending status: " + e.getMessage());
        }
    }
}
