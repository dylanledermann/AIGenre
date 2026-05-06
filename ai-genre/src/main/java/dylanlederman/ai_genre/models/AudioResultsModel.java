package dylanlederman.ai_genre.models;

import java.util.Map;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AudioResultsModel {
    @Size(min=64, max=64)
    private String sampleHash;
    @Size(min=64, max=64)
    private String fileHash;
    @Size(min=64, max=64)
    private String taskId;
    @Pattern(regexp="PENDING|PROCESSING|COMPLETE|FAILURE")
    private String status;
    private Map<String, String> result;
    private int windowStartSample;
    private int sampleRate;
}
