from contextlib import contextmanager
import json

from psycopg_pool import ConnectionPool

from typing import Optional

_config = None

def init_pool(config: dict):
    global _config
    _config = config

@contextmanager
def get_db():
    with ConnectionPool(**_config) as pool:
        with pool.connection() as conn:
            try:
                yield conn
                if not conn.autocommit:
                    conn.commit()
            except Exception as e:
                if not conn.autocommit:
                    conn.rollback()
                print("Database Error")
                raise

def query_uploads_by_hash(file_hash: str) -> Optional[bytes]:
    with get_db() as db:
        with db.cursor() as cursor:
            cursor.execute(
                "SELECT file_bytes FROM files WHERE file_hash = %s",
                (file_hash,)
            )

            return cursor.fetchone()
        
def query_audio_results_by_sample_hash(sample_hash: str) -> Optional[dict]:
    with get_db() as db:
        with db.cursor() as cursor:
            cursor.execute(
                "SELECT task_id, status, result, error from audio_results WHERE sample_hash = %s",
                (sample_hash,)
            )

            exist = cursor.fetchone()
            if exist:
                status = exist[1]

                if status == 'FAILED':
                    print(f"Rerunning analysis for task id: {exist[0]}. Reason for failure {exist[3]}")

                elif status == 'COMPLETE':
                    return {
                        'status': status,
                        'results': exist[2]
                    }
                
                elif status == 'PROCESSING':
                    return {
                        'taskId': exist[0],
                        'status': status
                    }
                
            return None
        
def update_task_status(taskId: str, status: str, error: str = None, results = dict[str, str]) -> None:
    with get_db() as db:
        with db.cursor() as cursor:
            match status:
                case 'COMPLETE':
                    cursor.execute(
                        """
                            UPDATE audio_results
                            SET status = %s, result = %s, error=NULL, finished_at=NOW()
                            WHERE task_id = %s::uuid
                        """,
                        (status, json.dumps(results), taskId)
                    )
                case 'FAILED':
                    cursor.execute(
                        """
                            UPDATE audio_results
                            SET status = %s, error = %s, result=NULL, finished_at=NOW()
                            WHERE task_id = %s::uuid
                        """,
                        (status, error, taskId)
                    )
                case _:
                    cursor.execute(
                        """
                            UPDATE audio_results
                            SET status = %s, error=NULL, result=NULL, finished_at=NULL
                            WHERE task_id = %s::uuid
                        """,
                        (status, taskId)
                    )