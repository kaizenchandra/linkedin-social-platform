-- Complete composite FK index prevents parent membership deletion acquiring
-- a table-wide dependency lock while another transaction waits for its company row.
CREATE INDEX ix_company_owner_fk ON companies(id,owner_id);
