#!/usr/bin/env python3
import json
from pathlib import Path
count=0
for path in Path('infra/k8s').glob('*.json'):
 for item in json.loads(path.read_text())['items']:
  assert item['apiVersion'] and item['kind'] and item['metadata']['name'];count+=1
  if item['kind']=='Deployment':
   pod=item['spec']['template']['spec'];assert pod['automountServiceAccountToken'] is False
   for c in pod['containers']:
    assert ':latest' not in c['image'];assert c['resources']['requests'];assert c['resources']['limits'];assert c['readinessProbe'];assert c['livenessProbe']
print(f'PASS: {count} Kubernetes resources structurally checked; server-side validation is a separate deployment gate')
