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

The current postgres db uses local path, which can be installed from rancher:

```bash
kubectl apply -f https://raw.githubusercontent.com/rancher/local-path-provisioner/master/deploy/local-path-storage.yaml

# Make local-path the default storage class
kubectl patch storageclass local-path -p '{"metadata": {"annotations":{"storageclass.kubernetes.io/is-default-class":"true"}}}'
```

After the operator is installed, apply the PostgreSQL custom resource:

```bash
kubectl apply -f kubernetes/storage/postgres_db/postgres_db.yaml
```

Test the connection:

```bash
kubectl run pg-client --rm -i --tty --image=postgres --restart=Never -- psql "postgres://user:password@postgres-rw.backend.svc.cluster.local:5432/genre_db"

# General Form
kubectl run pg-client --rm -i --tty --image=postgres --restart=Never -- psql "postgres://<user>:<password>@<svc-name>.<namespace>.svc.cluster.local:<port>/<db>
```