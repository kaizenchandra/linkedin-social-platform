ALTER TABLE members ADD (summary_text CLOB);
UPDATE members SET summary_text=summary;
ALTER TABLE members DROP COLUMN summary;
ALTER TABLE members RENAME COLUMN summary_text TO summary;
