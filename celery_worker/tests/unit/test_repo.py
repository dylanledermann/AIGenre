import json
import os
from pathlib import Path
import uuid

import pytest
from src.config.settings import get_settings, init_settings
from src.repo.repo import *
from testcontainers.postgres import PostgresContainer

postgres = PostgresContainer("postgres:16-alpine")

@pytest.fixture(scope="module", autouse=True)
def setup():
    # Set initial db script
    init_script = Path(__file__).parent / "db.sql"
    postgres.with_volume_mapping(
        host=str(init_script),
        container=f"/docker-entrypoint-initdb.d/{init_script.name}"
    )

    postgres.start()
    sample_env_path = './test.env'
    with open(sample_env_path, 'w') as f:
        f.write(f"""
            MODEL_PATH=./ai-model/without_lyrics_cnn_weights.pth
                
            # Database
            DB_HOST={postgres.get_container_host_ip()}
            DB_PORT={postgres.get_exposed_port(5432)}
            DB_NAME={postgres.dbname}
            DB_USERNAME={postgres.username}
            DB_PASSWORD={postgres.password}

            # Broker
            BROKER_HOST=localhost
            BROKER_PORT=6379
        """)

    # Initialize settings and pool
    init_settings(sample_env_path)
    settings = get_settings()
    init_pool(settings.database_config())

    yield

    os.remove(sample_env_path)
    postgres.stop()

@pytest.fixture(scope="function", autouse=True)
def reset_db():
    with get_db() as db:
        with db.cursor() as cursor:
            cursor.execute("DELETE FROM uploads")

class TestRepo:
    def populate_db(self):
        """Helper to populate the db with dummy values and save the values."""
        
        self.file_hash1 = "a" * 64

        self.file_hash2 = "b" * 64

        self.sample_hash1 = "b" * 64
        self.results1 = {
            "genre": "pop",
            "accuracy": "83%"
        }
        self.task_id1 = uuid.uuid4()
        self.status1 = "COMPLETE"

        self.sample_hash2 = "c" * 64
        self.error2 = "Some error occurred"
        self.task_id2 = uuid.uuid4()
        self.status2 = "FAILED"
        with get_db() as db:
            with db.cursor() as cursor:
                cursor.execute(
                    "INSERT INTO uploads (file_hash)" \
                    "VALUES (%s), (%s)",
                    (self.file_hash1, self.file_hash2)
                )
                cursor.execute(
                    "INSERT INTO audio_results" \
                    "(task_id, sample_hash, file_hash, status, result, error)" \
                    "VALUES (%s, %s, %s, %s, %s, %s), (%s, %s, %s, %s, %s, %s)",
                    (
                        self.task_id1, self.sample_hash1, self.file_hash1, self.status1, json.dumps(self.results1), None,
                        self.task_id2, self.sample_hash2, self.file_hash2, self.status2, None, self.error2
                    )
                )

    # -------------- query_audio_results_by_sample_hash --------------
    def test_query_audio_results_by_sample_hash_complete(self):
        """Tests query_audio_results_by_sample_hash to ensure correct output when status is 'COMPLETE'."""
        self.populate_db()
        assert ({"status": self.status1, "results": self.results1}) == query_audio_results_by_sample_hash(self.sample_hash1)

    def test_query_audio_results_by_sample_hash_failed(self):
        """Tests query_audio_results_by_sample_hash to ensure no output is given when the status is 'FAILED'."""
        self.populate_db()
        assert query_audio_results_by_sample_hash(self.sample_hash2) is None

    # -------------- update_task_status --------------
    def test_update_task_status_failed_to_complete(self):
        """Tests update_task_status correctly updates values"""
        self.populate_db()
        results = {"genre": "hip-hop", "accuracy": "34%" }
        update_task_status(self.task_id2, "COMPLETE", results=results)
        with get_db() as db:
            with db.cursor() as cursor:
                cursor.execute(
                    "SELECT file_hash, sample_hash, status, error, result " \
                    "FROM audio_results " \
                    "WHERE task_id=%s::uuid",
                    (str(self.task_id2),)
                )
                assert (self.file_hash2, self.sample_hash2, "COMPLETE", None, results) == cursor.fetchone()