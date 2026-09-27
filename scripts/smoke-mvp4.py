#!/usr/bin/env python3
"""Gateway journey; run auth-hiring-setup.py first. Compose default, MVP4_BACKEND=kind supported."""
import os,subprocess,sys
for script in ['check-mvp4-following','check-mvp4-feed-saved','check-mvp4-discovery','check-mvp4-alerts']:
 subprocess.run([sys.executable,'scripts/refresh-session.py'],env={**os.environ,'TEST_SESSION':'.local/hiring-session.json'},check=True)
 subprocess.run([sys.executable,'scripts/'+script+'.py'],check=True)
print('PASS complete MVP4 gateway discovery/saved-items/alerts journey')
