package dylanlederman.ai_genre.config;

import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandlerConfig {
    @ExceptionHandler(BadSqlGrammarException.class)
    public ResponseEntity<?> handleBadGrammar(
        BadSqlGrammarException ex
    ) {
        ex.printStackTrace();
        log.error("Bad Sql Error: " + ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("error", "An Internal Server Error Occurred"));
    }

    @ExceptionHandler(EmptyResultDataAccessException.class)
    public ResponseEntity<?> handleEmptyResult(
        EmptyResultDataAccessException ex
    ) {
        ex.printStackTrace();
        log.error("Empty Result Error: " + ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(Map.of("error", "Resource Not Found"));
    }

    @ExceptionHandler(IncorrectResultSizeDataAccessException.class)
    public ResponseEntity<?> handleIncorrectSizeResult(
        IncorrectResultSizeDataAccessException ex
    ) {
        ex.printStackTrace();
        log.error("Incorrect Result Size Error: " + ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("error", "An Internal Server Error Occurred"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> handleDataIntegrityViolation(
        DataIntegrityViolationException ex
    ) {
        ex.printStackTrace();
        log.error("Data Integrity Violation: " + ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(Map.of("error", "Given Value Already Exists"));
    }

    @ExceptionHandler(QueryTimeoutException.class)
    public ResponseEntity<?> handleQueryTimeout(
        QueryTimeoutException ex
    ) {
        ex.printStackTrace();
        log.error("Query Timeout: " + ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("error", "An Internal Server Error Occurred"));
    }

    @ExceptionHandler(CannotGetJdbcConnectionException.class)
    public ResponseEntity<?> handleCannotConnect(
        CannotGetJdbcConnectionException ex
    ) {
        ex.printStackTrace();
        log.error("Cannot Get Jdbc Connection: " + ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("error", "An Internal Server Error Occurred"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleDataAccess(
        Exception ex
    ) {
        ex.printStackTrace();
        log.error("Exception Occurred: " + ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("error", "An Internal Server Error Occurred"));
    }
}
