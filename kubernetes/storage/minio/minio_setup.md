# Create MinIO Storage Class
Minio has a specific persistent volume storage class.

Before creating the DirectPV storage class, you need to create an unmounted block on whatever node is going to be running directpv.
Since MinIO does not allow external disks, you can create a dm-linear loop with the following instructions:
```bash
# Create drive and disk image
sudo mkdir -p /data
sudo dd if=/dev/zero bs=1 count=0 seek=10G of=/data/disk1.img

# Verify it
ls -lh /data/disk1.img      # shows 10G logical size
du -sh /data/disk1.img      # shows near-zero actual usage (sparse)

# Attack the loop device
LOOP=$(sudo losetup --find --show /data/disk1.img)
echo $LOOP # e.g. /dev/loop0

# Verify
sudo losetup -l # should show your loop device and backing file

# Get device size for sectors
SIZE_SECTORS=$(sudo blockdev --getsz $LOOP)
echo $SIZE_SECTORS # e.g. 20971520 for a 10G device

# Create dm-linear device
echo "0 $SIZE_SECTORS linear $LOOP 0" | sudo dmsetup create directpv-disk1

# Verify
sudo dmsetup ls # should show directpv-disk1
sudo dmsetup info directpv-disk1
ls -lh /dev/mapper/directpv-disk1

# Confirm it shows in lsblk
lsblk /dev/mapper/directpv-disk1

# Add more disks
# Disk 2
sudo dd if=/dev/zero bs=1 count=0 seek=10G of=/data/disk2.img
LOOP2=$(sudo losetup --find --show /data/disk2.img)
SIZE2=$(sudo blockdev --getsz $LOOP2)
echo "0 $SIZE2 linear $LOOP2 0" | sudo dmsetup create directpv-disk2

# Disk 3
sudo dd if=/dev/zero bs=1 count=0 seek=10G of=/data/disk3.img
LOOP3=$(sudo losetup --find --show /data/disk3.img)
SIZE3=$(sudo blockdev --getsz $LOOP3)
echo "0 $SIZE3 linear $LOOP3 0" | sudo dmsetup create directpv-disk3

# Make it persist across reboots (This is for 3, to change it change the for loop)
sudo tee /etc/systemd/system/directpv-disks.service > /dev/null <<'EOF'
[Unit]
Description=Setup dm-linear devices for DirectPV
After=local-fs.target
Before=kubelet.service

[Service]
Type=oneshot
RemainAfterExit=yes

ExecStart=/bin/bash -c 'for i in 1 2 3 ; do dmsetup info directpv-disk$i > /dev/null 2>&1 || (LOOP=$(losetup --find --show /data/disk$i.img) && SIZE=$(blockdev --getsz $LOOP) && echo "0 $SIZE linear $LOOP 0" | dmsetup create directpv-disk$i); done'

ExecStop=/bin/bash -c 'for i in 1 2 3 ; do dmsetup remove directpv-disk$i 2>/dev/null; losetup -d $(losetup -j /data/disk$i.img | cut -d: -f1) 2>/dev/null; done'

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable directpv-disks.service
sudo systemctl start directpv-disks.service
sudo systemctl status directpv-disks.service
```

```bash
# Install krew from: https://krew.sigs.k8s.io/docs/user-guide/setup/install/
kubectl krew install directpv
kubectl directpv install

# Scan for drives and initialize them
kubectl directpv discover
kubectl directpv init drives.yaml

# To see drives
kubectl directpv list drives
```

# Create certs for minio
Minio requires certs in kubernetes pods. 
Here the certs are managed with cert-manager pods.
```bash
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/latest/download/cert-manager.yaml

# Wait for it to be ready
kubectl get pods -n cert-manager -w

# Once ready, create the pod
kubectl apply -f kubernetes/storage/minio/cert-issuer.yaml
```

- Get and validate the cert

```bash
# You can check and save the cert with the following:

# Check the secret was created
kubectl get secret minio-tls -n celery -o yaml

# Extract the CA cert
kubectl get secret minio-tls -n celery \
  -o jsonpath='{.data.ca\.crt}' | base64 -d > minio-ca.crt

# Verify it looks correct
openssl x509 -in minio-ca.crt -text -noout | grep -E 'Issuer|Subject|DNS|IP'

# Create the secret
kubectl create secret generic minio-ca \
  --from-file=ca.crt=minio-ca.crt \
  -n celery
```

# MinIO Setup
To set up MinIO, use the MinIO Aistor operator.
Log in to https://subnet.min.io and get a license for MinIO Aistor.

```bash
# Add Helm repo
helm repo add minio https://helm.min.io/
helm repo add minio-operator https://operator.min.io
helm repo update

# Install aistor operator
helm install aistor minio/aistor-operator \
  -n aistor --create-namespace \
  --set license="<license>"

# Install Aistor object storage
helm install miniostorage minio/aistor-objectstore \
  -n celery --create-namespace \
  -f kubernetes/storage/minio/minio_config.yaml
```

After the operator is installed, apply the MinIO Tenant manifest:

```bash
kubectl apply -f kubernetes/storage/minio/minio_storage.yaml
```

## Testing Minio
After creating the pod, you can get the login with:

 - `kubectl exec -it minio-pool-0-0 -n celery -c minio -- cat /tmp/minio/config.env`

Then connect with the following steps:

Connect to the console with `kubectl port-forward svc/minio-console 9443:9443 -n celery --address 0.0.0.0`.
Then log in to the address (localhost or the vm ip address).

Use the following for connecting:

 - Endpoint: `http://minio.celery.svc.cluster.local:9000`
 - Access Key: `MINIO_ROOT_USER`
 - Secret Key: `MINIO_ROOT_PASSWORD`

You can also test the connection with the following:
```bash
# Create test container and enter its bash
kubectl run minio-test -n celery --rm -it \
  --image=python:3.11-slim \
  --restart=Never \
  --overrides='{
    "spec": {
      "containers": [{
        "name": "minio-test",
        "image": "python:3.11-slim",
        "stdin": true,
        "tty": true,
        "command": ["/bin/bash"],
        "volumeMounts": [{
          "name": "minio-ca",
          "mountPath": "/etc/minio/certs"
        }]
      }],
      "volumes": [{
        "name": "minio-ca",
        "secret": {
          "secretName": "minio-ca"
        }
      }]
    }
  }'

# In the pod:
# Install minio and run script
pip install minio

python3 -c "
import urllib3
from minio import Minio

client = Minio(
    'minio.celery.svc.cluster.local:443',
    access_key='your-access-key',
    secret_key='your-secret-key',
    secure=True,
    http_client=urllib3.PoolManager(
        ca_certs='/etc/minio/certs/ca.crt'
    )
)

buckets = client.list_buckets()
for bucket in buckets:
    print(bucket.name)
"
```