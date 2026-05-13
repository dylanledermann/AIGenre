import redis

_broker = None

def get_broker():
    return _broker

def init_broker(config: dict):
    global _broker
    _broker = redis.Redis(**config())