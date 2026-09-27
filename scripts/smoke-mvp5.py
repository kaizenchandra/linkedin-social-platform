#!/usr/bin/env python3
"""Reusable gateway live-update journey with isolated identities per fixture.
Optional failure tests are disruptive and restricted to the dedicated local Compose stack.
"""
import argparse,os,subprocess,sys
parser=argparse.ArgumentParser()
parser.add_argument('--failure-tests',action='store_true',help='Also run dedicated Compose outages, stopped-reader/retention/admission, and mute-authority recovery; requires Prometheus')
args=parser.parse_args()
checks=['check-mvp5-messaging','check-mvp5-controls']
if args.failure_tests:
 if os.getenv('MVP4_BACKEND')=='kind':parser.error('Failure fixture flag is Compose-only; use check-mvp5-rolling.py for kind')
 checks+=['check-mvp5-failures','check-mvp5-limits','check-mvp5-mute-recovery']
for check in checks:
 subprocess.run([sys.executable,'scripts/auth-hiring-setup.py'],check=True)
 subprocess.run([sys.executable,'scripts/'+check+'.py'],check=True)
subprocess.run([sys.executable,'scripts/check-mvp5-expiry.py'],check=True)
print('PASS MVP5 gateway live updates, owner read synchronization, replay, private controls and real token expiry')
