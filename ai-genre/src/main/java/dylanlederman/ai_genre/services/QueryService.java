package dylanlederman.ai_genre.services;

import java.security.MessageDigest;
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

    /**
     * Checks if the given hash already exists in the cache or db.
     * If the hash already exists, return the results if completed, the task id if pending or processing.
     * If failed, the task will be reran
     * @param hash (String) the hash of the file to be checked
     * @return (Optional<ResultModel>) Optional containing the result model if complete, pending, or processing, otherwise empty.
     */
    public Optional<ResultModel> checkHash(String hash) {
        String cached = redisTemplate.opsForValue().get("result:" + hash);
        Optional<ResultModel> dbResult;
        ResultModel result;

        if (cached != null) {
            // Convert cached value to ResultModel (only complete is cached)
            try {
                result = objectMapper.readValue(cached, ResultModel.Complete.class);
            } catch (Exception e) {
                log.error("Malformed cached value: file hash={} result={}", hash, cached);
                // Delete invalid cached values
                redisTemplate.delete("result:" + hash);
                return Optional.empty();
            }
        } else if ((dbResult = queryRepo.getResultsByFileHash(hash)).isPresent()) {
            // Check if db contains fileHash
            result = dbResult.get();
        } else {
            return Optional.empty();
        }

        // Validate the result for the ResultModel fields
        Set<String> resultViolations = result.validate();
        if(!resultViolations.isEmpty()) {
            log.error("Invalid saved results violations={}", resultViolations);
            return Optional.empty();
        }

        // Only cache complete results (They should not change)
        if (result instanceof ResultModel.Complete) {
            cacheResult(result);
        } else if (result instanceof ResultModel.Failed) {
            // If the task failed, a new one should be started
            return Optional.empty();
        }

        // Returned cached result
        return Optional.of(result);
    }

    // Private function that caches the given ResultModel.
    private void cacheResult(ResultModel result) {
        try {
            redisTemplate.opsForValue().set("result:" + result.fileHash(), objectMapper.writeValueAsString(result), ttl);
        } catch (Exception e) {
            log.error("Failed to cache result fileHash={}", result.fileHash(), e);
        }
    }

    /**
     * Validates and saves the given file information to the database
     * @param hash (String) hash of the file
     * @param mp3_bytes (byte[]) byte array for the file
     * @param metadata (FileMetadataModel) Object containing information about the file (length, mimetype, etc.)
     * @return boolean indicating whether the file was saved or not as true or false respectively.
     */
    public boolean saveFile(String hash, byte[] mp3_bytes, FileMetadataModel metadata) {
        // Validate the all inputs
        if (
            !hash.matches("^[0-9a-f]{64}$") ||
            mp3_bytes.length == 0 ||
            !metadata.getMimeType().matches("^audio\\/((x-)?wav|mpeg|ogg|(x-)?flac|x-m4a|mp4a-latm|aac|(x-)?aiff)$")
        ) {
            return false;
        }

        // Create UploadModel from the inputs and save the file
        UploadModel upload = new UploadModel(hash, metadata);

        queryRepo.insertFile(upload);
        return true;
    }

    /**
     * Creates a new task for the given fileHash
     * @param fileHash (String) hash of file the task is being created for
     * @return ResultModel.Pending for the task
     */
    public ResultModel createTask(String fileHash) {
        UUID taskId = UUID.randomUUID();
        queryRepo.insertTask(fileHash, taskId);
        return new ResultModel.Pending(taskId, fileHash);
    }

    /**
     * Deletes a task from the database
     * @param taskId (UUID) task to be deleted
     */
    public void deleteTask(UUID taskId) {
        queryRepo.deleteTask(taskId);
    }
}
