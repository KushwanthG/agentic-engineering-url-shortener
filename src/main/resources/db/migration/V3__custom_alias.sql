-- V3: custom-alias capability (GF-001, contract 1.1.0). Additive: existing links default to generated codes.
ALTER TABLE short_link ADD COLUMN custom_alias BOOLEAN NOT NULL DEFAULT FALSE;
