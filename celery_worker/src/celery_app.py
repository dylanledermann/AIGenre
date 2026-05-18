import os

from celery import Celery
from celery.signals import worker_process_init, worker_process_shutdown

from config.broker import init_broker
from repo.repo import init_pool
from config.settings import init_settings, get_settings
from ai_model.model import build_model

celery_app = Celery(
    'ai-genre-worker',
    broker=os.getenv('BROKER_URL'),
    include=[
        'tasks.inference_task'
    ]
)

celery_app.conf.update(
    task_serializer='json',
    accept_content=['json'],
    result_serializer='json',
    timezone='UTC',
    enable_utc=True,
    task_track_started=True,
    task_time_limit=3600, # 1 hour
    worker_prefetch_multiplier=1, # Parallel tasks
    worker_max_tasks_per_child=50, # Number of tasks before restarting worker (prevents memory leak)
    task_acks_late=True
)

@worker_process_init.connect
def init_worker(**kwargs):

    init_settings()
    settings = get_settings()
    
    build_model(settings.model_config())
    
    init_pool(settings.database_config())

    init_broker(settings.broker_config())

@worker_process_shutdown.connect
def shutdown_worker(**kwargs):
    pass