package dylanlederman.ai_genre.Unit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;


import dylanlederman.ai_genre.config.SecurityConfig;
import dylanlederman.ai_genre.controllers.QueryController;
import dylanlederman.ai_genre.models.FileMetadataModel;
import dylanlederman.ai_genre.models.ResultModel;
import dylanlederman.ai_genre.proto.EnqueueResponse;
import dylanlederman.ai_genre.services.GrpcServiceStub;
import dylanlederman.ai_genre.services.QueryService;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(value=QueryController.class, properties="spring.celery.url=http://localhost:8000")
@Import({SecurityConfig.class})
@ImportAutoConfiguration(classes={
    JacksonAutoConfiguration.class,
    MockMvc.class
})
public class QueryControllerTest {
    @MockitoBean
    private QueryService queryService;
    @MockitoBean
    private GrpcServiceStub grpcClient;
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

    @BeforeEach
    // If exception is thrown something is wrong with the test, so just allow it to raise.
    void setUp() throws Exception {
        EnqueueResponse mockedResponse = Mockito.mock(EnqueueResponse.class);
        CompletableFuture<EnqueueResponse> mockedFuture = CompletableFuture.completedFuture(mockedResponse);
        when(grpcClient.buildTaskStub(anyString(), anyString(), any())).thenReturn(mockedFuture);
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
            ResultModel res = new ResultModel.Pending(UUID.randomUUID(), "fileHash");
            when(queryService.hashFile(fileBytes)).thenReturn(hash);
            when(queryService.checkHash(hash)).thenReturn(Optional.empty());
            when(queryService.createTask(hash)).thenReturn(res);
            
            FileMetadataModel metadata = new FileMetadataModel(
                file.getName(),
                file.getSize(),
                file.getContentType()
            );

            when(queryService.saveFile(hash, fileBytes, metadata)).thenReturn(true);

            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/query")
                .file(file)
            ).andExpect(status().isAccepted())
            .andExpect(jsonPath("$.taskId").value(res.taskId().toString()));
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

            ResultModel result = new ResultModel.Pending(UUID.randomUUID(), "fileHash");

            when(queryService.hashFile(fileBytes)).thenReturn(hash);
            when(queryService.checkHash(hash)).thenReturn(Optional.of(result));
            String response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/query")
                .file(file)
            ).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

            ResultModel responseMap = objectMapper.readValue(response, new TypeReference<ResultModel.Pending>(){});
            
            assertEquals(
                result,
                responseMap
            );
        });
    }

    @Test
    void testTaskRemovedOnGrpcError () {
        assertDoesNotThrow(() -> {
            // Setup values
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

            FileMetadataModel metadata = new FileMetadataModel(
                file.getName(),
                file.getSize(),
                file.getContentType()
            );

            ResultModel result = new ResultModel.Pending(UUID.randomUUID(), "fileHash");

            // Set mocked outputs
            when(queryService.hashFile(fileBytes)).thenReturn(hash);
            when(queryService.checkHash(hash)).thenReturn(Optional.empty());
            when(queryService.saveFile(hash, fileBytes, metadata)).thenReturn(true);
            when(queryService.createTask(hash)).thenReturn(result);
            // Throw on grpc stub to test the task is deleted
            when(grpcClient.buildTaskStub(anyString(), anyString(), any())).thenThrow(new Exception());
            
            // get and verify response
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/query")
                .file(file)
            ).andExpect(status().isInternalServerError());
            
            verify(queryService).deleteTask(result.taskId());
        });
    }
}
