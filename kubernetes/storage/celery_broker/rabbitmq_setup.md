# RabbitMQ Setup
To use RabbitMQ, use the RabbitMQ Operator, which helps to manage the RabbitMQ pods.

First, create the rabbitmq secret for login (use this for the celery env):
```bash
kubectl create secret generic rabbitmq \
  --from-literal=default_user.conf="default_user = <user>
default_pass = <password>" \
  -n celery
```

```bash
# install the RabbitMQ operator
kubectl apply -f "https://github.com/rabbitmq/cluster-operator/releases/latest/download/cluster-operator.yml"
```

After the operator is installed, apply the RabbitMQ cluster manifest:

```bash
kubectl apply -f kubernetes/storage/celery_broker/celery_broker.yaml
```

You can connect with the following:

Log in to management server:
 - port forward with `kubectl port-forward svc/celerybroker 15672:15672 -n celery --address 0.0.0.0`
 - Access the in the browser as http://<vm-ip>:15672

You can also access this pod in a program with the following:
- host: celerybroker.celery.svc.cluster.local
- port: 5672

Test the connection with the following:
```bash
kubectl run rabbit-test -n celery --rm -it \
  --image=python:3.11-slim \
  --restart=Never \
  -- bash
```
 - Once in the pod run:
```bash
pip install pika
python3 -c "
import pika
credentials = pika.PlainCredentials('<user>', '<password>')
connection = pika.BlockingConnection(
    pika.ConnectionParameters(
        host='celerybroker.celery.svc.cluster.local',
        port=5672,
        credentials=credentials,
        virtual_host='/'
    )
)
print('Connection successful')
connection.close()
"
```