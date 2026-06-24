from concurrent import futures
import signal
import grpc
import prometheus_client
from py_grpc_prometheus.prometheus_server_interceptor import PromServerInterceptor

from src.generated_sources import inference_pb2_grpc
from src.grpc.InferenceService import InferenceService

import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

def serve():
    # Initialize prometheus server intercept
    psi = PromServerInterceptor(enable_handling_time_histogram=True)

    # Initialize grpc server with interceptor
    server = grpc.server(
        futures.ThreadPoolExecutor(max_workers=4),
        interceptors=(psi,)
    )
    inference_pb2_grpc.add_InferenceServiceServicer_to_server(
        InferenceService(), server
    )

    # Start server
    server.add_insecure_port('[::]:50051')
    server.start()

    # Start metrics server
    prometheus_client.start_http_server(8000)

    def handle_shutdown(signum, frame):
        server.stop(grace=5)  # 5 seconds to finish in-flight requests

    signal.signal(signal.SIGTERM, handle_shutdown)
    signal.signal(signal.SIGINT, handle_shutdown)

    server.wait_for_termination()

if __name__ == "__main__":
    logger.info("Starting server")
    serve()