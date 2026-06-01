from io import BytesIO

from minio import Minio
from minio.error import S3Error
from minio.commonconfig import Filter
from minio.lifecycleconfig import LifecycleConfig, Rule, Expiration

_client = None

def init_minio(config: dict) -> None:
    """
    Initializes MinIO storage using the given config.
    Args:
        config (dict): {
            endpoint (str): URL of the target MinIO service
            access_key (str): access key/user id for the service
            secret_key (str): secret key/password for the given user
        }
    """
    global _client
    # default create client and default bucket
    _client = Minio(**config)
    default_bucket = "audio-files"

    if not _client.bucket_exists(bucket_name=default_bucket):
        _client.make_bucket(bucket_name=default_bucket)

    # Set files to expire after a day
    lifecycle = LifecycleConfig([
        Rule(
            status="Enabled",
            rule_filter=Filter(prefix=""),
            expiration=Expiration(days=1)
        )
    ])
    _client.set_bucket_lifecycle(default_bucket, lifecycle)

def get_minio():
    if _client == None:
        print("Error client is None")
        raise S3Error("MinIO client was requested, but has not been initialized.")
    
    return _client

def insert_file(bucket: str, destination: str, data: bytes):
    # Make new bucket if it does not exist
    if not _client.bucket_exists(bucket_name=bucket):
        _client.make_bucket(bucket_name=bucket)

    # Add the object to the bucket. If an error occurs, it raises
    _client.put_object(
        bucket_name=bucket,
        object_name=destination,
        data=BytesIO(data),
        length=len(data)
    )

def get_file(bucket: str, destination: str, file_name: str) -> bool:
    try:
        _client.fget_object(
            bucket_name=bucket, 
            object_name=destination,
            file_path=file_name
        )

        return True
    except S3Error as e:
        # Return false if the file is not found
        if e.code == "NoSuchKey":
            return False
        # Unexpected errors should still throw
        raise