package dylanlederman.ai_genre.Unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.postgresql.util.PGobject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import dylanlederman.ai_genre.TestContainers.BaseTestContainers;
import dylanlederman.ai_genre.config.DataSourceConfig;
import dylanlederman.ai_genre.models.FileMetadataModel;
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
        assertEquals(queryRepo.getByFileHash(hash), Optional.empty());
        String uploadQuery = """
            SELECT * FROM uploads WHERE file_hash = ?
        """;
        Map<String, Object> uploadRes = jdbcTemplate.queryForMap(uploadQuery, hash);
        assertEquals((String) uploadRes.get("file_hash"), hash);
        assertEquals(objectMapper.readValue(((PGobject) uploadRes.get("file_metadata")).getValue(), FileMetadataModel.class), metadata);
    }

    @Test
    @Transactional
    void testGetByHash() {
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

        Optional<ResultModel> queryRes = queryRepo.getByFileHash(hash);

        assertFalse(queryRes.isEmpty());
        assertEquals(queryRes.get(), correctResult);
    }

    @Test
    @Transactional
    void testResetTask() {
        // Set up values to test they are reset
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

        UUID secondTask = UUID.randomUUID();

        queryRepo.resetTask(hash, secondTask);
        
        Optional<ResultModel> resetResults = queryRepo.getByFileHash(hash);
        ResultModel.Pending expectedResults = new ResultModel.Pending(secondTask, hash);

        assertTrue(resetResults.isPresent());
        assertEquals(expectedResults, resetResults.get());
    }
}
