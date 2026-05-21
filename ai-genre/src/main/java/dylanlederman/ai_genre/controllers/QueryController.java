package dylanlederman.ai_genre.controllers;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import dylanlederman.ai_genre.models.FileMetadataModel;
import dylanlederman.ai_genre.models.ResultModel;
import dylanlederman.ai_genre.services.GrpcServiceStub;
import dylanlederman.ai_genre.services.QueryService;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/query")
@Slf4j
public class QueryController {
    private final QueryService queryService;
    private final GrpcServiceStub grpcServiceStub;

    public QueryController(
            QueryService queryService,
            GrpcServiceStub gprcServiceStub) {
        this.queryService = queryService;
        this.grpcServiceStub = gprcServiceStub;
    }

    @PostMapping()
    public ResponseEntity<?> handleMp3Upload(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty() ||
                file.getContentType() == null ||
                !file.getContentType()
                        .matches("^audio\\/((x-)?mp3|(x-)?wav|mpeg|ogg|(x\\-)?flac|x\\-m4a|mp4a-latm|aac|(x\\-)?aiff)$")) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "error",
                            String.format("%s file types not allowed", file.getContentType())));
        }

        byte[] fileBytes = file.getBytes();
        String fileHash = queryService.hashFile(fileBytes);

        FileMetadataModel metadata = new FileMetadataModel(file.getName(), file.getSize(), file.getContentType());

        Optional<Map<String, Object>> savedRes = queryService.checkHash(fileHash);
        if (savedRes.isPresent()) {
            return ResponseEntity.ok(savedRes.get());
        }

        if (!queryService.saveFile(fileHash, fileBytes, metadata)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid file"));
        }
        UUID taskId = UUID.randomUUID();
        ResultModel returnedTask = queryService.createTask(fileHash, taskId);

        if (!returnedTask.taskId().equals(taskId)) {
            return ResponseEntity.accepted().body(returnedTask);
        }

        try {
            grpcServiceStub.buildTaskStub(taskId.toString(), fileHash, fileBytes).get();
            return ResponseEntity.accepted().body(returnedTask);
        } catch (Exception e) {
            log.error("Error occurred building task stub: ", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Internal Server Error"));
        }
    }
}