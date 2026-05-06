package dylanlederman.ai_genre.services;

import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import dylanlederman.ai_genre.models.ResultModel;
import dylanlederman.ai_genre.repositories.QueryRepo;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@Slf4j
public class QueryService {
    private QueryRepo queryRepo;
    private ObjectMapper objectMapper;
    private RedisTemplate<String, String> redisTemplate;

    public QueryService(QueryRepo queryRepo, ObjectMapper objectMapper, RedisTemplate<String, String> redisTemplate) {
        this.queryRepo = queryRepo;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }

    public String hashFile(byte[] mp3_bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        
        byte[] encodedHash = digest.digest(mp3_bytes);

        StringBuilder hexString = new StringBuilder(64);
        for (byte b:encodedHash) {
            String hex = Integer.toHexString(b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }

        return hexString.toString();
    }

    public Optional<Map<String, String>> checkHash(String hash) {
        String cached = redisTemplate.opsForValue().get("result:" + hash);
        if (cached != null) {
            Map<String, String> cachedRes = objectMapper.readValue(cached, new TypeReference<Map<String, String>>(){});
            return Optional.of(cachedRes);
        }

        Optional<ResultModel> queryRes = queryRepo.getByFileHash(hash);
        if (queryRes.isPresent()) {
            ResultModel queryVal = queryRes.get();
            return Optional.of(
                Map.of(
                    "task_id", queryVal.getTaskId(),
                    "status", queryVal.getStatus(),
                    "genre", queryVal.getResult().getOrDefault("genre", "Unknown"),
                    "acc", queryVal.getResult().getOrDefault("accuracy", "100%")
                )
            );
        }

        return Optional.empty();
    }

    public void saveFile(String hash, byte[] mp3_bytes) {
        
    }
}
