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

# Make it persist across reboots
sudo tee /etc/systemd/system/directpv-disk1.service > /dev/null <<'EOF'
[Unit]
Description=Setup dm-linear device for DirectPV disk1
After=local-fs.target
Before=kubelet.service

[Service]
Type=oneshot
RemainAfterExit=yes

ExecStart=/bin/bash -c '\
  LOOP=$(losetup --find --show /data/disk1.img) && \
  SIZE=$(blockdev --getsz $LOOP) && \
  echo "0 $SIZE linear $LOOP 0" | dmsetup create directpv-disk1'

ExecStop=/bin/bash -c '\
  dmsetup remove directpv-disk1; \
  losetup -d $(losetup -j /data/disk1.img | cut -d: -f1)'

[Install]
WantedBy=multi-user.target
EOF

# Enable and start it
sudo systemctl daemon-reload
sudo systemctl enable directpv-disk1.service
sudo systemctl start directpv-disk1.service

# Verify
sudo systemctl status directpv-disk1.service

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
```

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
helm repo add minio-operator https://operator.min.io
help repo update

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