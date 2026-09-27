#!/bin/sh
set -eu
# Dedicated local cluster only. Keep user/global kubeconfig untouched.
kind_bin=${KIND_BIN:-.local/tools/kind}
if ! "$kind_bin" get clusters | grep -qx professional-network-mvp; then
  "$kind_bin" create cluster --name professional-network-mvp --config infra/kind.yaml --kubeconfig .local/kubeconfig --wait 120s
fi
# Docker's containerd image store can retain multi-platform indexes with only the
# host layers present. Export the host platform explicitly before kind imports it.
case "$(uname -m)" in arm64|aarch64) platform=linux/arm64;; *) platform=linux/amd64;; esac
# Compose pulls digest references; a clean Docker store may have no tag aliases.
# Create aliases only from the pinned images already pulled by Compose.
python3 - <<'PYTHON'
import json, subprocess
from pathlib import Path
items = json.loads(Path('infra/k8s/infrastructure.json').read_text())['items']
images = {c['image'] for item in items if item['kind'] == 'Deployment'
          for c in item['spec']['template']['spec']['containers']}
for image in sorted(images):
    subprocess.run(['docker', 'image', 'tag', image, image.split('@')[0]], check=True)
PYTHON
docker image save --platform "$platform" -o .local/kind-images.tar \
  gvenzl/oracle-free:23.9-slim-faststart apache/kafka:4.1.2 quay.io/keycloak/keycloak:26.7.4 \
  professional-network/api-gateway:0.1.0 professional-network/member-service:0.1.0 \
  professional-network/content-service:0.1.0 professional-network/notification-service:0.1.0
"$kind_bin" load image-archive .local/kind-images.tar --name professional-network-mvp
scripts/kubectl-local.sh create namespace network-mvp --dry-run=client -o yaml | scripts/kubectl-local.sh apply -f -
scripts/kubectl-local.sh -n network-mvp create secret generic network-secrets --from-env-file=.env --dry-run=client -o yaml | scripts/kubectl-local.sh apply -f -
scripts/kubectl-local.sh apply -f infra/k8s/infrastructure.json
for svc in oracle kafka keycloak; do scripts/kubectl-local.sh -n network-mvp rollout status deployment/$svc --timeout=300s; done
scripts/kubectl-local.sh apply -f infra/k8s/migrations.json
scripts/kubectl-local.sh -n network-mvp wait --for=condition=complete job --all --timeout=300s
scripts/kubectl-local.sh apply -f infra/k8s/applications.json
for svc in member-service content-service notification-service api-gateway; do scripts/kubectl-local.sh -n network-mvp rollout status deployment/$svc --timeout=180s; done
