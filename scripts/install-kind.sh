#!/bin/sh
set -eu
case "$(uname -s)-$(uname -m)" in
 Darwin-arm64) platform=darwin-arm64;; Darwin-x86_64) platform=darwin-amd64;;
 Linux-aarch64|Linux-arm64) platform=linux-arm64;; Linux-x86_64) platform=linux-amd64;;
 *) echo 'Unsupported local kind platform' >&2; exit 1;;
esac
mkdir -p .local/tools
curl -fsSL "https://github.com/kubernetes-sigs/kind/releases/download/v0.33.0/kind-$platform" -o ".local/tools/kind-$platform"
curl -fsSL "https://github.com/kubernetes-sigs/kind/releases/download/v0.33.0/kind-$platform.sha256sum" -o ".local/tools/kind-$platform.sha256sum"
(cd .local/tools && shasum -a 256 -c "kind-$platform.sha256sum")
cp ".local/tools/kind-$platform" .local/tools/kind
chmod +x .local/tools/kind
