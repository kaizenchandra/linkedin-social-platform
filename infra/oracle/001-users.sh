#!/bin/bash
set -euo pipefail
for service in member content notification; do
  upper=${service^^}
  owner_key=${upper}_DB_PASSWORD
  runtime_key=${upper}_RUNTIME_PASSWORD
  sqlplus -s / as sysdba <<SQL
WHENEVER SQLERROR EXIT SQL.SQLCODE
ALTER SESSION SET CONTAINER=FREEPDB1;
CREATE USER ${service}_app IDENTIFIED BY "${!owner_key}" QUOTA 100M ON USERS;
GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE TO ${service}_app;
CREATE USER ${service}_runtime IDENTIFIED BY "${!runtime_key}";
GRANT CREATE SESSION TO ${service}_runtime;
SQL
done
