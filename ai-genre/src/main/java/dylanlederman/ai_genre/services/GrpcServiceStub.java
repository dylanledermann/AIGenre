package dylanlederman.ai_genre.services;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Service;

import com.google.protobuf.ByteString;

import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import dylanlederman.ai_genre.proto.EnqueueResponse;
import dylanlederman.ai_genre.proto.FileChunk;
import dylanlederman.ai_genre.proto.InferenceServiceGrpc;

@Service
@Slf4j
public class GrpcServiceStub {
    private InferenceServiceGrpc.InferenceServiceStub asyncStub;

    public GrpcServiceStub(GrpcChannelFactory channels) {
        // Channel is specificied in application.yaml spring.grpc.client.channels, must match the channel name
        this.asyncStub = InferenceServiceGrpc.newStub(channels.createChannel("grpc-server"));
    }

    public CompletableFuture<EnqueueResponse> buildTaskStub(String taskId, String fileHash, byte[] fileBytes) throws Exception {
        CompletableFuture<EnqueueResponse> future = new CompletableFuture<>();

        // Stream file over grpc, log if an error occurs
        StreamObserver<EnqueueResponse> responseObserver = new StreamObserver<>() {
            @Override
            public void onNext(EnqueueResponse response) {
                future.complete(response);
            }

            @Override
            public void onError(Throwable t) {
                log.error("Error Occurred in GRPC", t.getMessage());
                future.completeExceptionally(t);
            }

            @Override
            public void onCompleted() {
            }
        };

        StreamObserver<FileChunk> requestObserver = asyncStub.streamFile(responseObserver);

        int chunkSize = 64 * 1024; // 64 kb
        for (int i = 0; i < fileBytes.length; i += chunkSize) {
            byte[] chunk = Arrays.copyOfRange(fileBytes, i, Math.min(fileBytes.length, i + chunkSize));
            requestObserver.onNext(
                    FileChunk.newBuilder()
                            .setTaskId(taskId)
                            .setFileHash(fileHash)
                            .setChunk(ByteString.copyFrom(chunk))
                            .setIsLast(i + chunkSize >= fileBytes.length)
                            .build());
        }

        requestObserver.onCompleted();
        return future;
    }
}