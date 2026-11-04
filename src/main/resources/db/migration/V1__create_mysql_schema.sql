CREATE TABLE lessons (
    id VARCHAR(36) NOT NULL,
    number VARCHAR(32) NOT NULL,
    sort_order BIGINT UNSIGNED NOT NULL,
    title VARCHAR(200) NOT NULL,
    title_zh VARCHAR(200) NULL,
    category VARCHAR(60) NOT NULL,
    level VARCHAR(40) NOT NULL,
    duration DOUBLE NOT NULL,
    audio VARCHAR(1024) NULL,
    cover VARCHAR(1024) NULL,
    summary VARCHAR(1000) NULL,
    summary_zh VARCHAR(1000) NULL,
    updated_at VARCHAR(40) NOT NULL,
    transcript JSON NOT NULL,
    words_text VARCHAR(5000) NULL,
    key_phrases JSON NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_lessons_sort_order (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE categories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name_key VARCHAR(240) NOT NULL,
    name VARCHAR(60) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_categories_name_key (name_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE users (
    id VARCHAR(36) NOT NULL,
    username VARCHAR(30) NOT NULL,
    username_key VARCHAR(120) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at VARCHAR(40) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username_key (username_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE lesson_number_sequence (
    id TINYINT UNSIGNED NOT NULL,
    next_number INT UNSIGNED NOT NULL,
    next_sort_order BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_lesson_number_sequence_singleton CHECK (id = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

INSERT INTO lesson_number_sequence (id, next_number, next_sort_order) VALUES (1, 1, 1);

CREATE TABLE data_import_manifest (
    manifest_id CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    source_counts JSON NOT NULL,
    started_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at TIMESTAMP(6) NULL,
    PRIMARY KEY (manifest_id),
    CONSTRAINT chk_data_import_manifest_status CHECK (status IN ('IN_PROGRESS', 'COMPLETE', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
