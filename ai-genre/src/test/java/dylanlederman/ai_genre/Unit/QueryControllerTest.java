package dylanlederman.ai_genre.Unit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.hamcrest.Matchers.matchesPattern;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.client.RestClient;

import dylanlederman.ai_genre.config.SecurityConfig;
import dylanlederman.ai_genre.controllers.QueryController;
import dylanlederman.ai_genre.models.FileMetadataModel;
import dylanlederman.ai_genre.services.QueryService;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(value=QueryController.class, properties="spring.celery.url=http://localhost:8000")
@Import({SecurityConfig.class})
@ImportAutoConfiguration(classes={
    ObjectMapper.class
})
public class QueryControllerTest {
    @MockitoBean
    private QueryService queryService;
    @MockitoBean
    private RestClient restClient;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Value("classpath:TestFiles/sample_image.jpg")
    Resource sampleImage;
    @Value("classpath:TestFiles/sample_mp3.mp3")
    Resource sampleMp3;
    @Value("${spring.celery.url}") 
    private String workerUrl;

    private RestClient.RequestBodyUriSpec requestBodyUriSpec = Mockito.mock(RestClient.RequestBodyUriSpec.class);
    private RestClient.RequestBodySpec requestBodySpec = Mockito.mock(RestClient.RequestBodySpec.class);
    private RestClient.ResponseSpec responseSpec = Mockito.mock(RestClient.ResponseSpec.class);

    @BeforeEach
    void setUp() {
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(anyMap())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(ResponseEntity.ok().build());
    }

    @Test
    void testInvalidMp3Format() {
        assertDoesNotThrow(() -> {
            // Null content type
            MockMultipartFile file = new MockMultipartFile(
                "file",
                sampleImage.getContentAsByteArray()
            );
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/query")
                .file(file)
            ).andExpect(status().isBadRequest());

            // Invalid content type
            String contentType = MediaTypeFactory.getMediaType(sampleImage.getFilename())
                .orElse(MediaType.APPLICATION_OCTET_STREAM).toString();
            file = new MockMultipartFile(
                "file",
                sampleImage.getFilename(),
                contentType,
                sampleImage.getContentAsByteArray()
            );
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/query")
                .file(file)
            ).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value(String.format("%s file types not allowed", file.getContentType())));
        });
    }

    @Test
    void testServiceFails() {
        assertDoesNotThrow(() -> {
            String hash = "a".repeat(64);
            String contentType = MediaTypeFactory.getMediaType(sampleMp3.getFilename())
                .orElse(MediaType.APPLICATION_OCTET_STREAM).toString();
            byte[] fileBytes = sampleMp3.getContentAsByteArray();
            MockMultipartFile file = new MockMultipartFile(
                "file",
                sampleMp3.getFilename(),
                contentType,
                fileBytes
            );
            when(queryService.hashFile(fileBytes)).thenReturn(hash);
            when(queryService.checkHash(hash)).thenReturn(Optional.empty());
            
            FileMetadataModel metadata = new FileMetadataModel(
                file.getName(),
                file.getSize(),
                file.getContentType()
            );

            when(queryService.saveFile(hash, fileBytes, metadata)).thenReturn(false);

            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/query")
                .file(file)  
            ).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Invalid file"));
        });
    }

    @Test
    void testMp3FormatNotSaved() {
        assertDoesNotThrow(() -> {
            String hash = "a".repeat(64);
            String contentType = MediaTypeFactory.getMediaType(sampleMp3.getFilename())
                .orElse(MediaType.APPLICATION_OCTET_STREAM).toString();
            byte[] fileBytes = sampleMp3.getContentAsByteArray();
            MockMultipartFile file = new MockMultipartFile(
                "file",
                sampleMp3.getFilename(),
                contentType,
                fileBytes
            );
            when(queryService.hashFile(fileBytes)).thenReturn(hash);
            when(queryService.checkHash(hash)).thenReturn(Optional.empty());
            when(queryService.createTask(eq(hash), any())).thenAnswer(invocation -> {
                // Return second arg (taskId)
                return invocation.getArgument(1);
            });
            
            FileMetadataModel metadata = new FileMetadataModel(
                file.getName(),
                file.getSize(),
                file.getContentType()
            );

            when(queryService.saveFile(hash, fileBytes, metadata)).thenReturn(true);

            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/query")
                .file(file)
            ).andExpect(status().isAccepted())
            .andExpect(jsonPath("$.taskId").value(matchesPattern("^[0-9a-f]{8}\\-[0-9a-f]{4}\\-[0-9a-f]{4}\\-[0-9a-f]{4}\\-[0-9a-f]{12}$")));
        });
    }

    @Test
    void testMp3FormatSaved() {
        assertDoesNotThrow(() -> {
            String hash = "a".repeat(64);
            String contentType = MediaTypeFactory.getMediaType(sampleMp3.getFilename())
                .orElse(MediaType.APPLICATION_OCTET_STREAM).toString();
            byte[] fileBytes = sampleMp3.getContentAsByteArray();

            MockMultipartFile file = new MockMultipartFile(
                "file",
                sampleMp3.getFilename(),
                contentType,
                fileBytes
            );

            Map<String, Object> result = Map.of(
                "key", "val"
            );

            when(queryService.hashFile(fileBytes)).thenReturn(hash);
            when(queryService.checkHash(hash)).thenReturn(Optional.of(result));
            String response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/query")
                .file(file)
            ).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

            Map<String, Object> responseMap = objectMapper.readValue(response, new TypeReference<Map<String, Object>>(){});
            
            assertEquals(
                result,
                responseMap
            );
        });
    }
}
