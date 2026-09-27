#!/usr/bin/env python3
"""Use only the dedicated Compose member schema for local integration verification."""
import os,subprocess
from pathlib import Path
e=os.environ.copy();v=dict(x.split('=',1) for x in Path('.env').read_text().splitlines() if '=' in x and not x.startswith('#'));e.update(TEST_DB_URL='jdbc:oracle:thin:@//localhost:1521/FREEPDB1',TEST_DB_USER='member_app',TEST_CONTENT_DB_USER='content_app',TEST_NOTIFICATION_DB_USER='notification_app',TEST_DB_PASSWORD=v['MEMBER_DB_PASSWORD'],TEST_CONTENT_DB_PASSWORD=v['CONTENT_DB_PASSWORD'],TEST_NOTIFICATION_DB_PASSWORD=v['NOTIFICATION_DB_PASSWORD'])
e.update(TEST_MESSAGING_DB_USER='messaging_app',TEST_MESSAGING_DB_PASSWORD=v['MESSAGING_DB_PASSWORD'],TEST_MEDIA_DB_USER='media_app',TEST_MEDIA_DB_PASSWORD=v['MEDIA_DB_PASSWORD'],S3_ACCESS_KEY=v['MEDIA_S3_ACCESS_KEY'],S3_SECRET_KEY=v['MEDIA_S3_SECRET_KEY'])
e.update(TEST_HIRING_DB_USER='hiring_app',TEST_HIRING_DB_PASSWORD=v['HIRING_DB_PASSWORD'])
raise SystemExit(subprocess.call(['scripts/java21.sh','-B','-ntp','clean','verify'],env=e))
