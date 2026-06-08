# PostgreSQL Setup
To setup the Postgres containers, the CloudNativePG operator is used.

```bash
# Install the Postgres Operator with Helm
helm repo add cnpg https://cloudnative-pg.github.io/charts
helm upgrade --install cnpg \
  --namespace backend \
  --create-namespace \
  cnpg/cloudnative-pg

# Single namespace installation
helm upgrade --install cnpg \
  --namespace backend \
  --create-namespace \
  --set config.clusterWide=false \
  cnpg/cloudnative-pg
```

After the operator is installed, apply the PostgreSQL custom resource:

```bash
kubectl apply -f kubernetes/storage/postgres_db/postgres_db.yaml
```