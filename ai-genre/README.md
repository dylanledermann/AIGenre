# Spring Boot Backend
## About
This backend application allows for query queueing connecting the React frontend to the Celery worker.
The Spring to Celery communication is done via gRPC and Celery to Spring via Redis.
The React to Spring is done via REST and Spring to React via websockets.

## Usage
This Spring application uses 1 PostgreSQL db and 2 Redis dbs.
The PostgreSQL db is for long term storage, such as previous tasks and their results.
One of the Redis dbs is used for caching within the application to reduce repeated work.
The other Redis db is used as the celery backend to store results in for the Spring to read from.
In this the Redis db is called the broker db to log messages from Celery to Spring.
A dockerfile and docker compose are given, the file is for the Spring application and the compose is for the app and dbs.
A .env file is not given, but env variables are needed and can be found in application.yml.
I used the same .env file for the PostgreSQL db.