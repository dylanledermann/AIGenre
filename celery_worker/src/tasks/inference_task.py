import json
import os
from pathlib import Path

import celery

from src.config.object_storage import get_file
from src.config.backend import get_backend
from src.celery_app import celery_app
from src.repo.repo import update_task_status, query_audio_results_by_sample_hash
from src.service.helpers import mp3_to_spectrogram, sample_file_bytes, get_audio_hash, run_analysis\

class BaseClassWithLogging(celery.Task):
    def on_failure(self, exc, task_id, args, kwargs, einfo):
        # Log error and send failure result to listeners
        print(args, kwargs)
        update_task_status(task_id, 'FAILED', error = "Task Error: task failed, task_id={task_id}")
        get_backend().publish("celery:results", json.dumps({
            'task_id': task_id,
            'status': 'FAILED',
            'error': f"Task Error: task failed, task_id={task_id}"
        }))
        # Call super
        super().on_failure(exc, task_id, args, kwargs, einfo)

@celery_app.task(
    base=BaseClassWithLogging,
    bind=True,
    time_limit=300,
    retry_backoff=True,
    retry_kwargs={'max_retries': 3},
)
def inference_task(self, task_id: str, file_hash: str, bucket: str, object_name: str):
    _backend = get_backend()
    # get file bytes from MinIO storage, log failure if DNE
    file_location = f"./src/tmp/{object_name}"
    file_created = get_file(bucket, object_name, file_location)
    if not file_created:
        update_task_status(task_id, 'FAILED', error = f"Task Error: file not found, task_id={task_id}, bucket={bucket}, object={object_name}")
        _backend.publish("celery:results", json.dumps({
            'task_id': task_id,
            'file_hash': file_hash,
            'status': 'FAILED',
            'error': f"Task Error: file not found in database, task_id={task_id}"
        }))
        return
    
    # Read the file into memory, if an error occurs, the file should be removed still
    try:
        file_bytes = Path(file_location).read_bytes()
    finally:
        os.remove(Path(file_location))

    sampled_bytes = sample_file_bytes(file_bytes)
    
    audio_hash = get_audio_hash(sampled_bytes)

    # Check if it already exists
    audio_hash_query = query_audio_results_by_sample_hash(audio_hash)
    if audio_hash_query:
        # Update task and return
        payload = {
            'task_id': task_id,
            'file_hash': file_hash,
            **audio_hash_query
        }
        update_task_status(task_id, 'COMPLETE', results = payload['results'])
        _backend.publish("celery:results", json.dumps(payload))
        return
    
    # Update status from pending to processing and start running
    update_task_status(task_id, 'PROCESSING')
    _backend.publish("celery:results", json.dumps({
        'task_id': task_id,
        'file_hash': file_hash,
        'status': 'PROCESSING'
    }))
    spectrogram = mp3_to_spectrogram(sampled_bytes)
    genre, accuracy = run_analysis(spectrogram)

    payload = {
        'task_id': task_id,
        'file_hash': file_hash,
        'status': 'COMPLETE',
        'results': {
            'genre': genre,
            'accuracy': accuracy
        }
    }

    update_task_status(task_id, 'COMPLETE', results = payload['results'])
    _backend.publish("celery:results", json.dumps(payload))