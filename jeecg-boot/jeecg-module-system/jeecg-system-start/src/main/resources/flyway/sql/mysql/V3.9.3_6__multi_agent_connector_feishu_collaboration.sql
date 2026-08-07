-- Module 04 is forward-only: existing Connector rows remain LEGACY until explicitly upgraded.
ALTER TABLE `ai_connector`
  MODIFY COLUMN `response_mapping` varchar(1000) NULL,
  ADD COLUMN `result_contract_version` varchar(16) NOT NULL DEFAULT 'LEGACY' AFTER `response_mapping`;

-- This unique key intentionally makes Flyway fail if duplicate App IDs were not cleaned up first.
ALTER TABLE `ai_feishu_bot`
  ADD UNIQUE KEY `uk_ai_feishu_bot_app_id` (`app_id`);

CREATE TABLE `ai_feishu_user_binding` (
  `id` varchar(36) NOT NULL,
  `create_by` varchar(50) DEFAULT NULL, `create_time` datetime(3) NOT NULL,
  `update_by` varchar(50) DEFAULT NULL, `update_time` datetime(3) DEFAULT NULL,
  `sys_org_code` varchar(64) DEFAULT NULL, `tenant_id` varchar(32) NOT NULL DEFAULT '0',
  `del_flag` tinyint(1) NOT NULL DEFAULT 0,
  `bot_id` varchar(36) NOT NULL, `sender_open_id` varchar(128) NOT NULL,
  `user_id` varchar(36) NOT NULL, `username` varchar(50) NOT NULL,
  `binding_source` varchar(16) NOT NULL, `enabled` tinyint(1) NOT NULL DEFAULT 1,
  `last_used_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_feishu_binding_sender` (`bot_id`,`sender_open_id`),
  UNIQUE KEY `uk_ai_feishu_binding_user` (`bot_id`,`tenant_id`,`user_id`),
  KEY `idx_ai_feishu_binding_tenant` (`tenant_id`,`enabled`,`username`),
  KEY `idx_ai_feishu_binding_bot` (`bot_id`,`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ai_feishu_binding_token` (
  `id` varchar(36) NOT NULL, `tenant_id` varchar(32) NOT NULL,
  `bot_id` varchar(36) NOT NULL, `user_id` varchar(36) NOT NULL,
  `username` varchar(50) NOT NULL, `token_hash` char(64) NOT NULL,
  `status` varchar(16) NOT NULL, `expires_at` datetime(3) NOT NULL,
  `used_by_open_id` varchar(128) DEFAULT NULL, `used_at` datetime(3) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL,
  `active_token_key` varchar(140) GENERATED ALWAYS AS
    (CASE WHEN `status`='ACTIVE' THEN CONCAT(`bot_id`,':',`tenant_id`,':',`user_id`) ELSE NULL END) STORED,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_ai_feishu_binding_token_hash` (`token_hash`),
  UNIQUE KEY `uk_ai_feishu_binding_active` (`active_token_key`),
  KEY `idx_ai_feishu_binding_token_expiry` (`status`,`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ai_feishu_inbound_event` (
  `id` varchar(36) NOT NULL, `tenant_id` varchar(32) NOT NULL,
  `event_key` varchar(200) NOT NULL, `bot_id` varchar(36) NOT NULL,
  `event_type` varchar(16) NOT NULL, `message_id` varchar(128) DEFAULT NULL,
  `sender_open_id` varchar(128) NOT NULL, `payload_cipher` longtext DEFAULT NULL,
  `payload_hash` char(64) NOT NULL, `status` varchar(16) NOT NULL,
  `claim_token` varchar(64) DEFAULT NULL, `claimed_until` datetime(3) DEFAULT NULL,
  `retry_count` int NOT NULL DEFAULT 0, `next_retry_at` datetime(3) NOT NULL,
  `result_type` varchar(32) DEFAULT NULL, `run_id` varchar(36) DEFAULT NULL,
  `intervention_id` varchar(36) DEFAULT NULL, `last_error` varchar(1000) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL, `update_time` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_ai_feishu_inbound_event` (`event_key`),
  KEY `idx_ai_feishu_inbound_claim` (`status`,`next_retry_at`,`claimed_until`),
  KEY `idx_ai_feishu_inbound_sender` (`tenant_id`,`bot_id`,`sender_open_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ai_feishu_session` (
  `id` varchar(36) NOT NULL, `tenant_id` varchar(32) NOT NULL,
  `bot_id` varchar(36) NOT NULL, `chat_id` varchar(128) NOT NULL,
  `thread_key` varchar(128) NOT NULL, `root_message_id` varchar(128) NOT NULL,
  `run_id` varchar(36) NOT NULL, `binding_id` varchar(36) NOT NULL,
  `sender_open_id` varchar(128) NOT NULL, `status` varchar(16) NOT NULL,
  `last_message_id` varchar(128) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL, `update_time` datetime(3) DEFAULT NULL,
  `active_session_key` varchar(400) GENERATED ALWAYS AS
    (CASE WHEN `status` IN ('ACTIVE','WAITING')
      THEN CONCAT(`bot_id`,':',`chat_id`,':',`thread_key`) ELSE NULL END) STORED,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_ai_feishu_session_run` (`run_id`),
  UNIQUE KEY `uk_ai_feishu_session_active` (`active_session_key`),
  KEY `idx_ai_feishu_session_lookup` (`tenant_id`,`bot_id`,`chat_id`,`thread_key`,`status`),
  KEY `idx_ai_feishu_session_sender` (`binding_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ai_feishu_delivery` (
  `id` varchar(36) NOT NULL, `tenant_id` varchar(32) NOT NULL,
  `event_id` varchar(64) NOT NULL, `run_id` varchar(36) DEFAULT NULL,
  `event_type` varchar(48) NOT NULL, `event_sequence` bigint DEFAULT NULL,
  `intervention_id` varchar(36) DEFAULT NULL, `bot_id` varchar(36) NOT NULL,
  `target_type` varchar(16) NOT NULL, `target_id` varchar(128) NOT NULL,
  `message_type` varchar(16) NOT NULL, `content_cipher` longtext NOT NULL,
  `content_hash` char(64) NOT NULL, `status` varchar(16) NOT NULL,
  `claim_token` varchar(64) DEFAULT NULL, `claimed_until` datetime(3) DEFAULT NULL,
  `retry_count` int NOT NULL DEFAULT 0, `next_retry_at` datetime(3) NOT NULL,
  `feishu_message_id` varchar(128) DEFAULT NULL, `last_error` varchar(1000) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL, `update_time` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_ai_feishu_delivery_event` (`event_id`),
  KEY `idx_ai_feishu_delivery_claim` (`status`,`next_retry_at`,`claimed_until`),
  KEY `idx_ai_feishu_delivery_run` (`tenant_id`,`run_id`,`event_sequence`,`status`),
  KEY `idx_ai_feishu_delivery_target` (`bot_id`,`target_type`,`target_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `sys_permission`
(`id`,`parent_id`,`name`,`url`,`component`,`is_route`,`component_name`,`redirect`,`menu_type`,`perms`,`perms_type`,`sort_no`,`always_show`,`icon`,`is_leaf`,`keep_alive`,`hidden`,`hide_tab`,`description`,`create_by`,`create_time`,`update_by`,`update_time`,`del_flag`,`rule_flag`,`status`,`internal_or_external`)
VALUES
('2026071300000000061','2026071300000000004','查看用户绑定',NULL,NULL,0,NULL,NULL,2,'ai:feishu:binding:list','1',61,0,NULL,1,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0),
('2026071300000000062','2026071300000000004','新增用户绑定',NULL,NULL,0,NULL,NULL,2,'ai:feishu:binding:add','1',62,0,NULL,1,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0),
('2026071300000000063','2026071300000000004','禁用用户绑定',NULL,NULL,0,NULL,NULL,2,'ai:feishu:binding:disable','1',63,0,NULL,1,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0),
('2026071300000000064','2026071300000000004','生成本人绑定码',NULL,NULL,0,NULL,NULL,2,'ai:feishu:binding:self','1',64,0,NULL,1,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0);

INSERT INTO `sys_role_permission` (`id`,`role_id`,`permission_id`,`data_rule_ids`,`operate_date`,`operate_ip`)
SELECT MD5(CONCAT('f6817f48af4fb3af11b9e8bf182f618b', `id`)), 'f6817f48af4fb3af11b9e8bf182f618b', `id`, NULL, NOW(), '127.0.0.1'
FROM `sys_permission` WHERE `id` BETWEEN '2026071300000000061' AND '2026071300000000064';
