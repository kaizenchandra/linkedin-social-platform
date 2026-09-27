#!/usr/bin/env python3
"""Gateway release journey; create isolated hiring identities first."""
import subprocess,sys
for script in ['check-mvp3-companies','check-mvp3-jobs','check-mvp3-applications','check-mvp3-events-moderation']:
 subprocess.run([sys.executable,'scripts/'+script+'.py'],check=True)
print('PASS: complete MVP-3 gateway journey; recovery/replay/restart/upgrade are separate executable gates')
