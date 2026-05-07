package dylanlederman.ai_genre.services;

import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import dylanlederman.ai_genre.models.FileModel;
import dylanlederman.ai_genre.models.ResultModel;
import dylanlederman.ai_genre.models.UploadModel;
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
    private long ttl;

    public QueryService(QueryRepo queryRepo, ObjectMapper objectMapper, RedisTemplate<String, String> redisTemplate, @Value("${spring.data.redis.cache.ttl}") long ttl) {
        this.queryRepo = queryRepo;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
    }

    public String hashFile(byte[] mp3_bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        
        byte[] encodedHash = digest.digest(mp3_bytes);

        StringBuilder hexString = new StringBuilder(64);
        for (byte b:encodedHash) {
            String hex = Integer.toHexString(0xFF & b);
            if (hex.length() == 1) hexString.append('0');
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
            Map<String, String> response = Map.of(
                "task_id", queryVal.getTaskId().toString(),
                "status", queryVal.getStatus(),
                "genre", queryVal.getResult().getOrDefault("genre", "Unknown"),
                "accuracy", queryVal.getResult().getOrDefault("accuracy", "100%"),
                "error", queryVal.getResult().getOrDefault("error", "N/A")
            );
            redisTemplate.opsForValue().setGet(hash, objectMapper.writeValueAsString(response), Duration.ofMillis(ttl));
            return Optional.of(
                response
            );
        }

        return Optional.empty();
    }

    public boolean saveFile(String hash, byte[] mp3_bytes, Map<String, String> metadata) {
        if (
            !hash.matches("^[0-9a-f]{8}\\-[0-9a-f]{4}\\-[0-9a-f]{4}\\-[0-9a-f]{4}\\-[0-9a-f]{12}$") ||
            mp3_bytes.length == 0 ||
            !metadata.getOrDefault("mimeType", "").matches("^audio\\/((x-)?wav|mpeg|ogg|(x\\-)?flac|x\\-m4a|mp4a-latm|aac|(x\\-)?aiff)$")
        ) {
            return false;
        }

        UploadModel upload = new UploadModel(hash, metadata);
        FileModel file = new FileModel(hash, mp3_bytes);

        queryRepo.insertFile(upload, file);
        return true;
    }
}
