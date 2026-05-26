import json
import threading
import time
import uuid

import pytest

from celery_worker.src.config.settings import get_settings, init_settings
from celery_worker.src.repo.repo import get_db, init_pool
from celery_worker.src.service.helpers import get_audio_hash, sample_file_bytes
from src.generated_sources.inference_pb2 import FileChunk

channel = "celery:results"
TEST_AUDIO_PATH = 'tests/resources/sample_mp3.mp3'

# ------------ Grpc ------------
@pytest.fixture(scope='module')
def grpc_add_to_server():
    from src.generated_sources.inference_pb2_grpc import add_InferenceServiceServicer_to_server

    return add_InferenceServiceServicer_to_server

@pytest.fixture(scope='module')
def grpc_servicer():
    from src.grpc.InferenceService import InferenceService

    return InferenceService()

@pytest.fixture(scope='module')
def grpc_stub_cls(grpc_channel):
    from src.generated_sources.inference_pb2_grpc import InferenceServiceStub

    return InferenceServiceStub

# ------------ Storage Fixtures ------------
@pytest.fixture(scope='function')
def messages():
    return []

@pytest.fixture(scope='function', autouse=True)
def reset_db():
    """
    Remove all values stored in the database.
    """
    init_settings()
    init_pool(get_settings().database_config())

    with get_db() as db:
        with db.cursor() as cursor:
            cursor.execute(
                "DELETE FROM uploads"
            )

@pytest.fixture(scope='function')
def init_redis_sub(redis_sub, messages):
    def subscribe():
        ps = redis_sub.pubsub()
        ps.subscribe(channel)
        for msg in ps.listen():
            if msg["type"] == "message":
                messages.append(msg["data"].decode("utf-8"))
        ps.close()

    thread  = threading.Thread(target=subscribe, daemon=True)
    thread.start()
    yield 
    thread.join(timeout=2)

class TestGrpcToCelery:
    def generate_chunks(self, task_id, file_hash, file_path, chunk_size=64*1024):
        with open(file_path, 'rb') as f:
            file_bytes = f.read()
        
        for i in range(0, len(file_bytes), chunk_size):
            chunk = file_bytes[i: i+chunk_size]
            is_last = i + chunk_size >= len(file_bytes)
            yield FileChunk(
                task_id=task_id,
                file_hash=file_hash,
                chunk=chunk,
                is_last=is_last
            )

    def wait_for_messages(self, messages, count=1, timeout=120):
        start = time.time()
        while len(messages) < count:
            if time.time() - start > timeout:
                pytest.fail(
                    f"Timed out waiting for {count} messages, "
                    f"only received {len(messages)}"
                )
            time.sleep(0.1)
        return messages
    
    def validate_message(self, message, task_id, file_hash):
        """
        Validate the messages are in the correct format.
        Also checks the fields are correct (task_id, file_hash)

        Args:
            task_id (str) the expected task id.
            file_hash (str) the expected file hash.
        """
        # Make sure the messages are dict
        assert isinstance(message, dict)
        assert message.get('task_id') == task_id
        assert message.get('file_hash') == file_hash
        assert message.get('status') in ['PROCESSING', 'COMPLETE', 'FAILED']
        if message.get('status') == 'COMPLETE':
            assert isinstance(message.get('results'), dict)
            assert message.get('results').get('genre') is not None
            assert message.get('results').get('accuracy') is not None
        elif message.get('status') == 'FAILED':
            assert message.get('error') is not None
    
    def test_stream_publishes_result(
        self, 
        grpc_stub, 
        celery_worker, 
        init_redis_sub,
        messages,
        reset_db,
    ):
        task_id = str(uuid.uuid4())
        file_hash = "a"*64

        # Run grpc stream
        response = grpc_stub.StreamFile(
            self.generate_chunks(task_id, file_hash, TEST_AUDIO_PATH)
        )

        # Some response (No error)
        assert response is not None
        # Wait for the expected number of messages
        messages = self.wait_for_messages(messages, 2)

        # Messages should be processing -> complete
        assert len(messages) == 2
        # All messages should be standardized format (id, hash, status)
        for message in messages:
            self.validate_message(json.loads(message), task_id, file_hash)
        
    def test_stream_cached_result(
        self,
        grpc_stub,
        celery_worker,
        init_redis_sub,
        messages,
        reset_db,
    ):
        task_id = str(uuid.uuid4())
        file_hash = "a" * 64

        # Get the file bytes -> convert to sampled bytes -> convert to sample hash -> store hash in db to be returned 
        with open(TEST_AUDIO_PATH, 'rb') as f:
            file_bytes = f.read()

        sampled_bytes = sample_file_bytes(file_bytes)
        sample_hash = get_audio_hash(sampled_bytes)

        # Insert the sample hash into the db
        with get_db() as db:
            with db.cursor() as cursor:
                cursor.execute("""
                    INSERT INTO uploads
                    (file_hash) VALUES (%s)
                """, (file_hash,))
                cursor.execute("""
                    INSERT INTO audio_results
                    (task_id, file_hash, sample_hash, status, result)
                    VALUES (%s::uuid, %s, %s, %s, %s)
                """, (task_id, file_hash, sample_hash, 'COMPLETE', json.dumps({'genre': 'someGenre', 'accuracy': 'someAccuracy'})))

        second_task_id = str(uuid.uuid4())
        second_file_hash = 'b' * 64
        
        # Run grpc stream, but with a fake file path
        response = grpc_stub.StreamFile(
            self.generate_chunks(second_task_id, second_file_hash, TEST_AUDIO_PATH)
        )

        # Make sure some response, then wait for messages
        assert response is not None
        messages = self.wait_for_messages(messages, 1)
        # Validate messages
        assert len(messages)
        for message in messages:
            self.validate_message(json.loads(message), second_task_id, second_file_hash)
        