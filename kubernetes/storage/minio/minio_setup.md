# Create MinIO Storage Class
Minio has a specific persistent volume storage class.

```bash
# Add MinIO repo
helm repo add minio https://helm.min.io/
helm repo update

# Install DirectPV Plugin
helm install directpv minio/directpv --namespace celery --create-namespace

# Install local management CLI, required for DirectPV to format drives
curl -L https://dl.min.io/aistor/directpv/release/linux-amd64/kubectl-directpv_5.1.0 -o kubectl-directpv
chmod +x ./kubectl-directpv
sudo mv ./kubectl-directpv /usr/local/bin/kubectl-directpv

# Scan for drives and initialize them
kubectl directpv discover
kubectl directpv init drives.yaml

# To see drives
kubectl directpv list drives

# Create storage class
kubectl apply -f kubernetes/storage/minio/minio_storage_class.yaml
```

# MinIO Setup
To set up MinIO, use the MinIO Aistor operator.
Log in to https://subnet.min.io and get a license for MinIO Aistor.

```bash
# Add Helm repo
helm repo add minio https://helm.min.io/

# Install Aistor operator and create namespace
helm install aistor minio/aistor-operator \
  -n celery --create-namespace \
  --set license="<license>" \
  -f kubernetes/storage/minio/minio_config.yaml
```

After the operator is installed, apply the MinIO Tenant manifest:

```bash
kubectl apply -f kubernetes/storage/minio/minio_storage.yaml
```