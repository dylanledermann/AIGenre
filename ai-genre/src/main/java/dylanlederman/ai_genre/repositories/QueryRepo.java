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
import dylanlederman.ai_genre.models.ResultModel;
import dylanlederman.ai_genre.models.UploadModel;
import jakarta.validation.Valid;
import tools.jackson.core.type.TypeReference;
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
        return (rs, rowNum) -> ResultModel.builder()
            .taskId((UUID) rs.getObject("task_id"))
            .status(rs.getString("status"))
            .result(objectMapper.readValue(rs.getString("result"), new TypeReference<Map<String, String>>(){}))
            .build();
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
            SELECT task_id, status, result
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
