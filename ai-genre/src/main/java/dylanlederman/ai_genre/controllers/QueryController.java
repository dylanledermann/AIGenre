package dylanlederman.ai_genre.controllers;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import dylanlederman.ai_genre.services.QueryService;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/query")
@Slf4j
public class QueryController {
    private QueryService queryService;
    private ObjectMapper objectMapper;
    @Qualifier("brokerRedisTemplate")
    private RedisTemplate<String, String> redisTemplate;

    public QueryController(
        QueryService queryService,
        ObjectMapper objectMapper,
        @Qualifier("brokerRedisTemplate") RedisTemplate<String, String> redisTemplate
    ) {
        this.queryService = queryService;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }
    
    @PostMapping()
    public ResponseEntity<?> handleMp3Upload(@RequestParam("file") MultipartFile file) throws Exception {
        if (
            file.isEmpty() || 
            file.getContentType() == null ||
            !file.getContentType().matches("^audio\\/((x-)?wav|mpeg|ogg|(x\\-)?flac|x\\-m4a|mp4a-latm|aac|(x\\-)?aiff)$")
        ) {
            log.info(file.getContentType());
            return ResponseEntity.badRequest().body(
                Map.of(
                    "error", 
                    String.format("%s file types not allowed", file.getContentType())
                )
            );
        }

        byte[] fileBytes = file.getBytes();
        String fileHash = queryService.hashFile(fileBytes);

        Map<String, String> metadata = Map.of(
            "mimeType", file.getContentType(),
            "fileName", file.getName(),
            "fileSize", Long.toString(file.getSize())
        );

        Optional<Map<String, String>> savedRes = queryService.checkHash(fileHash);
        if (savedRes.isPresent()) {
            Map<String, String> response = new HashMap<>();
            response.putAll(metadata);
            response.putAll(savedRes.get());
            return ResponseEntity.ok(response);
        }

        if (!queryService.saveFile(fileHash, fileBytes, metadata)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid file"));
        }
        UUID jobId = UUID.randomUUID();

        Map<String, String> task = Map.of(
            "task_id", jobId.toString(),
            "file_hash", fileHash
        );
        redisTemplate.convertAndSend("celery:tasks", objectMapper.writeValueAsString(task));
        
        return ResponseEntity.accepted().body(Map.of("task_id", jobId.toString()));
    }
}