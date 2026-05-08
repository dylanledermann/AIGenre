package dylanlederman.ai_genre.Unit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.postgresql.util.PGobject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import dylanlederman.ai_genre.TestContainers.BaseTestContainers;
import dylanlederman.ai_genre.config.DataSourceConfig;
import dylanlederman.ai_genre.models.FileMetadataModel;
import dylanlederman.ai_genre.models.FileModel;
import dylanlederman.ai_genre.models.ResultModel;
import dylanlederman.ai_genre.models.UploadModel;
import dylanlederman.ai_genre.repositories.QueryRepo;
import tools.jackson.databind.ObjectMapper;


@SpringBootTest(classes={
    DataSourceConfig.class,
    QueryRepo.class
})
@EnableAutoConfiguration
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
        FileModel file = new FileModel(hash, hash.getBytes());
        queryRepo.insertFile(upload, file);
        assertEquals(queryRepo.getByFileHash(hash), Optional.empty());
        String uploadQuery = """
            SELECT * FROM uploads WHERE file_hash = ?
        """;
        String fileQuery = """
            SELECT * FROM files WHERE file_hash = ?
        """;
        Map<String, Object> uploadRes = jdbcTemplate.queryForMap(uploadQuery, hash);
        Map<String, Object> fileRes = jdbcTemplate.queryForMap(fileQuery, hash);
        assertEquals((String) uploadRes.get("file_hash"), hash);
        assertEquals(objectMapper.readValue(((PGobject) uploadRes.get("file_metadata")).getValue(), FileMetadataModel.class), metadata);
        assertEquals((String) fileRes.get("file_hash"), hash);
        assertArrayEquals((byte[]) fileRes.get("file_bytes"), hash.getBytes());
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
        FileModel file = new FileModel(hash, hash.getBytes());

        queryRepo.insertFile(upload, file);

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
}
