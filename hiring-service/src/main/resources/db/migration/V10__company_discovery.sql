CREATE INDEX ix_company_discovery_industry ON companies (LOWER(industry), id);
CREATE INDEX ix_company_discovery_location ON companies (LOWER(location), id);
