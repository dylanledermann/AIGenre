from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from typing import ClassVar as _ClassVar, Optional as _Optional

DESCRIPTOR: _descriptor.FileDescriptor

class FileChunk(_message.Message):
    __slots__ = ("task_id", "file_hash", "chunk", "is_last")
    TASK_ID_FIELD_NUMBER: _ClassVar[int]
    FILE_HASH_FIELD_NUMBER: _ClassVar[int]
    CHUNK_FIELD_NUMBER: _ClassVar[int]
    IS_LAST_FIELD_NUMBER: _ClassVar[int]
    task_id: str
    file_hash: str
    chunk: bytes
    is_last: bool
    def __init__(self, task_id: _Optional[str] = ..., file_hash: _Optional[str] = ..., chunk: _Optional[bytes] = ..., is_last: bool = ...) -> None: ...

class EnqueueResponse(_message.Message):
    __slots__ = ("message",)
    MESSAGE_FIELD_NUMBER: _ClassVar[int]
    message: str
    def __init__(self, message: _Optional[str] = ...) -> None: ...
