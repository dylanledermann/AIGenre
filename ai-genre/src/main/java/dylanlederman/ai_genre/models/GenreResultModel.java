package dylanlederman.ai_genre.models;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GenreResultModel {
    private String genre;
    private String accuracy;
}