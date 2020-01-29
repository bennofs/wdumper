-- initial schema, exported from live db

CREATE TABLE `run`
(
    `id`           int(11)   NOT NULL AUTO_INCREMENT,
    `started_at`   timestamp NULL     DEFAULT NULL,
    `finished_at`  timestamp NULL     DEFAULT NULL,
    `count`        int(11)   NOT NULL DEFAULT '0',
    `tool_version` text COLLATE utf8mb4_unicode_ci,
    `wdtk_version` text COLLATE utf8mb4_unicode_ci,
    `dump_date`    text COLLATE utf8mb4_unicode_ci,
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `dump`
(
    `id`              int(11)                         NOT NULL AUTO_INCREMENT,
    `title`           text COLLATE utf8mb4_unicode_ci NOT NULL,
    `spec`            text COLLATE utf8mb4_unicode_ci NOT NULL,
    `created_at`      timestamp                       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `run_id`          int(11)                                  DEFAULT NULL,
    `compressed_size` int(11)                         NOT NULL DEFAULT '0',
    `entity_count`    int(11)                         NOT NULL DEFAULT '0',
    `statement_count` int(11)                         NOT NULL DEFAULT '0',
    `triple_count`    int(11)                         NOT NULL DEFAULT '0',
    `description`     text COLLATE utf8mb4_unicode_ci NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_dump_run_id_run` FOREIGN KEY (`run_id`) REFERENCES `run` (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
CREATE INDEX `fk_dump_run_id_run` ON `dump` (`run_id`);
CREATE INDEX `ix_dump_created_at` ON `dump` (`created_at`);

CREATE TABLE `zenodo` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `deposit_id` int(11) NOT NULL,
  `dump_id` int(11) NOT NULL,
  `doi` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `target` enum('SANDBOX','RELEASE') COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `started_at` timestamp NULL DEFAULT NULL,
  `completed_at` timestamp NULL DEFAULT NULL,
  `uploaded_bytes` bigint(20) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_zenodo_dump_id_dump` FOREIGN KEY (`dump_id`) REFERENCES `dump` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=50 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX `fk_zenodo_dump_id_dump` ON `zenodo` (`dump_id`);

CREATE TABLE `dump_error` (
                              `id` int(11) NOT NULL AUTO_INCREMENT,
                              `logged_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              `dump_id` int(11) DEFAULT NULL,
                              `run_id` int(11) DEFAULT NULL,
                              `level` enum('CRITICAL','ERROR','WARNING') COLLATE utf8mb4_unicode_ci NOT NULL,
                              `message` text COLLATE utf8mb4_unicode_ci NOT NULL,
                              `zenodo_id` int(11) DEFAULT NULL,
                              PRIMARY KEY (`id`),
                              CONSTRAINT `fk_dump_error_dump_id_dump` FOREIGN KEY (`dump_id`) REFERENCES `dump` (`id`),
                              CONSTRAINT `fk_dump_error_run_id_run` FOREIGN KEY (`run_id`) REFERENCES `run` (`id`),
                              CONSTRAINT `fk_dump_error_zenodo_id_zenodo` FOREIGN KEY (`zenodo_id`) REFERENCES `zenodo` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX `fk_dump_error_dump_id_dump` ON `dump_error` (`dump_id`);
CREATE INDEX `fk_dump_error_run_id_run` ON `dump_error` (`run_id`);
CREATE INDEX `fk_dump_error_zenodo_id_zenodo` ON `dump_error` (`zenodo_id`);