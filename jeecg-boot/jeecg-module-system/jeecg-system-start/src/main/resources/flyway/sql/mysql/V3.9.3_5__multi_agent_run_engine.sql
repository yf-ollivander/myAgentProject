CREATE TABLE `ai_run` (
  `id` varchar(36) NOT NULL,
  `create_by` varchar(50) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `update_time` datetime(3) DEFAULT NULL,
  `sys_org_code` varchar(64) DEFAULT NULL,
  `tenant_id` varchar(32) NOT NULL DEFAULT '0',
  `del_flag` tinyint(1) NOT NULL DEFAULT 0,
  `request_id` varchar(64) NOT NULL,
  `request_payload_hash` char(64) NOT NULL,
  `run_type` varchar(16) NOT NULL,
  `source` varchar(16) NOT NULL,
  `source_event_id` varchar(128) DEFAULT NULL,
  `source_bot_id` varchar(36) DEFAULT NULL,
  `source_chat_id` varchar(100) DEFAULT NULL,
  `source_thread_id` varchar(100) DEFAULT NULL,
  `initiator_username` varchar(50) NOT NULL,
  `pipeline_id` varchar(36) DEFAULT NULL,
  `pipeline_version_id` varchar(36) DEFAULT NULL,
  `pipeline_version` int DEFAULT NULL,
  `definition_hash` char(64) NOT NULL,
  `definition_json` longtext NOT NULL,
  `input_json` longtext NOT NULL,
  `workspace_context_json` longtext NOT NULL,
  `status` varchar(16) NOT NULL,
  `retry_of_run_id` varchar(36) DEFAULT NULL,
  `root_run_id` varchar(36) NOT NULL,
  `cancel_request_id` varchar(64) DEFAULT NULL,
  `cancel_reason` varchar(500) DEFAULT NULL,
  `event_sequence` bigint NOT NULL DEFAULT 0,
  `started_at` datetime(3) DEFAULT NULL,
  `ended_at` datetime(3) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_run_tenant_request` (`tenant_id`,`request_id`),
  UNIQUE KEY `uk_ai_run_source_event` (`tenant_id`,`source`,`source_event_id`),
  KEY `idx_ai_run_tenant_status` (`tenant_id`,`status`,`create_time`),
  KEY `idx_ai_run_pipeline` (`tenant_id`,`pipeline_id`,`pipeline_version_id`),
  KEY `idx_ai_run_retry_of` (`retry_of_run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ai_node_run` (
  `id` varchar(36) NOT NULL, `tenant_id` varchar(32) NOT NULL,
  `run_id` varchar(36) NOT NULL, `node_id` varchar(64) NOT NULL,
  `stage_code` varchar(64) DEFAULT NULL, `node_type` varchar(16) NOT NULL,
  `status` varchar(16) NOT NULL, `incoming_count` int NOT NULL DEFAULT 0,
  `resolved_incoming_count` int NOT NULL DEFAULT 0, `selected_incoming_count` int NOT NULL DEFAULT 0,
  `attempt_no` int NOT NULL DEFAULT 0, `retry_count` int NOT NULL DEFAULT 0,
  `manual_retry_count` int NOT NULL DEFAULT 0, `resume_generation` int NOT NULL DEFAULT 0,
  `dispatch_version` bigint NOT NULL DEFAULT 0, `dispatch_count` int NOT NULL DEFAULT 0,
  `lease_owner` varchar(128) DEFAULT NULL, `lease_until` datetime(3) DEFAULT NULL,
  `next_retry_at` datetime(3) DEFAULT NULL, `input_json` longtext DEFAULT NULL,
  `output_json` longtext DEFAULT NULL, `result_summary` varchar(1000) DEFAULT NULL,
  `selected_branch` varchar(8) DEFAULT NULL, `error_code` varchar(64) DEFAULT NULL,
  `error_message` varchar(1000) DEFAULT NULL, `started_at` datetime(3) DEFAULT NULL,
  `ended_at` datetime(3) DEFAULT NULL, `version` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_ai_node_run_node` (`run_id`,`node_id`),
  KEY `idx_ai_node_run_status` (`tenant_id`,`status`,`next_retry_at`),
  KEY `idx_ai_node_run_lease` (`status`,`lease_until`), KEY `idx_ai_node_run_run` (`run_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ai_run_event` (
  `id` varchar(36) NOT NULL, `tenant_id` varchar(32) NOT NULL,
  `run_id` varchar(36) NOT NULL, `node_run_id` varchar(36) DEFAULT NULL,
  `sequence` bigint NOT NULL, `event_type` varchar(48) NOT NULL,
  `from_status` varchar(16) DEFAULT NULL, `to_status` varchar(16) DEFAULT NULL,
  `summary` varchar(1000) DEFAULT NULL, `error_code` varchar(64) DEFAULT NULL,
  `trace_id` varchar(64) NOT NULL, `payload_json` longtext DEFAULT NULL,
  `create_time` datetime(3) NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_ai_run_event_sequence` (`run_id`,`sequence`),
  KEY `idx_ai_run_event_tenant_run` (`tenant_id`,`run_id`,`create_time`),
  KEY `idx_ai_run_event_node` (`node_run_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ai_outbox` (
  `id` varchar(36) NOT NULL, `tenant_id` varchar(32) NOT NULL,
  `event_id` varchar(64) NOT NULL, `destination` varchar(16) NOT NULL,
  `event_type` varchar(48) NOT NULL, `aggregate_id` varchar(36) NOT NULL,
  `payload_json` longtext NOT NULL, `status` varchar(16) NOT NULL,
  `retry_count` int NOT NULL DEFAULT 0, `next_retry_at` datetime(3) NOT NULL,
  `claim_token` varchar(64) DEFAULT NULL, `claimed_until` datetime(3) DEFAULT NULL,
  `last_error` varchar(1000) DEFAULT NULL, `create_time` datetime(3) NOT NULL,
  `update_time` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_ai_outbox_event` (`event_id`),
  KEY `idx_ai_outbox_dispatch` (`status`,`next_retry_at`,`claimed_until`),
  KEY `idx_ai_outbox_aggregate` (`tenant_id`,`aggregate_id`,`event_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ai_artifact` (
  `id` varchar(36) NOT NULL, `tenant_id` varchar(32) NOT NULL,
  `run_id` varchar(36) NOT NULL, `node_run_id` varchar(36) NOT NULL,
  `artifact_type` varchar(32) NOT NULL, `name` varchar(200) NOT NULL,
  `uri` varchar(2000) DEFAULT NULL, `content_json` longtext DEFAULT NULL,
  `checksum` char(64) DEFAULT NULL, `artifact_version` varchar(64) NOT NULL,
  `metadata_json` longtext NOT NULL, `size_bytes` bigint NOT NULL,
  `create_time` datetime(3) NOT NULL,
  PRIMARY KEY (`id`), KEY `idx_ai_artifact_run` (`tenant_id`,`run_id`,`create_time`),
  KEY `idx_ai_artifact_node` (`node_run_id`,`artifact_type`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ai_run_intervention` (
  `id` varchar(36) NOT NULL, `tenant_id` varchar(32) NOT NULL,
  `run_id` varchar(36) NOT NULL, `node_run_id` varchar(36) NOT NULL,
  `intervention_type` varchar(32) NOT NULL, `status` varchar(16) NOT NULL,
  `prompt` varchar(1000) NOT NULL, `allowed_actions_json` varchar(500) NOT NULL,
  `resume_token` varchar(64) NOT NULL, `resolve_request_id` varchar(64) DEFAULT NULL,
  `source_message_id` varchar(128) DEFAULT NULL, `resolved_action` varchar(24) DEFAULT NULL,
  `resume_input_json` longtext DEFAULT NULL, `resolved_by` varchar(50) DEFAULT NULL,
  `resolved_at` datetime(3) DEFAULT NULL, `create_time` datetime(3) NOT NULL,
  `open_node_run_id` varchar(36) GENERATED ALWAYS AS
    (CASE WHEN `status`='OPEN' THEN `node_run_id` ELSE NULL END) STORED,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_ai_intervention_resume` (`resume_token`),
  UNIQUE KEY `uk_ai_intervention_open_node` (`open_node_run_id`),
  UNIQUE KEY `uk_ai_intervention_request` (`tenant_id`,`resolve_request_id`),
  UNIQUE KEY `uk_ai_intervention_source_message` (`tenant_id`,`source_message_id`),
  KEY `idx_ai_intervention_run` (`tenant_id`,`run_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ai_run_dependency` (
  `id` varchar(36) NOT NULL, `tenant_id` varchar(32) NOT NULL,
  `run_id` varchar(36) NOT NULL, `dependency_type` varchar(16) NOT NULL,
  `dependency_id` varchar(36) NOT NULL, `create_time` datetime(3) NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_ai_run_dependency` (`run_id`,`dependency_type`,`dependency_id`),
  KEY `idx_ai_run_dependency_lookup` (`tenant_id`,`dependency_type`,`dependency_id`),
  KEY `idx_ai_run_dependency_run` (`run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `sys_permission`
(`id`,`parent_id`,`name`,`url`,`component`,`is_route`,`component_name`,`redirect`,`menu_type`,`perms`,`perms_type`,`sort_no`,`always_show`,`icon`,`is_leaf`,`keep_alive`,`hidden`,`hide_tab`,`description`,`create_by`,`create_time`,`update_by`,`update_time`,`del_flag`,`rule_flag`,`status`,`internal_or_external`)
VALUES
('2026071300000000051','2026071300000000001','运行查询',NULL,NULL,0,NULL,NULL,2,'ai:run:list','1',51,0,NULL,1,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0),
('2026071300000000052','2026071300000000001','启动运行',NULL,NULL,0,NULL,NULL,2,'ai:run:start','1',52,0,NULL,1,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0),
('2026071300000000053','2026071300000000001','取消运行',NULL,NULL,0,NULL,NULL,2,'ai:run:cancel','1',53,0,NULL,1,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0),
('2026071300000000054','2026071300000000001','重试运行',NULL,NULL,0,NULL,NULL,2,'ai:run:retry','1',54,0,NULL,1,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0),
('2026071300000000055','2026071300000000001','处理介入',NULL,NULL,0,NULL,NULL,2,'ai:run:intervene','1',55,0,NULL,1,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0);

INSERT INTO `sys_role_permission` (`id`,`role_id`,`permission_id`,`data_rule_ids`,`operate_date`,`operate_ip`)
SELECT MD5(CONCAT('f6817f48af4fb3af11b9e8bf182f618b', `id`)), 'f6817f48af4fb3af11b9e8bf182f618b', `id`, NULL, NOW(), '127.0.0.1'
FROM `sys_permission` WHERE `id` BETWEEN '2026071300000000051' AND '2026071300000000055';
