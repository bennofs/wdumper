-- the _count fields in the dump table should be BIGINT
ALTER TABLE `dump` MODIFY COLUMN `entity_count` BIGINT NOT NULL DEFAULT 0;
ALTER TABLE `dump` MODIFY COLUMN `statement_count` BIGINT NOT NULL;
ALTER TABLE `dump` MODIFY COLUMN `triple_count` BIGINT NOT NULL;