package dylanlederman.ai_genre.repositories;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import dylanlederman.ai_genre.models.FileModel;
import dylanlederman.ai_genre.models.GenreResultModel;
import dylanlederman.ai_genre.models.ResultModel;
import dylanlederman.ai_genre.models.UploadModel;
import jakarta.validation.Valid;
import tools.jackson.databind.ObjectMapper;

@Repository
public class QueryRepo {
    @Autowired
    private NamedParameterJdbcTemplate namedJdbcTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    private RowMapper<ResultModel> resultRowMapper() {
        return (rs, rowNum) -> switch(rs.getString("status")) {
            case "PENDING": yield new ResultModel.Pending(UUID.fromString(rs.getString("task_id")), rs.getString("file_hash"));
            case "PROCESSING": yield new ResultModel.Processing(UUID.fromString(rs.getString("task_id")), rs.getString("file_hash"));
            case "COMPLETE": yield new ResultModel.Complete(UUID.fromString(rs.getString("task_id")), rs.getString("file_hash"), objectMapper.readValue(rs.getString("result"), GenreResultModel.class));
            case "FAILED": yield new ResultModel.Failed(UUID.fromString(rs.getString("task_id")), rs.getString("file_hash"), rs.getString("error"));
            default: yield new ResultModel.Failed(UUID.fromString(rs.getString("task_id")), rs.getString("file_hash"), "Invalid Status: " + rs.getString("status"));
        };
    }

    @Transactional
    public void insertFile(@Valid UploadModel upload, @Valid FileModel file) {
        String uploadInsertQuery = """
            INSERT INTO uploads (file_hash, file_metadata) 
            VALUES(:fileHash, :fileMetadata::jsonb)
        """;
        String fileInsertQuery = """
            INSERT INTO files (file_hash, file_bytes)
            VALUES(:fileHash, :fileBytes)        
        """;

        namedJdbcTemplate.update(uploadInsertQuery, Map.of(
            "fileHash", upload.getFileHash(),
            "fileMetadata", objectMapper.writeValueAsString(upload.getFileMetadata())
        ));
        namedJdbcTemplate.update(fileInsertQuery, new BeanPropertySqlParameterSource(file));
    }

    public Optional<ResultModel> getByFileHash(String file_hash) {
        String getQuery = """
            SELECT task_id, file_hash, status, result, error
            FROM audio_results
            WHERE file_hash = ?
        """;
        try{
            ResultModel res = jdbcTemplate.queryForObject(getQuery, resultRowMapper(), file_hash);
            return Optional.of(res);
        } catch (IncorrectResultSizeDataAccessException e) {
            return Optional.empty();
        }
    }
}
