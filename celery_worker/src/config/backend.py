import redis

_broker = None

def get_backend():
    return _broker

def init_backend(config: dict):
    global _broker
    _broker = redis.Redis(**config)