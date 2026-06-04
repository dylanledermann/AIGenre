# MinIO Setup
To set up minio, use the minio operator.
log in to (SUBNET)[https://subnet.min.io/?jmp=docs] and get a liscence for minio aistor.
```bash
# Add helm repo
helm repo add minio https://helm.min.io/

# Install aistor operator and create namespace
helm install aistor minio/aistor-operator \
  -n <namespace> --create-namespace \
  --set license="<liscense> \
  -f <file containing values>"
```