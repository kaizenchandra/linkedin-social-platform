ALTER TABLE media_objects DROP CONSTRAINT ck_media_type;
ALTER TABLE media_objects ADD CONSTRAINT ck_media_type CHECK(resource_type IN ('PROFILE','POST','COMPANY'));
