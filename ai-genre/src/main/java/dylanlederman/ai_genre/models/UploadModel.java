package dylanlederman.ai_genre.models;

import java.util.Map;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadModel {
    @Size(min=64, max=64)
    private String fileHash;
    private Map<String, String> fileMetadata;
}
