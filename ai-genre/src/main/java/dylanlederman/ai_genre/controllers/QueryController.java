package dylanlederman.ai_genre.controllers;

import java.util.Map;
import java.util.Optional;

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

    /**
     * Handles post requests to /api/query.
     * Takes in a file as a part of the request, validates the file, then creates a task to run audio analysis on the task.
     * @param file Multipart file tagged as "file" in the body
     */
    @PostMapping()
    public ResponseEntity<?> handleMp3Upload(@RequestParam("file") MultipartFile file) throws Exception {
        // Validate the given file exists and is an accepted audio type
        if (file.isEmpty() ||
                file.getContentType() == null ||
                !file.getContentType()
                        .matches("^audio\\/((x-)?mp3|(x-)?wav|mpeg|ogg|(x-)?flac|x-m4a|mp4a-latm|aac|(x-)?aiff)$")) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "error",
                            String.format("%s file types not allowed", file.getContentType())));
        }

        // Get the bytes and hash the file to check if it has already been computed
        byte[] fileBytes = file.getBytes();
        String fileHash = queryService.hashFile(fileBytes);

        FileMetadataModel metadata = new FileMetadataModel(file.getName(), file.getSize(), file.getContentType());

        // Check if the hash exists. If so -> return it
        // To-Do only return if it exists and hasn't failed (running or complete), else get ready to re-run it
        Optional<ResultModel> savedRes = queryService.checkHash(fileHash);

        // Send task with complete status if already created
        if (savedRes.isPresent()) {
            return ResponseEntity.ok(savedRes.get());
        }

        // Save file to db
        // If false, an error occurred saving the file
        if (!queryService.saveFile(fileHash, fileBytes, metadata)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid file"));
        }

        // Create task and save it
        ResultModel newTask = queryService.createTask(fileHash);

        try {
            // Create grpc request if the task does not exist, otherwise send the task id/completed results depending on the status.
            log.info(" for task " + newTask.taskId().toString());
            grpcServiceStub.buildTaskStub(newTask.taskId().toString(), fileHash, fileBytes).get();
            return ResponseEntity.accepted().body(newTask);
        } catch (Exception e) {
            // Delete the task id if an error occurred
            log.info("Exception occurred for task " + newTask.taskId().toString());
            queryService.deleteTask(newTask.taskId());
            log.error("Error occurred building task stub: ", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Internal Server Error"));
        }
    }
}