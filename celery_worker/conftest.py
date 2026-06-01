import logging
from pathlib import Path
from unittest.mock import patch

import pytest
import os

# db mocks
from testcontainers.postgres import PostgresContainer
from testcontainers.rabbitmq import RabbitMqContainer
from testcontainers.minio import MinioContainer
import fakeredis

# Global containers
postgres = PostgresContainer("postgres:16-alpine")
rabbitmq_broker = RabbitMqContainer('rabbitmq:3.12-alpine')
minio_container = MinioContainer()

# Fake Redis
redis_server = fakeredis.FakeServer()

pytest_plugins = ('celery.contrib.pytest',)

def pytest_configure(config):
    init_script = Path("./src/repo/db.sql")
    postgres.with_volume_mapping(
        host=str(init_script.absolute()),
        container=f"/docker-entrypoint-initdb.d/{init_script.name}"
    )
    postgres.start()
    rabbitmq_broker.start()
    minio_container.start()
     # Model
    os.environ['MODEL_PATH']='./src/ai_model/without_lyrics_cnn_weights.pth'

    # Database
    os.environ['DB_HOST']=postgres.get_container_host_ip()
    os.environ['DB_PORT']=str(postgres.get_exposed_port(5432))
    os.environ['DB_NAME']=postgres.dbname
    os.environ['DB_USERNAME']=postgres.username
    os.environ['DB_PASSWORD']=postgres.password

    # Backend
    os.environ['BACKEND_HOST']="host"
    os.environ['BACKEND_PORT']="1000"
    os.environ['BACKEND_DB']="db"

    # Broker
    os.environ['BROKER_URL']=f"amqp://{rabbitmq_broker.username}:{rabbitmq_broker.password}@{rabbitmq_broker.get_container_host_ip()}:{rabbitmq_broker.get_exposed_port(5672)}/"

    # MinIO
    minio_config = minio_container.get_config()
    os.environ['MINIO_HOST'] = minio_config['endpoint']
    os.environ['MINIO_ROOT_USER'] = minio_config['access_key']
    os.environ['MINIO_ROOT_PASSWORD'] = minio_config['secret_key']

def pytest_unconfigure(config):
    postgres.stop()
    rabbitmq_broker.stop()
    minio_container.stop()

@pytest.fixture(scope='session')
def celery_app():
    from src.celery_app import celery_app
    return celery_app

@pytest.fixture(scope='session')
def celery_config():
    # config already in celery_app
    return {}

@pytest.fixture(scope='session')
def celery_worker_parameters():
    return {
        'queues': ('celery',),
        'perform_ping_check': False
    }

@pytest.fixture(scope='session')
def redis_backend():
    return fakeredis.FakeRedis(server=redis_server)

@pytest.fixture(scope='session')
def redis_sub():
    return fakeredis.FakeRedis(server=redis_server)

@pytest.fixture(scope='session', autouse=True)
def mock_backend(redis_backend):
    import src.tasks.inference_task # import for patching
    # fake redis is in memory, so it needs to be patched over the real connection
    with patch('src.tasks.inference_task.get_backend', return_value=redis_backend):
        yield