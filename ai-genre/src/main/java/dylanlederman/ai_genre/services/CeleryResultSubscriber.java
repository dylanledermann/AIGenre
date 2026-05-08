package dylanlederman.ai_genre.services;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import dylanlederman.ai_genre.models.CeleryMessage;
import dylanlederman.ai_genre.models.ResultModel;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import tools.jackson.databind.ObjectMapper;

@Component
@Slf4j
public class CeleryResultSubscriber implements MessageListener {
    private final SimpMessagingTemplate wsTemplate;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final long ttl;
    private final Validator validator;

    public CeleryResultSubscriber(
        SimpMessagingTemplate wsTemplate,
        @Qualifier("brokerRedisTemplate") RedisTemplate<String, String> redisTemplate,
        ObjectMapper objectMapper,
        @Value("${spring.data.redis.cache.ttl}") long ttl,
        Validator validator
    ) {
        this.wsTemplate = wsTemplate;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
        this.validator = validator;
    }

    @Override
    public void onMessage(Message message, byte @Nullable [] pattern) {
        String body = new String(message.getBody());

        CeleryMessage messageObject;
        try {
            messageObject = objectMapper.readValue(body, CeleryMessage.class);
        } catch (Exception e) {
            log.error("Malformed message on celery:results body={}", body);
            return;
        }

        Set<ConstraintViolation<CeleryMessage>> messageViolations = validator.validate(messageObject);
        if(!messageViolations.isEmpty()) {
            log.error("Invalid message from Celery: {}", messageViolations);
            return;
        }

        ResultModel result;
        try {
            result = messageObject.toResultModel();
        } catch (IllegalArgumentException e) {
            log.error("Invalid message structure {}", e.getMessage());
            if (messageObject.getTaskId() != null) {
                pushResult(messageObject.getTaskId(), "FAILED", null, "Internal Server Error");
            }
            return;
        }

        Set<String> resultViolations = result.validate();
        if(!resultViolations.isEmpty()) {
            log.error("Invalid results from Celery: {}", resultViolations);
            return;
        }

        switch (result) {
            case ResultModel.Pending r -> pushResult(r.taskId(), r.status(), null, null);
            case ResultModel.Processing r -> pushResult(r.taskId(), r.status(), null, null);
            case ResultModel.Complete r -> {
                cacheResult(r.fileHash(), r.result());
                pushResult(r.taskId(), r.status(), r.result(), null);
            }
            case ResultModel.Failed r -> {
                log.warn("Analysis failed taskId={} error={}", r.taskId(), r.error());
                pushResult(r.taskId(), r.status(), null, r.error());
            }
        }
    }

    private void pushResult(UUID taskId, String status, Object result, String error) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("status", status);
        if (result != null) payload.put("result", result);
        if (error != null) payload.put("error", error);
        wsTemplate.convertAndSend("topic/results/" + taskId, (Object) payload);
    }

    private void cacheResult(String fileHash, Object result) {
         try {
            redisTemplate.opsForValue().set("result:" + fileHash, objectMapper.writeValueAsString(result), ttl);
         } catch (Exception e) {
            log.error("Failed to cache result fileHash={}", fileHash, e);
         }
    }
}
