# Celery Worker
## About
This Celery worker folder includes a Celery worker that loads the model, converts the file bytes to spectrograms, and runs inference on the spectrograms.
This folder also includes a gRPC server that takes requests and enqueues the Celery tasks.
There are separate docker folders for the gRPC server and Celery worker, the worker includes torch and the server does not.
Currently the worker uses CUDA, so if you do not have CUDA available, the torch needs to be changed and the docker image needs to be changed.
The full flow for these microservices is a backend makes a gRPC request to the server, which then enqueues the task using RabbitMQ as a broker.
Once the task is running, it converts the given file bytes to a spectrogram and checks if the hash of the spectrogram already exists in the database to return the results reducing duplicate work.
If the hash does not exist or it failed, the spectrograms are put through the inference model, which then sends results to the backend using Redis pub-sub channels.
With Redis configured as the backend, when returning results from a task, they are stored as key value pairs, so to have a pub-sub channel without polling from the backend, manually publishing the results to Redis is necessary.

## Usage
This Celery application uses 1 PostgreSQL db, 1 Redis db, 1 RabbitMQ broker, and 1 gRPC server.
The PostgreSQL is for long-term storage of past results.
The Redis db is to publish results.
The RabbitMQ broker is to store queued tasks.
The gRPC server is to allow requests that are to be enqueued.
Without the gRPC server, enqueuing requests would need to be made to the RabbitMQ broker following a specific message format, so the server simplifies what is needed.
To run the application, first build the docker images, then run the given docker compose.
A .env file is needed with values for the PostgreSQL and Celery worker.
The PostgreSQL environment variables follow standard Postgres environment names.
The Celery worker env variables are all in `./src/config/settings.py` with the exception of `BROKER_URL`, which is the broker for Celery.