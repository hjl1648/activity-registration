-- 限量活动报名系统 DDL + 固定数据
-- 引擎必须用 InnoDB 以保证事务

CREATE DATABASE IF NOT EXISTS activity_reg
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE activity_reg;

DROP TABLE IF EXISTS registrations;
DROP TABLE IF EXISTS activities;

CREATE TABLE activities (
  id              BIGINT       NOT NULL PRIMARY KEY,
  title           VARCHAR(128) NOT NULL,
  status          VARCHAR(16)  NOT NULL COMMENT 'OPEN / CLOSED',
  total_quota     INT          NOT NULL,
  remaining_quota INT          NOT NULL,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  CONSTRAINT chk_remaining_non_neg CHECK (remaining_quota >= 0),
  CONSTRAINT chk_remaining_le_total CHECK (remaining_quota <= total_quota)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE registrations (
  id           VARCHAR(64)  NOT NULL PRIMARY KEY,
  activity_id  BIGINT       NOT NULL,
  user_id      BIGINT       NOT NULL,
  request_id   VARCHAR(64)  NOT NULL,
  status       VARCHAR(32)  NOT NULL DEFAULT 'REGISTERED',
  created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT uk_user_activity UNIQUE (user_id, activity_id),
  CONSTRAINT uk_user_request  UNIQUE (user_id, request_id),
  CONSTRAINT fk_reg_activity FOREIGN KEY (activity_id) REFERENCES activities(id),
  INDEX idx_reg_user_created (user_id, created_at DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO activities (id, title, status, total_quota, remaining_quota) VALUES
(1001, 'Java 实战分享',           'OPEN',   5, 5),
(1002, '并发验收专用活动',         'OPEN',  10, 10),
(1003, '零名额活动',               'OPEN',   0, 0),
(1004, '已关闭活动',               'CLOSED', 5, 5),
(1005, '请求键冲突验证活动',       'OPEN',   5, 5),
(1006, '同用户不同键并发活动',     'OPEN',   5, 5),
(1007, '同用户相同键并发活动',     'OPEN',   5, 5);
