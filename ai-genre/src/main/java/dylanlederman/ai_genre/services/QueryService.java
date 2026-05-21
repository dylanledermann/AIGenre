package dylanlederman.ai_genre.services;

import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import dylanlederman.ai_genre.models.FileMetadataModel;
import dylanlederman.ai_genre.models.ResultModel;
import dylanlederman.ai_genre.models.UploadModel;
import dylanlederman.ai_genre.repositories.QueryRepo;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Service
@Slf4j
public class QueryService {
    private final QueryRepo queryRepo;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, String> redisTemplate;
    private final long ttl;

    public QueryService(
        QueryRepo queryRepo, 
        ObjectMapper objectMapper, 
        RedisTemplate<String, String> redisTemplate, 
        @Value("${spring.data.redis.cache.ttl}") long ttl
    ) {
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

    public Optional<Map<String, Object>> checkHash(String hash) {
        String cached = redisTemplate.opsForValue().get("result:" + hash);
        Optional<ResultModel> dbResult;
        ResultModel result;

        if (cached != null) {
            try {
                result = objectMapper.readValue(cached, ResultModel.Complete.class);
            } catch (Exception e) {
                log.error("Malformed cached value: file hash={} result={}", hash, cached);
                return Optional.empty();
            }
        } else if ((dbResult = queryRepo.getByFileHash(hash)).isPresent()) {
            result = dbResult.get();
        } else {
            return Optional.empty();
        }

        Set<String> resultViolations = result.validate();

        if(!resultViolations.isEmpty()) {
            log.error("Invalid saved results violations={}", resultViolations);
            return Optional.empty();
        }

        // Only cache complete results (They should not change)
        if (result instanceof ResultModel.Complete) {
            cacheResult(result);
        }

        return Optional.of(resultModelToMap(result));
    }

    private Map<String, Object> resultModelToMap(ResultModel result) {
        return switch(result) {
            case ResultModel.Pending r: yield Map.of("task_id", r.taskId(), "status", r.status());
            case ResultModel.Processing r: yield Map.of("task_id", r.taskId(), "status", r.status());
            case ResultModel.Complete r: yield Map.of("status", r.status(), "result", r.result());
            case ResultModel.Failed r: yield Map.of("status", r.status(), "error", r.error());
        };
    }

    private void cacheResult(ResultModel result) {
        try {
            redisTemplate.opsForValue().set("result:" + result.fileHash(), objectMapper.writeValueAsString(result), ttl);
        } catch (Exception e) {
            log.error("Failed to cache result fileHash={}", result.fileHash(), e);
        }
    }

    public boolean saveFile(String hash, byte[] mp3_bytes, FileMetadataModel metadata) {
        if (
            !hash.matches("^[0-9a-f]{8}\\-[0-9a-f]{4}\\-[0-9a-f]{4}\\-[0-9a-f]{4}\\-[0-9a-f]{12}$") ||
            mp3_bytes.length == 0 ||
            !metadata.getMimeType().matches("^audio\\/((x-)?wav|mpeg|ogg|(x\\-)?flac|x\\-m4a|mp4a-latm|aac|(x\\-)?aiff)$")
        ) {
            return false;
        }

        UploadModel upload = new UploadModel(hash, metadata);

        queryRepo.insertFile(upload);
        return true;
    }

    public ResultModel createTask(String fileHash, UUID taskId) {
        Optional<ResultModel> res = queryRepo.getByFileHash(fileHash);
        if (res.isPresent()) {
            ResultModel result = res.get();
            if (result instanceof ResultModel.Failed) queryRepo.resetTask(fileHash, taskId);
            else {
                if (result instanceof ResultModel.Complete) cacheResult(result);
                return result;
            }
        }
        queryRepo.insertTask(fileHash, taskId);
        return new ResultModel.Pending(taskId, fileHash);
    }
}
