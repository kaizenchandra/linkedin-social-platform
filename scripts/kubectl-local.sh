#!/bin/sh
set -eu
exec kubectl --kubeconfig "$PWD/.local/kubeconfig" --context kind-professional-network-mvp "$@"
