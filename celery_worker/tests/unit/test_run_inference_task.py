import json
import os
from pathlib import Path

from celery_worker.src.config.settings import init_settings
from src.tasks.inference_task import inference_task
import pytest
from unittest.mock import ANY, MagicMock, patch
import numpy as np

# Constants
FILE_BYTES_INPUT=b'sampled'
RUN_INPUT=np.array([1, 2, 3])
TASK_ID = 'task-123'
FILE_HASH = 'file-hash-abc'

@pytest.fixture(scope='module', autouse=True)
def start_settings():
    init_settings()

@pytest.fixture
def mock_broker():
    broker = MagicMock()
    with patch('src.tasks.inference_task.get_backend', return_value=broker):
        yield broker

@pytest.fixture
def mock_db():
    with patch('src.tasks.inference_task.query_audio_results_by_sample_hash') as mock_audio_query, \
        patch('src.tasks.inference_task.update_task_status') as mock_update:
        yield {
            'query_audio_results': mock_audio_query,
            'update_status': mock_update
        }

@pytest.fixture
def mock_inference():
    with patch('src.tasks.inference_task.sample_file_bytes', return_value=FILE_BYTES_INPUT) as mock_sample, \
        patch('src.tasks.inference_task.get_audio_hash', return_value='audio-hash-abc') as mock_hash, \
        patch('src.tasks.inference_task.run_analysis', return_value=('Rock', '0.95')) as mock_run, \
        patch('src.tasks.inference_task.mp3_to_spectrogram', return_value=(RUN_INPUT)) as mock_spect:
        yield {
            'sample': mock_sample,
            'hash': mock_hash,
            'run': mock_run,
            'spect': mock_spect
        }

@pytest.fixture
def mock_create_file():
    temp_path = 'file'
    with open(temp_path, 'wb') as file:
        file.write(temp_path.encode())

    yield temp_path

    if Path(temp_path).exists():
        os.remove(temp_path)

class TestInferenceTaskFileNotFound:

    def test_publishes_failed_status(self, mock_broker, mock_db, mock_inference):

        inference_task.apply(kwargs={'task_id': TASK_ID, 'file_hash': FILE_HASH, 'file_path': "some path"})

        published = json.loads(mock_broker.publish.call_args[0][1])

        # Make sure status is updated and published, then the task ends
        mock_db['update_status'].assert_called_once_with(TASK_ID, 'FAILED', error=ANY)
        mock_inference['run'].assert_not_called()
        assert published['task_id'] == TASK_ID
        assert published['status'] == 'FAILED'


class TestInferenceTaskCacheHit:
    def test_publishes_cached_result(self, mock_broker, mock_db, mock_inference, mock_create_file):
        mock_db['query_audio_results'].return_value = {
            'status': 'COMPLETE',
            'results': {'genre': 'Rock', 'accuracy': '0.95'}
        }

        inference_task.apply(kwargs={'task_id': TASK_ID, 'file_hash': FILE_HASH, 'file_path': mock_create_file})

        # Check published with correct values
        published = json.loads(mock_broker.publish.call_args[0][1])
        assert published['task_id'] == TASK_ID
        assert published['results']['genre'] == 'Rock'

        # Check inference and update not ran, since using cached values
        mock_inference['run'].assert_not_called()

class TestInferenceTaskFullRun:
    def test_publishes_processing_then_complete(self, mock_broker, mock_db, mock_inference, mock_create_file):
        mock_db['query_audio_results'].return_value = None

        inference_task.apply(kwargs={'task_id': TASK_ID, 'file_hash': FILE_HASH, 'file_path': mock_create_file})

        # Check broker publish
        calls = [json.loads(c[0][1]) for c in mock_broker.publish.call_args_list]
        assert calls[0]['status'] == 'PROCESSING'
        assert calls[1]['results']['genre'] == 'Rock'
        assert calls[1]['results']['accuracy'] == '0.95'

        # Check db status update
        calls = mock_db['update_status'].call_args_list
        assert calls[0][0] == (TASK_ID, 'PROCESSING')
        assert calls[1][0] == (TASK_ID, 'COMPLETE')

        # Check spect ran
        mock_inference['spect'].assert_called_once_with(FILE_BYTES_INPUT)

        # Check Inference ran (Have to compare args, since it takes a numpy array)
        np.testing.assert_array_equal(RUN_INPUT, mock_inference['run'].call_args[0][0])

        # Check publishes to the correct channel
        for call in mock_broker.publish.call_args_list:
            assert call[0][0] == 'celery:results'