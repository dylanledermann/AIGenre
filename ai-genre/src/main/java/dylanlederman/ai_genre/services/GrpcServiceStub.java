package dylanlederman.ai_genre.services;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Service;

import com.google.protobuf.ByteString;

import io.grpc.stub.StreamObserver;
import dylanlederman.ai_genre.proto.EnqueueResponse;
import dylanlederman.ai_genre.proto.FileChunk;
import dylanlederman.ai_genre.proto.InferenceServiceGrpc;

@Service
public class GrpcServiceStub {
    private InferenceServiceGrpc.InferenceServiceStub asyncStub;

    public GrpcServiceStub(GrpcChannelFactory channels) {
        this.asyncStub = InferenceServiceGrpc.newStub(channels.createChannel("celery-worker"));
    }

    public CompletableFuture<EnqueueResponse> buildTaskStub(String taskId, String fileHash, byte[] fileBytes) {
        CompletableFuture<EnqueueResponse> future = new CompletableFuture<>();

        StreamObserver<EnqueueResponse> responseObserver = new StreamObserver<>() {
            @Override
            public void onNext(EnqueueResponse response) {
                future.complete(response);
            }

            @Override
            public void onError(Throwable t) {
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