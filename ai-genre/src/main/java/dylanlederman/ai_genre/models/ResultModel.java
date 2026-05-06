package dylanlederman.ai_genre.models;

import java.util.Map;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ResultModel {
    @Size(min=64, max=64)
    private String taskId;
    @Pattern(regexp="PENDING|PROCESSING|COMPLETE|FAILURE")
    private String status;
    private Map<String, String> result;
}
