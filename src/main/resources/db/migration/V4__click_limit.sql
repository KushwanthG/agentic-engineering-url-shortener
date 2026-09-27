-- V4: click-limit capability (BF-001, contract 1.2.0). Additive and nullable: existing links stay unlimited.
ALTER TABLE short_link ADD COLUMN max_clicks BIGINT NULL;
ALTER TABLE short_link ADD CONSTRAINT ck_short_link_max_clicks CHECK (max_clicks IS NULL OR max_clicks BETWEEN 1 AND 1000000);
