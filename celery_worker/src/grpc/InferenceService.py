from celery_worker.src.generated_sources.proto import inference_pb2, inference_pb2_grpc
from celery_worker.src.tasks.inference_task import inference_task


class InferenceService(inference_pb2_grpc.InferenceServiceServicer):
    def StreamFile(self, request_iterator, context):
        chunks = []
        task_id = None
        file_hash = None

        # Get streamed file chunks to combine to the overall bytes
        for chunk in request_iterator:
            task_id = chunk.task_id
            file_hash = chunk.file_hash
            chunks.append(chunk.chunk)
        file_bytes = b''.join(chunks)

        # Store file bytes as a temp file and send the path to the task
        temp_path = f"./tmp/{file_hash}"
        with open(temp_path, 'wb') as temp_file:
            temp_file.write(file_bytes)

        inference_task.delay(task_id, file_hash, temp_file)

        return inference_pb2.EnqueueResponse(message='PENDING')