package dylanlederman.ai_genre.models;

import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ResultModel {
    private UUID taskId;
    @Pattern(regexp="PENDING|PROCESSING|COMPLETE|FAILURE")
    private String status;
    private Map<String, String> result;
}
