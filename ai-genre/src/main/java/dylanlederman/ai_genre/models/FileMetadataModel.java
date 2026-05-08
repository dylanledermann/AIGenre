package dylanlederman.ai_genre.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileMetadataModel {
    private String fileName;
    private long fileSize;
    private String mimeType;
}
