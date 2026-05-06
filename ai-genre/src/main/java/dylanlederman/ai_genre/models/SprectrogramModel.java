package dylanlederman.ai_genre.models;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SprectrogramModel {
    @Size(min=64, max=64)
    private String sampleHash;
    private int spectrogramRows;
    private int spectrogramCols;
    private byte[] spectrogram;   
}
