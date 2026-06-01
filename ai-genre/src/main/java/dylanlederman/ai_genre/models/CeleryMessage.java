package dylanlederman.ai_genre.models;

import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CeleryMessage {
    @NotNull
    private UUID taskId;
    @NotNull @Size(min=64, max=64)
    private String fileHash;
    @NotNull @Pattern(regexp="PENDING|PROCESSING|COMPLETE|FAILED")
    private String status;
    private Map<String, String> results;
    private String error;

    public ResultModel toResultModel() {
        return switch(status) {
            case "PENDING" -> new ResultModel.Pending(taskId, fileHash);
            case "PROCESSING" -> new ResultModel.Processing(taskId, fileHash);
            case "COMPLETE" -> {
                if (results == null) throw new IllegalArgumentException("COMPLETE status missing results for taskId: " + taskId);
                yield new ResultModel.Complete(
                    taskId,
                    fileHash,
                    new GenreResultModel(
                        results.get("genre"), 
                        results.get("accuracy")
                    )
                );
            } case "FAILED" -> {
                if (error == null) error = "Internal Server Error";
                yield new ResultModel.Failed(taskId, fileHash, error);
            } default -> throw new IllegalArgumentException("Unknown state: " + status + "for taskId: " + taskId);
        };
    }
}
