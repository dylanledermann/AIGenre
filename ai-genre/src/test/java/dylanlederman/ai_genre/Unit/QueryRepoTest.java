package dylanlederman.ai_genre.Unit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PGobject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import dylanlederman.ai_genre.TestContainers.BaseTestContainers;
import dylanlederman.ai_genre.config.DataSourceConfig;
import dylanlederman.ai_genre.models.FileMetadataModel;
import dylanlederman.ai_genre.models.GenreResultModel;
import dylanlederman.ai_genre.models.ResultModel;
import dylanlederman.ai_genre.models.UploadModel;
import dylanlederman.ai_genre.repositories.QueryRepo;
import tools.jackson.databind.ObjectMapper;


@SpringBootTest(classes={
    DataSourceConfig.class,
    QueryRepo.class
})
@ImportAutoConfiguration(classes={
    DataSourceAutoConfiguration.class,
    DataSourceTransactionManagerAutoConfiguration.class, // @Transactional class
    JacksonAutoConfiguration.class // ObjectMapper class
})
public class QueryRepoTest extends BaseTestContainers {
    @Autowired
    QueryRepo queryRepo;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    ObjectMapper objectMapper;

    // Map to track the ResultModels added
    Map<String, ResultModel> addedTasks; 
    
    @BeforeEach
    void setup() {
        // Add some test data to the db (One for each status)
        addedTasks = new HashMap<String, ResultModel>();
        
        // Create a model for each and store the model under its status in the addedTasks map to be accessed in tests
        addedTasks.put(
            "COMPLETE", 
            new ResultModel.Complete(
                UUID.randomUUID(), 
                "COMPLETE", 
                new GenreResultModel("Genre", "Acc")
            )
        );
        addedTasks.put(
            "FAILED", 
            new ResultModel.Failed(
                UUID.randomUUID(), 
                "FAILED", 
                "Error"
            )
        );
        addedTasks.put(
            "PENDING", 
            new ResultModel.Pending(
                UUID.randomUUID(), 
                "PENDING"
            )
        );
        addedTasks.put(
            "PROCESSING", 
            new ResultModel.Processing(
                UUID.randomUUID(), 
                "PROCESSING"
            )
        );
    }

    @Test
    @Transactional
    void testInsert() {
        String hash = "a".repeat(64);
        FileMetadataModel metadata = new FileMetadataModel(
            "fileName",
            (long) 0,
            "mimeType"
        );
        UploadModel upload = new UploadModel(hash, metadata);
        queryRepo.insertFile(upload);
        assertEquals(queryRepo.getResultsByFileHash(hash), Optional.empty());
        String uploadQuery = """
            SELECT * FROM uploads WHERE file_hash = ?
        """;
        Map<String, Object> uploadRes = jdbcTemplate.queryForMap(uploadQuery, hash);
        assertEquals((String) uploadRes.get("file_hash"), hash);
        assertEquals(objectMapper.readValue(((PGobject) uploadRes.get("file_metadata")).getValue(), FileMetadataModel.class), metadata);
    }

    @Test
    @Transactional
    void testInsertOnConflict() {

    }

    @Test
    @Transactional
    void testGetResultsByFileHash() {
        String hash = "a".repeat(64);
        FileMetadataModel metadata = new FileMetadataModel(
            "fileName",
            (long) 0,
            "mimeType"
        );
        UploadModel upload = new UploadModel(hash, metadata);

        queryRepo.insertFile(upload);

        String taskHash = "b".repeat(64);
        String status = "PROCESSING";
        UUID taskId = UUID.randomUUID();

        String insertTaskQuery = """
            INSERT INTO audio_results (sample_hash, file_hash, task_id, status, error, result)
            VALUES (?, ?, ?, ?, ?, ?::jsonb)     
        """;
        jdbcTemplate.update(
            insertTaskQuery, 
            taskHash, 
            hash, 
            taskId, 
            status,
            null,
            null
        );

        ResultModel correctResult = new ResultModel.Processing(taskId, hash);

        Optional<ResultModel> queryRes = queryRepo.getResultsByFileHash(hash);

        assertFalse(queryRes.isEmpty());
        assertEquals(queryRes.get(), correctResult);
    }

    @Test
    @Transactional
    void testGetResultsByFileHashMultipleHash() {
        // Use the same hash, but different results
        String hash = "a".repeat(64);
        FileMetadataModel metadata = new FileMetadataModel(
            "fileName",
            (long) 0,
            "mimeType"
        );
        UploadModel upload = new UploadModel(hash, metadata);

        queryRepo.insertFile(upload);

        // Different status, sample hash, task id, to validate different values exist
        // Second task should be returned, since it is inserted after
        String sampleHash1 = "b".repeat(64);
        String sampleHash2 = "c".repeat(64);
        String status1 = "FAILED";
        String status2 = "PROCESSING";
        UUID taskId1 = UUID.randomUUID();
        UUID taskId2 = UUID.randomUUID();

        // Make sure they are different (should just require a testing rerun)
        assert(!taskId1.equals(taskId2));

        String insertTaskQuery = """
            INSERT INTO audio_results (sample_hash, file_hash, task_id, status, error, result, created_at)
            VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)
        """;
        
        // Insert first failed task, then wait, then insert second
        // Ensures the second task was created last
        jdbcTemplate.update(
            insertTaskQuery, 
            sampleHash1, hash, taskId1, status1, "error", null, Timestamp.valueOf(LocalDateTime.now())
        );

        jdbcTemplate.update(
            insertTaskQuery,
            sampleHash2, hash, taskId2, status2, null, null, Timestamp.valueOf(LocalDateTime.now().plusSeconds(10))
        );

        Optional<ResultModel> results = queryRepo.getResultsByFileHash(hash);

        // Results should be ResultModel.Processing with second task id
        assertFalse(results.isEmpty());
        assertEquals(new ResultModel.Processing(taskId2, hash), results.get());
    }

    @Test
    @Transactional
    void testGetResultsByFileHashDNE() {
        Optional<ResultModel> results = queryRepo.getResultsByFileHash("someHash");
        assertTrue(results.isEmpty());
    }

    @Test
    @Transactional
    void testInsertTask() {
        // New tasks should default to pending state
        UUID taskId = UUID.randomUUID();
        String hash = "a".repeat(64);
        // Create file upload
        FileMetadataModel metadata = new FileMetadataModel(
            "fileName",
            (long) 0,
            "mimeType"
        );
        UploadModel upload = new UploadModel(hash, metadata);

        queryRepo.insertFile(upload);
        ResultModel expected = new ResultModel.Pending(taskId, hash);

        queryRepo.insertTask(hash, taskId);

        assertEquals(expected, queryRepo.getResultsByFileHash(hash).get());
    }

    @Test
    @Transactional
    void testInsertTaskNoUpload() {
        // Tests an error occurs when a task is inserted without a corresponding foreign key in uploads
        assertThrows(DataIntegrityViolationException.class, () -> {
            queryRepo.insertTask("a", UUID.randomUUID());
        });
    }

    @Test
    @Transactional
    void testInsertTaskExists() {
        UUID taskId = UUID.randomUUID();
        String hash = "a";
        // verify an error did not occur in the first insert
        final boolean[] insertTaskRanTwice = {false};

        // Should throw error -> indicates duplicate UUID
        assertThrows(DuplicateKeyException.class, () -> {
            // Create Upload file that insertTask references
            FileMetadataModel metadata = new FileMetadataModel(
                "fileName",
                (long) 0,
                "mimeType"
            );
            UploadModel upload = new UploadModel(hash, metadata);

            queryRepo.insertFile(upload);

            // Double insert with the same task id should throw error.
            queryRepo.insertTask(hash, taskId);
            insertTaskRanTwice[0] = true;
            queryRepo.insertTask(hash, taskId);

            // Exception shoud be thrown after second insert
            fail();
        });

        assertTrue(insertTaskRanTwice[0]);
    }

    @Test
    @Transactional
    void testDeleteTaskExists() {
        // Simple insert audio results -> delete audio results -> validate they are not in the db
        UUID taskId = UUID.randomUUID();
        String hash = "a";

        // Create value in Upload table
        FileMetadataModel metadata = new FileMetadataModel(
            "fileName",
            (long) 0,
            "mimeType"
        );
        UploadModel upload = new UploadModel(hash, metadata);
        queryRepo.insertFile(upload);

        // Add task to audio_results
        queryRepo.insertTask(hash, taskId);
        queryRepo.deleteTask(taskId);

        // Verify it does not exist
        assertTrue(queryRepo.getResultsByFileHash(hash).isEmpty());
    }

    @Test
    @Transactional
    void testDeleteTaskDNE() {
        // General check an error does not occur if no task exists
        assertDoesNotThrow(() -> {
            UUID taskId = UUID.randomUUID();
            queryRepo.deleteTask(taskId);
        });
    }
}
