from asyncio import futures
import signal
import grpc

from celery_worker.src.generated_sources.proto import inference_pb2_grpc
from celery_worker.src.grpc import InferenceService

def serve():
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=4))
    inference_pb2_grpc.add_InferenceServiceServicer_to_server(
        InferenceService(), server
    )
    server.add_insecure_port('[::]:50051')
    server.start()

    def handle_shutdown(signum, frame):
        server.stop(grace=5)  # 5 seconds to finish in-flight requests

    signal.signal(signal.SIGTERM, handle_shutdown)
    signal.signal(signal.SIGINT, handle_shutdown)

    server.wait_for_termination()

if __name__ == "__main__":
    serve()