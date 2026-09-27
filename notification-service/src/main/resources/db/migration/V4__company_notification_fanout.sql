BEGIN
 FOR c IN (SELECT c.constraint_name FROM user_constraints c
           JOIN user_cons_columns col ON c.constraint_name=col.constraint_name
           WHERE c.table_name='NOTIFICATIONS' AND c.constraint_type='U'
           GROUP BY c.constraint_name HAVING COUNT(*)=1 AND MAX(col.column_name)='EVENT_ID') LOOP
  EXECUTE IMMEDIATE 'ALTER TABLE notifications DROP CONSTRAINT ' || DBMS_ASSERT.SIMPLE_SQL_NAME(c.constraint_name);
 END LOOP;
END;
/
ALTER TABLE notifications ADD CONSTRAINT uq_notification_recipient UNIQUE(event_id,recipient_id);
