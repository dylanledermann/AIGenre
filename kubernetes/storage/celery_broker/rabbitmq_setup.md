# RabbitMQ Setup
To use RabbitMQ, use the RabbitMQ Operator, which helps to manage the RabbitMQ pods.

```bash
# install the RabbitMQ operator
kubectl apply -f "https://github.com/rabbitmq/cluster-operator/releases/latest/download/cluster-operator.yml"
```

After the operator is installed, apply the RabbitMQ cluster manifest:

```bash
kubectl apply -f kubernetes/storage/celery_broker/celery_broker.yaml
```