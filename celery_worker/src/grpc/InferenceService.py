from src.generated_sources import inference_pb2, inference_pb2_grpc
from src.celery_app import celery_app


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

        # Apply async task with the given task_id
        celery_app.send_task(name="inference_task", args=(task_id, file_hash, temp_file), task_id=task_id)

        return inference_pb2.EnqueueResponse(message='PENDING')