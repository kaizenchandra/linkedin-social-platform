#!/bin/bash
set -euo pipefail
for service in hiring; do
  upper=${service^^}
  owner_key=${upper}_DB_PASSWORD
  runtime_key=${upper}_RUNTIME_PASSWORD
  sqlplus -s / as sysdba <<SQL
WHENEVER SQLERROR EXIT SQL.SQLCODE
ALTER SESSION SET CONTAINER=FREEPDB1;
DECLARE n NUMBER;
BEGIN
 SELECT COUNT(*) INTO n FROM dba_users WHERE username=UPPER('${service}_app');
 IF n=0 THEN
  EXECUTE IMMEDIATE 'CREATE USER ${service}_app IDENTIFIED BY "${!owner_key}" QUOTA 500M ON USERS';
  EXECUTE IMMEDIATE 'GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE TO ${service}_app';
 END IF;
 SELECT COUNT(*) INTO n FROM dba_users WHERE username=UPPER('${service}_runtime');
 IF n=0 THEN
  EXECUTE IMMEDIATE 'CREATE USER ${service}_runtime IDENTIFIED BY "${!runtime_key}"';
  EXECUTE IMMEDIATE 'GRANT CREATE SESSION TO ${service}_runtime';
 END IF;
END;
/
SQL
done
