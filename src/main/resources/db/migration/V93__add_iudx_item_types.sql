-- Items migrated from the IUDX v1 catalogue keep their iudx: type prefix
-- (iudx:DataBank, iudx:AiModel, iudx:Apps) alongside the adex: types, so
-- access requests, app constraints and delegation scope constraints must
-- accept them too.
ALTER TYPE _asset_type
    ADD VALUE IF NOT EXISTS 'iudx:DataBank';

ALTER TYPE _asset_type
    ADD VALUE IF NOT EXISTS 'iudx:AiModel';

ALTER TYPE entity_type_enum
    ADD VALUE IF NOT EXISTS 'iudx:AiModel';

ALTER TYPE entity_type_enum
    ADD VALUE IF NOT EXISTS 'iudx:DataBank';

ALTER TYPE entity_type_enum
    ADD VALUE IF NOT EXISTS 'iudx:Apps';

ALTER TYPE delegation_entity_type
    ADD VALUE IF NOT EXISTS 'iudx:AiModel';

ALTER TYPE delegation_entity_type
    ADD VALUE IF NOT EXISTS 'iudx:DataBank';

ALTER TYPE delegation_entity_type
    ADD VALUE IF NOT EXISTS 'iudx:Apps';

-- V35 missed a comma between 'APPS' and 'ASSET' in entity_type, so Postgres
-- concatenated them into a single 'APPSASSET' label. Add the two intended
-- labels; 'APPSASSET' is left in place since enum labels can't be dropped
-- without recreating the type.
ALTER TYPE entity_type
    ADD VALUE IF NOT EXISTS 'APPS';

ALTER TYPE entity_type
    ADD VALUE IF NOT EXISTS 'ASSET';
