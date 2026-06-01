import magic

from src.config.settings import get_settings, init_settings
from src.config.object_storage import get_minio, init_minio, insert_file
from src.generated_sources import inference_pb2, inference_pb2_grpc
from src.celery_app import celery_app

import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


class InferenceService(inference_pb2_grpc.InferenceServiceServicer):
    def StreamFile(self, request_iterator, context):
        # Connect to minio
        init_settings()
        init_minio(get_settings().minio_config())

        print(get_minio())
        
        def get_extension(file_bytes: bytes) -> str:
            """
            Gets the file mimetype of the given bytearray and converts it to the file suffix.
            Assumes the file is a valid audio file
            """
            mime = magic.from_buffer(file_bytes[:2048], mime=True)
            return {
                'audio/mpeg': '.mp3',
                'audio/wav': '.wav',
                'audio/flac': '.flac',
                'audio/ogg': '.ogg'
            }.get(mime, '.mp3')
        chunks = []
        task_id = None
        logger.info(f"Starting Stream")
        file_hash = None

        # Get streamed file chunks to combine to the overall bytes
        for chunk in request_iterator:
            task_id = chunk.task_id
            file_hash = chunk.file_hash
            chunks.append(chunk.chunk)
        file_bytes = b''.join(chunks)

        # Store file bytes to minio
        object_name = f"{file_hash}{get_extension(file_bytes)}"
        bucket_name = "audio-files"
        insert_file(bucket_name, object_name, file_bytes)

        # Apply async task with the given task_id
        celery_app.send_task(name="src.tasks.inference_task.inference_task", args=(task_id, file_hash, bucket_name, object_name), task_id=task_id)

        return inference_pb2.EnqueueResponse(message='PENDING')