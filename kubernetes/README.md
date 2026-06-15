# Organization
This directory is organized by service similar to the main file structure.
The .env files are not included in the git repo, but they are used for secrets in each corresponding environment.
Each manifest that requires secrets has to mount the secret to the pod, so 
Example of creating the secret:
kubectl create secret generic <name> --from-file=path/to/.env
```test
kubernetes/
├─ ai_genre_frontend/
|  ├─ frontend/ # contains .env secret file and frontend deployment manifest
|  |  ├─ .env
|  |  └─ frontend_deployment.yaml
|  └─ ingress.yaml # Actually gateway manifests, ingress is frozen
├─ ai-genre/ # contains .env file and backend deployment manifest
|  ├─ .env
|  └─ backend_deployment.yaml
├─ celery_worker/
|  ├─ celery/ # contains .env secret file and celery worker deployment manifest
|  |  ├─ .env
|  |  └─ celery_deployment.yaml
|  └─ grpc/ # contains .env secret file and grpc server deployment manifest
|     ├─ .env
|     └─ grpc_deployment.yaml
└── storage/
    ├─ celery_broker/ # contains deployment manifest, .env, and .md guide for deploying the celery broker
    |  ├─ .env
    |  ├─ celery_broker.yaml
    |  └─ rabbitmq_setup.md
    ├─ minio/ # contains .md about minio operator, operator config values, and pod manifest
    |  ├─ minio_storage.yaml # manifest for pods
    |  ├─ minio_config.yaml # values for operator
    |  └─ minio_setup.md
    ├─ postgres_db/ # contains .env secret file, operator setup .md, and pod manifest
    |  ├─ .env
    |  ├─ postgres_db.yaml
    |  └─ postgresql_setup.md
    ├─ backend.yaml # backend redis manifest
    └─ cache.yaml # redis cache manifest
```

# Deployment
Each application has a deployment/manifest to create all the pods for the application.
Once you have a kubernetes control plane set up, you can run kubectl apply -f <file/directory> on each of the .yaml files for each directory.
Each service requires secrets, which can be found from default environment variables for the image or the created services configs files/application properties.
To add the .env files to each node create the secrets `kubectl create secret generic <secret-name> --from-env-file=<path/to/.env>`.
Make sure to edit the name of the secrets for each deployment (they inject the secrets through the name of the secret).

# Testing
The architecture was testing using Multipass with Hyper-V.
This was mostly done following [Omer Sezer's](https://github.com/omerbsezer/Fast-Kubernetes/blob/main/K8s-Kubeadm-Cluster-Setup.md) guide.
## Cluster Setup
### Set Up Multipass containers
```bash
# Create the nodes
multipass launch -n <container-name> -c <number-of-cores> -m <memory> -d <disk-size>
# Example with 2 cores, 2 gigabytes of memory, and 10 gigabytes of disk space
multipass launch -n master -c 4 -m 2G -m 80G
```
### Enter Multipass container shell
```bash
multipass shell <container-name>
```

### Configuring the Kubernetes Nodes
To get the nodes working, you need to initialize networking within the containers, install the Kubernetes packages, and initialize the clusters.
#### Setting Up Networks
- Run on ALL nodes:
```bash
# Makes sure br_netfilter runs on startup
cat <<EOF | sudo tee /etc/modules-load.d/k8s.conf
br_netfilter
EOF

# Ensures network traffice crossing a bridge is processed by the iptables
cat <<EOF | sudo tee /etc/sysctl.d/k8s.conf
net.bridge.bridge-nf-call-ip6tables = 1
net.bridge.bridge-nf-call-iptables = 1
EOF

# Reloads kernel parameters from all system config files. This applies the changes above.
sudo sysctl --system
```
- When running Kubernetes on-premise (instead of in a VM), you need to stop swaps (using memory for VMs).
```bash
sudo swapoff -a
sudo sed -i '/ swap / s/^/#/' /etc/fstab
```
- Define proxy environment variables (On Worker and Master, swap master ip for worker on master node).
```bash
export no_proxy="192.168.*.*, ::6443, <your-master-ip>:6443, 172.24.*.*, 172.25.*.*, 10.*.*.*, localhost, 127.0.0.1"
```
#### Install Kubernetes Packages
- Run on ALL nodes:
```bash
# overlay and br_netfilter are started on startup from containerd config
cat <<EOF | sudo tee /etc/modules-load.d/containerd.conf
overlay
br_netfilter
EOF

# loads the packages
sudo modprobe overlay
sudo modprobe br_netfilter

# Allows Kubernetes to interact with IP tables
cat <<EOF | sudo tee /etc/sysctl.d/99-kubernetes-cri.conf
net.bridge.bridge-nf-call-iptables  = 1
net.ipv4.ip_forward                 = 1
net.bridge.bridge-nf-call-ip6tables = 1
EOF

# Apply the previous changes
sudo sysctl --system

# Installing Containerd
## Update and upgrade packages
sudo apt-get update
sudo apt-get upgrade -y

sudo apt-get install containerd -y
# Apply containerd config to root
sudo mkdir -p /etc/containerd
sudo containerd config default | tee /etc/containerd/config.toml
sudo systemctl restart containerd

# Install kubectl following the guide: https://kubernetes.io/docs/tasks/tools/install-kubectl-linux/
# Install binary
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"

# Validate binary with checksum
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl.sha256"
# Should show 'kubectl: OK'
echo "$(cat kubectl.sha256)  kubectl" | sha256sum --check

# Install kubectl
sudo install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl

# Update apt package index for Kubernetes repo
sudo apt-get update
# apt-transport-https may be a dummy package; if so, you can skip that package
sudo apt-get install -y apt-transport-https ca-certificates curl gnupg

# Download signing key for Kubernetes repo
# If the folder `/etc/apt/keyrings` does not exist, it should be created before the curl command, read the note below.
# sudo mkdir -p -m 755 /etc/apt/keyrings
curl -fsSL https://pkgs.k8s.io/core:/stable:/v1.36/deb/Release.key | sudo gpg --dearmor -o /etc/apt/keyrings/kubernetes-apt-keyring.gpg
sudo chmod 644 /etc/apt/keyrings/kubernetes-apt-keyring.gpg # allow unprivileged APT programs to read this keyring

# Add appropriate apt repo
# This overwrites any existing configuration in /etc/apt/sources.list.d/kubernetes.list
echo 'deb [signed-by=/etc/apt/keyrings/kubernetes-apt-keyring.gpg] https://pkgs.k8s.io/core:/stable:/v1.36/deb/ /' | sudo tee /etc/apt/sources.list.d/kubernetes.list
sudo chmod 644 /etc/apt/sources.list.d/kubernetes.list   # helps tools such as command-not-found to work correctly

# Install kubectl
sudo apt-get update
sudo apt-get install -y kubectl

# Install kubelet and kubeadm
sudo apt install -y kubeadm kubelet
# Prevent auto-update on the packages (changes can break the implementation)
sudo apt-mark hold kubelet kubeadm kubectl

# Install Kubernetes Cluster
sudo kubeadm config images pull
```

#### Set Up Kubernetes Cluster
- Get Master IP
```bash
# On host machine (multipass tracks IP)
multipass list

# On Worker machine
ping <container-name>
```
- Start Cluster on master
```bash
# Init KubeAdm
sudo kubeadm init --pod-network-cidr=192.168.0.0/16 --apiserver-advertise-address=<ip> --control-plane-endpoint=<ip>
# sudo kubeadm init --pod-network-cidr=192.168.0.0/16 --apiserver-advertise-address=172.31.45.74 --control-plane-endpoint=172.31.45.74
```
- After this command finishes, a list of other commands to run should be given. Possible commands (Copy specific kubernetes output, since tokens will be different here):
```bash
# To start cluster run the following as a regular user:
mkdir -p $HOME/.kube
sudo cp -i /etc/kubernetes/admin.conf $HOME/.kube/config
sudo chown $(id -u):$(id -g) $HOME/.kube/config
# If root user, run the following instead
export KUBECONFIG=/etc/kubernetes/admin.conf

# To add worker node, run on worker machine:
sudo kubeadm join 172.31.45.74:6443 --token w7nntd.7t6qg4cd418wzkup \
        --discovery-token-ca-cert-hash sha256:1f03886e5a28fb9716e01794b4a01144f362bf431220f15ca98bed2f5a44e91b

# To add master node, run on master machine being added:
sudo kubeadm join 172.31.45.74:6443 --token w7nntd.7t6qg4cd418wzkup \
        --discovery-token-ca-cert-hash sha256:1f03886e5a28fb9716e01794b4a01144f362bf431220f15ca98bed2f5a44e91b \
        --control-plane
```

#### Set Up Kubernetes Network
- Kubernetes requires network plugins for nodes to communicate with each other.
```bash
# For Calico (follow https://docs.tigera.io/calico/latest/getting-started/kubernetes/quickstart):
kubectl create -f https://raw.githubusercontent.com/projectcalico/calico/v3.32.0/manifests/tigera-operator.yaml
# If you have your own manifests for calico, replace them here.
curl -O https://raw.githubusercontent.com/projectcalico/calico/v3.32.0/manifests/custom-resources.yaml
# Verify the manifest has correct cidr, etc. then apply it.
kubectl create -f custom-resources.yaml
```

### Adding Applications
For this, I will be using the applications in this repo, but any application can be used in general.
The following namespaces will also need to be created - `backend`, `frontend`, `celery`.

#### Install Helm
Helm will be used to get manifests, such as the operators. 
The full install instructions can be found on the [Helm website](https://helm.sh/docs/intro/install/).
The instructions below are for installing Helm with Apt (Debian/Ubuntu).

```bash
HELM_BUILDKITE_APT_KEY_ID="DDF78C3E6EBB2D2CC223C95C62BA89D07698DBC6"

sudo apt-get install curl gpg apt-transport-https --yes

curl -fsSL https://packages.buildkite.com/helm-linux/helm-debian/gpgkey > "${TMPDIR:-/tmp}/helm.gpg"

# Ensure that the key ID matches to prevent a repository compromise from establishing an attacker controlled key
if [ "$(gpg --show-keys --with-colons "${TMPDIR:-/tmp}/helm.gpg" | awk -F: '$1 == "fpr" {print $10}' | head -n 1)" != "${HELM_BUILDKITE_APT_KEY_ID}" ]; then echo "ERROR: Unexpected Helm APT key ID: potential key compromise"; exit 1; fi

cat "${TMPDIR:-/tmp}/helm.gpg" | gpg --dearmor | sudo tee /usr/share/keyrings/helm.gpg > /dev/null
echo "deb [signed-by=/usr/share/keyrings/helm.gpg] https://packages.buildkite.com/helm-linux/helm-debian/any/ any main" | sudo tee /etc/apt/sources.list.d/helm-stable-debian.list

sudo apt-get update
sudo apt-get install helm
```

#### Install Operators

*Check the each subdirectory's .md for specific info on creating the application.

This repository uses operator-managed custom resources for the following services:

 - PostgreSQL: `kubernetes/storage/postgres_db/postgres_db.yaml` uses the CloudNativePG operator (`postgresql.cnpg.io/v1`).
 - MinIO: `kubernetes/storage/minio/minio_storage.yaml` uses the MinIO Aistor operator (`minio.min.io/v2`).
 - RabbitMQ: `kubernetes/storage/celery_broker/celery_broker.yaml` uses the RabbitMQ Cluster Operator (`rabbitmq.com/v1beta1`).

Install the operators before applying the service CRDs:

1. PostgreSQL operator
```bash
helm repo add cnpg https://cloudnative-pg.github.io/charts
helm upgrade --install cnpg --namespace backend --create-namespace --set config.clusterWide=false cnpg/cloudnative-pg
```

2. MinIO operator
- Requires getting license from: [here](https://subnet.min.io) as well as installing [krew](https://krew.sigs.k8s.io/docs/user-guide/setup/install/) (package manager for kubernetes)
```bash
kubectl krew install directpv
kubectl directpv install
# MinIO requires 3+ drives
kubectl directpv discover
kubectl directpv init drives.yaml
helm install aistor minio/aistor-operator -n celery --create-namespace --set license="<license>" -f kubernetes/storage/minio/minio_config.yaml
```

3. RabbitMQ operator
```bash
kubectl apply -f "https://github.com/rabbitmq/cluster-operator/releases/latest/download/cluster-operator.yml"

# After the operators are installed, apply the corresponding service manifests with:
kubectl apply -f kubernetes/storage/postgres_db/postgres_db.yaml
kubectl apply -f kubernetes/storage/minio/minio_storage.yaml
kubectl apply -f kubernetes/storage/celery_broker/celery_broker.yaml
```

#### Create Pods

##### Storage
For multipass I am using the rancher local-storage and setting it as default storage. 
The default storage is used for cache.yaml, backend.yaml, the postgres db, and the rabbitmq broker.
For all pods, you can go through the setup.md file in each repo. If there is not one, you can just apply the manifest with `kubectl apply -f <manifest.yaml>`
```bash
# Install rancher local-path
kubectl apply -f https://raw.githubusercontent.com/rancher/local-path-provisioner/master/deploy/local-path-storage.yaml

# Make local-path the default storage class
kubectl patch storageclass local-path -p '{"metadata": {"annotations":{"storageclass.kubernetes.io/is-default-class":"true"}}}'
```

To test the redis containers, or any container in general, create a busybox pod and ping the pod:

```bash
kubectl run -it --rm busybox --image=busybox --restart=Never -- nslookup backend-redis-0.backend-redis.backend.svc.cluster.local
# Remove the statefule set name if not a stateful set.
kubectl run -it --rm busybox --image=busybox --restart=Never -- nslookup <pod-name>.<stateful-set-name>.<namespace>.svc.cluster.local
```

##### Application
Create the .env files (should be in each individual folders for isolation) and apply them with 
`kubectl create secret generic -n <namespace> <secretname> --from-env-file=path/to/env`.
Install docker:
```bash
sudo apt update
sudo apt install docker.io -y
sudo systemctl start docker
sudo systemctl enable docker
sudo usermod -aG docker $USER
newgrp docker
```