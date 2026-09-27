#!/usr/bin/env python3
import os,subprocess
scope={**os.environ,'TEST_SESSION':'.local/hiring-session.json','TEST_MEMBER_NAMES':'owner,recruiter,alice,bob,outsider'}
for script in ['auth-test-setup.py','auth-moderator-setup.py']:subprocess.run(['python3','scripts/'+script],env=scope,check=True)
