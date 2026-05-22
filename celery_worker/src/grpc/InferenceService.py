from pathlib import Path
import magic

from src.generated_sources import inference_pb2, inference_pb2_grpc
from src.celery_app import celery_app


class InferenceService(inference_pb2_grpc.InferenceServiceServicer):
    def StreamFile(self, request_iterator, context):
        def get_extension(file_bytes: bytearray) -> dict:
            mime = magic.from_buffer(file_bytes[:2048], mime=True)
            return {
                'audio/mpeg': '.mp3',
                'audio/wav': '.wav',
                'audio/flac': '.flac',
                'audio/ogg': '.ogg'
            }.get(mime, '.mp3')
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
        temp_path = f"./src//tmp/{file_hash}{get_extension(file_bytes)}"
        # Create file if dne
        file_path = Path(temp_path)
        file_path.parent.mkdir(parents=True, exist_ok=True)

        # Write streamed bytes to file
        with open(temp_path, 'wb') as temp_file:
            temp_file.write(file_bytes)

        # Apply async task with the given task_id
        celery_app.send_task(name="inference_task", args=(task_id, file_hash, temp_path), task_id=task_id)

        return inference_pb2.EnqueueResponse(message='PENDING')