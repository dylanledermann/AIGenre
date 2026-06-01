package dylanlederman.ai_genre.Unit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.connection.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dylanlederman.ai_genre.models.GenreResultModel;
import dylanlederman.ai_genre.services.CeleryResultSubscriber;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(classes ={
    CeleryResultSubscriber.class
}, properties = {
    "spring.data.redis.cache.ttl=0"
})
@ImportAutoConfiguration(classes = {
    JacksonAutoConfiguration.class,
    ValidationAutoConfiguration.class
})
public class CeleryResultSubscriberTest {
    @MockitoBean
    private SimpMessagingTemplate wsTemplate;
    @MockitoBean
    private RedisTemplate<String, String> redisTemplate;
    @MockitoBean
    private ValueOperations<String, String> valueOps;
    @Autowired
    private ObjectMapper objectMapper;
    @Value("${spring.data.redis.broker.ttl}")
    private long ttl;
    @Autowired
    private CeleryResultSubscriber celeryResultSubscriber;

    @BeforeEach
    void setup() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void onValidMessage() {
        assertDoesNotThrow(() -> {
            UUID taskId = UUID.randomUUID();
            String status = "COMPLETE";
            String fileHash = "a".repeat(64);
            GenreResultModel genreRes = new GenreResultModel("pop", "73%");
            Map<String, Object> payload = Map.of(
                "task_id", taskId,
                "file_hash", fileHash,
                "status", status,
                "results", genreRes
            );

            String json = objectMapper.writeValueAsString(payload);
            Message message = mock(Message.class);
            when(message.getBody()).thenReturn(json.getBytes());

            celeryResultSubscriber.onMessage(message, null);

            Map<String, Object> expected = Map.of(
                "taskId", taskId,
                "status", status,
                "results", genreRes
            );

            verify(wsTemplate).convertAndSend("/topic/results/" + taskId, (Object) expected);
        });
    }

    @Test
    void onNonJsonMessage() {
        assertDoesNotThrow(() -> {
            Message message = mock(Message.class);
            when(message.getBody()).thenReturn("not json format.".getBytes());

            celeryResultSubscriber.onMessage(message, null);
            verify(wsTemplate, never()).convertAndSend(anyString(), any(Object.class));
        });
    }

    @Test
    void onIncorrectJsonMessage() {
        // Checks if program fails gracefull when message is not CeleryMessage (model) format.
        assertDoesNotThrow(() -> {
            Message message = mock(Message.class);
            Map<String, String> jsonMap = Map.of("fileHash", "value");
            String jsonString = objectMapper.writeValueAsString(jsonMap);
            when(message.getBody()).thenReturn(jsonString.getBytes());

            celeryResultSubscriber.onMessage(message, null);
            verify(wsTemplate, never()).convertAndSend(anyString(), any(Object.class));
        });
    }
}