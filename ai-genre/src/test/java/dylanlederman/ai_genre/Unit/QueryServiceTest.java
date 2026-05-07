package dylanlederman.ai_genre.Unit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dylanlederman.ai_genre.models.ResultModel;
import dylanlederman.ai_genre.repositories.QueryRepo;
import dylanlederman.ai_genre.services.QueryService;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(classes={
    QueryService.class
})
@ImportAutoConfiguration(classes={
    ObjectMapper.class
})
public class QueryServiceTest {
    @MockitoBean
    private QueryRepo queryRepo;
    @MockitoBean
    private RedisTemplate<String, String> redisTemplate;
    @MockitoBean
    private ValueOperations<String, String> valueOperations;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private QueryService queryService;

    @BeforeEach
    void setup() { 
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void testHashFunc() {
        assertDoesNotThrow(() -> {
            byte[] tempBytes = "aaaaa".getBytes();
            String hash = queryService.hashFile(tempBytes);
            assertTrue(hash.length() == 64);
            assertTrue(hash.matches("^[0-9a-f]{64}$"));
        });
    }

    @Test
    void testCheckHashCached() {
        assertDoesNotThrow(() -> {
            byte[] tempBytes = "aaaa".getBytes();
            String hash = queryService.hashFile(tempBytes);
            Map<String, String> cache = Map.of("Key", "Value");
            String cacheString = objectMapper.writeValueAsString(cache);
            when(valueOperations.get("result:" + hash)).thenReturn(cacheString);
            Optional<Map<String, String>> res = queryService.checkHash(hash);
            assertFalse(res.isEmpty());
            assertEquals(cache, res.get());
        });
    }

    @Test
    void testCheckHashRepo() {
        assertDoesNotThrow(() -> {
            byte[] tempBytes = "aaaa".getBytes();
            String hash = queryService.hashFile(tempBytes);
            Map<String, String> resultMap = Map.of(
                "genre", "pop",
                "accuracy", "50%"
            );
            UUID taskId = UUID.randomUUID();
            ResultModel queryResult = ResultModel.builder()
                .taskId(taskId)
                .status("COMPLETE")
                .result(resultMap)
                .build();

            when(queryRepo.getByFileHash(hash)).thenReturn(Optional.of(queryResult));

            Optional<Map<String, String>> res = queryService.checkHash(hash);
            
            assertFalse(res.isEmpty());
            assertTrue(
                Map.of(
                    "task_id", queryResult.getTaskId().toString(),
                    "status", queryResult.getStatus(),
                    "genre", queryResult.getResult().get("genre"),
                    "accuracy", queryResult.getResult().get("accuracy")
                ).equals(res.get())
            );
        });
    }
    
    @Test
    void testCheckHashDNE() {
        String hash = "a".repeat(64);
        assertTrue(queryService.checkHash(hash).isEmpty());
    }
}
