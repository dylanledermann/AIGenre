package dylanlederman.ai_genre.models;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadModel {
    @NotNull @Size(min=64, max=64)
    private String fileHash;
    private FileMetadataModel fileMetadata;
}