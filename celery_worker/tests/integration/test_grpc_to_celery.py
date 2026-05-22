import threading
import time
import uuid

import pytest

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
@pytest.fixture(scope='module')
def messages():
    return []

@pytest.fixture(scope='module')
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

    def wait_for_messages(self, messages, count=1, timeout=30):
        start = time.time()
        while len(messages) < count:
            if time.time() - start > timeout:
                pytest.fail(
                    f"Timed out waiting for {count} messages, "
                    f"only received {len(messages)}"
                )
            time.sleep(0.1)
        return messages
    def test_stream_publishes_result(
        self, 
        grpc_stub, 
        celery_worker, 
        mock_backend, 
        init_redis_sub,
        messages,
    ):
        task_id = str(uuid.uuid4())
        file_hash = "a"*64
    
        response = grpc_stub.StreamFile(
            self.generate_chunks(task_id, file_hash, TEST_AUDIO_PATH)
        )
        assert response is not None
        start = time.time()
        messages = self.wait_for_messages(messages, 2)
        assert len(messages) == 2