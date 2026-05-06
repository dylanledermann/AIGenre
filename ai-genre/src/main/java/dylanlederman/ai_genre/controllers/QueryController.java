package dylanlederman.ai_genre.controllers;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
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
    @Autowired
    private QueryService queryService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    @Qualifier("brokerRedisTemplate")
    private RedisTemplate<String, String> redisTemplate;
    
    @PostMapping()
    public ResponseEntity<?> handleMp3Upload(@RequestParam("file") MultipartFile file) throws Exception {
        if (
            file.isEmpty() || 
            !file.getContentType().matches("^audio\\/((x-)?wav|mpeg|ogg|(x\\-)?flac|x\\-m4a|mp4a-latm|aac|(x\\-)?aiff)$")
        ) {
            log.info(file.getContentType());
            return ResponseEntity.badRequest().body("File type must be .mp3");
        }

        byte[] fileBytes = file.getBytes();
        String fileHash = queryService.hashFile(fileBytes);

        Optional<Map<String, String>> savedRes = queryService.checkHash(fileHash);
        if (savedRes.isPresent()) {
            return ResponseEntity.ok(savedRes.get());
        }

        queryService.saveFile(fileHash, fileBytes);
        String jobId = UUID.randomUUID().toString();

        Map<String, String> task = Map.of(
            "job_id", jobId,
            "file_hash", fileHash
        );
        redisTemplate.convertAndSend("celery:tasks", objectMapper.writeValueAsString(task));
        
        return ResponseEntity.accepted().body(Map.of("job_id", jobId));
    }
}