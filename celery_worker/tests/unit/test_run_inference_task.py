import json

from tasks.inference_task import inference_task
import pytest
from unittest.mock import ANY, MagicMock, patch

@pytest.fixture
def mock_broker():
    broker = MagicMock()
    with patch('tasks.inference_task.get_broker', return_value=broker):
        yield broker

@pytest.fixture
def mock_db():
    with patch('tasks.inference_task.query_uploads_by_hash') as mock_query, \
        patch('tasks.inference_task.query_audio_results_by_sample_hash') as mock_audio_query, \
        patch('tasks.inference_task.update_task_status') as mock_update:
        yield {
            'query_uploads': mock_query,
            'query_audio_results': mock_audio_query,
            'update_status': mock_update
        }

@pytest.fixture
def mock_inference():
    with patch('tasks.inference_task.sample_file_bytes', return_value=b'sampled') as mock_sample, \
        patch('tasks.inference_task.get_audio_hash', return_value='audio-hash-abc') as mock_hash, \
        patch('tasks.inference_task.run_analysis', return_value=('Rock', 0.95)) as mock_run:
        yield {
            'sample': mock_sample,
            'hash': mock_hash,
            'run': mock_run
        }

TASK_ID = 'task-123'
FILE_HASH = 'file-hash-abc'

class TestInferenceTaskFileNotFound:

    def test_publishes_failed_status(self, mock_broker, mock_db, mock_inference):
        mock_db['query_uploads'].return_value = None

        inference_task.apply(kwargs={'taskId': TASK_ID, 'fileHash': FILE_HASH})

        mock_db['query_uploads'].assert_called_once()
        published = json.loads(mock_broker.publish.call_args[0][1])
        assert published['taskId'] == TASK_ID
        assert published['status'] == 'FAILED'

    def test_updates_task_status_failed(self, mock_broker, mock_db, mock_inference):
        mock_db['query_uploads'].return_value = None

        inference_task.apply(kwargs={'taskId': TASK_ID, 'fileHash': FILE_HASH})

        mock_db['update_status'].assert_called_once_with(TASK_ID, 'FAILED', error=ANY)

    def test_does_not_run_inference(self, mock_broker, mock_db, mock_inference):
        mock_db['query_uploads'].return_value = None

        inference_task.apply(kwargs={'taskId': TASK_ID, 'fileHash': FILE_HASH})

        mock_inference['run'].assert_not_called()


class TestInferenceTaskCacheHit:
    def test_publishes_cached_result(self, mock_broker, mock_db, mock_inference):
        mock_db['query_uploads'].return_value = b'file-bytes'
        mock_db['query_audio_results'].return_value = {
            'status': 'COMPLETE',
            'results': {'genre': 'Rock', 'accuracy': 0.95}
        }

        inference_task.apply(kwargs={'taskId': TASK_ID, 'fileHash': FILE_HASH})

        published = json.loads(mock_broker.publish.call_args[0][1])
        assert published['taskId'] == TASK_ID
        assert published['results']['genre'] == 'Rock'

    def test_does_not_run_inference(self, mock_broker, mock_db, mock_inference):
        mock_db['query_uploads'].return_value = b'file-bytes'
        mock_db['query_audio_results'].return_value = {
            'status': 'COMPLETE',
            'results': {'genre': 'Rock', 'accuracy': 0.95}
        }

        inference_task.apply(kwargs={'taskId': TASK_ID, 'fileHash': FILE_HASH})

        mock_inference['run'].assert_not_called()

    def test_does_not_update_task_status(self, mock_broker, mock_db, mock_inference):
        mock_db['query_uploads'].return_value = b'file-bytes'
        mock_db['query_audio_results'].return_value = {
            'status': 'COMPLETE',
            'results': {'genre': 'Rock', 'accuracy': 0.95}
        }

        inference_task.apply(kwargs={'taskId': TASK_ID, 'fileHash': FILE_HASH})

        mock_db['update_status'].assert_not_called()


class TestInferenceTaskFullRun:
    def test_publishes_processing_then_complete(self, mock_broker, mock_db, mock_inference):
        mock_db['query_uploads'].return_value = b'file-bytes'
        mock_db['query_audio_results'].return_value = None

        inference_task.apply(kwargs={'taskId': TASK_ID, 'fileHash': FILE_HASH})

        calls = [json.loads(c[0][1]) for c in mock_broker.publish.call_args_list]
        assert calls[0]['status'] == 'PROCESSING'
        assert calls[1]['results']['genre'] == 'Rock'
        assert calls[1]['results']['accuracy'] == 0.95

    def test_updates_status_processing_then_complete(self, mock_broker, mock_db, mock_inference):
        mock_db['query_uploads'].return_value = b'file-bytes'
        mock_db['query_audio_results'].return_value = None

        inference_task.apply(kwargs={'taskId': TASK_ID, 'fileHash': FILE_HASH})

        calls = mock_db['update_status'].call_args_list
        assert calls[0][0] == (TASK_ID, 'PROCESSING')
        assert calls[1][0] == (TASK_ID, 'COMPLETE')

    def test_run_analysis_called_with_sampled_bytes(self, mock_broker, mock_db, mock_inference):
        mock_db['query_uploads'].return_value = b'file-bytes'
        mock_db['query_audio_results'].return_value = None

        inference_task.apply(kwargs={'taskId': TASK_ID, 'fileHash': FILE_HASH})

        mock_inference['run'].assert_called_once_with(b'sampled')

    def test_publishes_correct_channel(self, mock_broker, mock_db, mock_inference):
        mock_db['query_uploads'].return_value = b'file-bytes'
        mock_db['query_audio_results'].return_value = None

        inference_task.apply(kwargs={'taskId': TASK_ID, 'fileHash': FILE_HASH})

        for call in mock_broker.publish.call_args_list:
            assert call[0][0] == 'celery:results'