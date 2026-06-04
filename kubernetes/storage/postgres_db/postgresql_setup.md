# PostgreSQL Setup
To setup the Postgres containers, the cloud native postgres operator is used.
```bash
# Install the Postgres Operator with Helm
helm repo add cnpg https://cloudnative-pg.github.io/charts
helm upgrade --install cnpg \
  --namespace <namespace> \
  --create-namespace \
  cnpg/cloudnative-pg

# Single namespace installation
helm upgrade --install cnpg \
  --namespace <namespace> \
  --create-namespace \
  --set config.clusterWide=false \
  cnpg/cloudnative-pg
```