import json

from pydantic import BaseModel

from config.broker import get_broker
from app import celery_app, app
from repo.repo import query_uploads_by_hash, update_task_status, query_audio_results_by_sample_hash
from service.helpers import sample_file_bytes, get_audio_hash, run_analysis

class EnqueueRequest(BaseModel):
    taskId: str
    fileHash: str

@app.post("/enqueue")
def enqueue_inference_task(request: EnqueueRequest):
    inference_task.delay(request.taskId, request.fileHash)
    return {'status': 'PROCESSING'}

@celery_app.task(bind=True, name="tasks.inference_task")
def inference_task(self, taskId: str, fileHash: str):
    _broker = get_broker()
    # get file bytes, log failure if DNE
    file_bytes = query_uploads_by_hash(fileHash)
    if file_bytes is None:
        update_task_status(taskId, 'FAILED', error = "Task Error: file not found in database, taskId={taskId}")
        _broker.publish("celery:results", json.dumps({
            'taskId': taskId,
            'status': 'FAILED',
            'error': f"Task Error: file not found in database, taskId={taskId}"
        }))
        return

    sampled_bytes = sample_file_bytes(file_bytes)
    
    audio_hash = get_audio_hash(sampled_bytes)

    # Check if it already exists
    audio_hash_query = query_audio_results_by_sample_hash(audio_hash)
    if audio_hash_query:
        # Update task and return
        payload = {
            'taskId': taskId,
            'fileHash': fileHash,
            **audio_hash_query
        }
        _broker.publish("celery:results", json.dumps(payload))
        return
    
    # Update status from pending to processing and start running
    update_task_status(taskId, 'PROCESSING')
    _broker.publish("celery:results", json.dumps({
        'taskId': taskId,
        'status': 'PROCESSING'
    }))

    genre, accuracy = run_analysis(sampled_bytes)

    payload = {
        'taskId': taskId,
        'fileHash': fileHash,
        'results': {
            'genre': genre,
            'accuracy': accuracy
        }
    }

    update_task_status(taskId, 'COMPLETE', results = payload['results'])
    _broker.publish("celery:results", json.dumps(payload))