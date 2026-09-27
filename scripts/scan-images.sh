#!/bin/sh
set -eu
mkdir -p .local/scans
endpoint=$(docker context inspect --format '{{.Endpoints.docker.Host}}')
case "$endpoint" in unix://*) socket=${endpoint#unix://};; *) echo 'Local Unix Docker endpoint required' >&2; exit 1;; esac
for service in member-service content-service notification-service api-gateway; do
 docker run --rm -v "$socket:/var/run/docker.sock" -v "$PWD/.local/scans:/reports" -v professional-network-trivy-cache:/root/.cache/trivy \
  aquasec/trivy:0.74.0@sha256:62b1e65e8869bc4b4c6aa4fa2b21595256c7c2f6018a9d9ad61caf87187c1969 image --quiet --scanners vuln --severity HIGH,CRITICAL --exit-code 1 --format json --output "/reports/$service.json" "professional-network/$service:0.1.0"
done
