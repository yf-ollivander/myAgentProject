USE `jeecg-boot`;

CREATE TABLE IF NOT EXISTS `ai_connector` (
  `id` varchar(36) NOT NULL,
  `create_by` varchar(50) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `sys_org_code` varchar(64) DEFAULT NULL,
  `tenant_id` varchar(32) DEFAULT NULL,
  `del_flag` tinyint(1) NOT NULL DEFAULT 0,
  `connector_code` varchar(64) NOT NULL,
  `name` varchar(100) NOT NULL,
  `base_url` varchar(500) NOT NULL,
  `path` varchar(255) NOT NULL,
  `auth_type` varchar(16) NOT NULL DEFAULT 'NONE',
  `auth_header` varchar(100) DEFAULT NULL,
  `request_headers` text,
  `response_mapping` varchar(1000) NOT NULL,
  `secret_cipher` text,
  `connect_timeout` int NOT NULL DEFAULT 10,
  `read_timeout` int NOT NULL DEFAULT 300,
  `enabled` tinyint(1) NOT NULL DEFAULT 0,
  `last_test_status` varchar(16) NOT NULL DEFAULT 'UNTESTED',
  `last_test_message` varchar(500) DEFAULT NULL,
  `last_test_time` datetime DEFAULT NULL,
  `last_test_duration_ms` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_connector_code` (`connector_code`),
  KEY `idx_ai_connector_enabled` (`enabled`),
  KEY `idx_ai_connector_org` (`sys_org_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='HTTP Agent Connector configuration';

CREATE TABLE IF NOT EXISTS `ai_feishu_bot` (
  `id` varchar(36) NOT NULL,
  `create_by` varchar(50) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `sys_org_code` varchar(64) DEFAULT NULL,
  `tenant_id` varchar(32) DEFAULT NULL,
  `del_flag` tinyint(1) NOT NULL DEFAULT 0,
  `bot_key` varchar(64) NOT NULL,
  `name` varchar(100) NOT NULL,
  `app_id` varchar(100) NOT NULL,
  `app_secret_cipher` text,
  `verification_token_cipher` text,
  `encrypt_key_cipher` text,
  `default_chat_id` varchar(100) DEFAULT NULL,
  `entry_mode` varchar(20) NOT NULL DEFAULT 'DIRECT_AGENT',
  `command_enabled` tinyint(1) NOT NULL DEFAULT 0,
  `enabled` tinyint(1) NOT NULL DEFAULT 0,
  `last_test_status` varchar(16) NOT NULL DEFAULT 'UNTESTED',
  `last_test_message` varchar(500) DEFAULT NULL,
  `last_test_time` datetime DEFAULT NULL,
  `last_test_duration_ms` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_feishu_bot_key` (`bot_key`),
  KEY `idx_ai_feishu_bot_entry_mode` (`entry_mode`, `enabled`, `command_enabled`),
  KEY `idx_ai_feishu_bot_enabled` (`enabled`),
  KEY `idx_ai_feishu_bot_org` (`sys_org_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Feishu bot configuration';

CREATE TABLE IF NOT EXISTS `ai_agent` (
  `id` varchar(36) NOT NULL,
  `create_by` varchar(50) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `sys_org_code` varchar(64) DEFAULT NULL,
  `tenant_id` varchar(32) DEFAULT NULL,
  `del_flag` tinyint(1) NOT NULL DEFAULT 0,
  `agent_code` varchar(64) NOT NULL,
  `name` varchar(100) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `system_prompt` longtext,
  `connector_id` varchar(36) NOT NULL,
  `feishu_bot_id` varchar(36) DEFAULT NULL,
  `timeout_seconds` int NOT NULL DEFAULT 300,
  `max_retry` int NOT NULL DEFAULT 0,
  `enabled` tinyint(1) NOT NULL DEFAULT 0,
  `last_test_status` varchar(16) NOT NULL DEFAULT 'UNTESTED',
  `last_test_message` varchar(500) DEFAULT NULL,
  `last_test_time` datetime DEFAULT NULL,
  `last_test_duration_ms` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_agent_code` (`agent_code`),
  KEY `idx_ai_agent_connector` (`connector_id`),
  KEY `idx_ai_agent_feishu_bot` (`feishu_bot_id`),
  KEY `idx_ai_agent_enabled` (`enabled`),
  KEY `idx_ai_agent_org` (`sys_org_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent configuration';

INSERT IGNORE INTO `sys_permission`
(`id`,`parent_id`,`name`,`url`,`component`,`is_route`,`component_name`,`redirect`,`menu_type`,`perms`,`perms_type`,`sort_no`,`always_show`,`icon`,`is_leaf`,`keep_alive`,`hidden`,`hide_tab`,`description`,`create_by`,`create_time`,`update_by`,`update_time`,`del_flag`,`rule_flag`,`status`,`internal_or_external`)
VALUES
('2026071300000000001','','多 Agent 管理','/multi-agent','layouts/default/index',1,'MultiAgentRoot','/multi-agent/agents',0,NULL,'0',2,1,'ant-design:deployment-unit-outlined',0,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0),
('2026071300000000002','2026071300000000001','Agent 管理','/multi-agent/agents','super/multiagent/agent/AiAgentList',1,'AiAgentList',NULL,1,NULL,'0',1,0,'ant-design:robot-outlined',1,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0),
('2026071300000000003','2026071300000000001','HTTP Connector','/multi-agent/connectors','super/multiagent/connector/AiConnectorList',1,'AiConnectorList',NULL,1,NULL,'0',2,0,'ant-design:api-outlined',1,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0),
('2026071300000000004','2026071300000000001','飞书机器人','/multi-agent/feishu-bots','super/multiagent/feishu/AiFeishuBotList',1,'AiFeishuBotList',NULL,1,NULL,'0',3,0,'ant-design:message-outlined',1,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0),
('2026071300000000011','2026071300000000002','查询',NULL,NULL,0,NULL,NULL,2,'ai:agent:list','1',1,0,NULL,1,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0),
('2026071300000000012','2026071300000000002','新增',NULL,NULL,0,NULL,NULL,2,'ai:agent:add','1',2,0,NULL,1,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0),
('2026071300000000013','2026071300000000002','编辑',NULL,NULL,0,NULL,NULL,2,'ai:agent:edit','1',3,0,NULL,1,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0),
('2026071300000000014','2026071300000000002','删除',NULL,NULL,0,NULL,NULL,2,'ai:agent:delete','1',4,0,NULL,1,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0),
('2026071300000000015','2026071300000000002','启用',NULL,NULL,0,NULL,NULL,2,'ai:agent:enable','1',5,0,NULL,1,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0),
('2026071300000000016','2026071300000000002','禁用',NULL,NULL,0,NULL,NULL,2,'ai:agent:disable','1',6,0,NULL,1,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0),
('2026071300000000017','2026071300000000002','测试',NULL,NULL,0,NULL,NULL,2,'ai:agent:test','1',7,0,NULL,1,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0),
('2026071300000000021','2026071300000000003','查询',NULL,NULL,0,NULL,NULL,2,'ai:connector:list','1',1,0,NULL,1,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0),
('2026071300000000022','2026071300000000003','新增',NULL,NULL,0,NULL,NULL,2,'ai:connector:add','1',2,0,NULL,1,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0),
('2026071300000000023','2026071300000000003','编辑',NULL,NULL,0,NULL,NULL,2,'ai:connector:edit','1',3,0,NULL,1,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0),
('2026071300000000024','2026071300000000003','删除',NULL,NULL,0,NULL,NULL,2,'ai:connector:delete','1',4,0,NULL,1,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0),
('2026071300000000025','2026071300000000003','启用',NULL,NULL,0,NULL,NULL,2,'ai:connector:enable','1',5,0,NULL,1,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0),
('2026071300000000026','2026071300000000003','禁用',NULL,NULL,0,NULL,NULL,2,'ai:connector:disable','1',6,0,NULL,1,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0),
('2026071300000000027','2026071300000000003','测试',NULL,NULL,0,NULL,NULL,2,'ai:connector:test','1',7,0,NULL,1,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0),
('2026071300000000031','2026071300000000004','查询',NULL,NULL,0,NULL,NULL,2,'ai:feishu:list','1',1,0,NULL,1,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0),
('2026071300000000032','2026071300000000004','新增',NULL,NULL,0,NULL,NULL,2,'ai:feishu:add','1',2,0,NULL,1,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0),
('2026071300000000033','2026071300000000004','编辑',NULL,NULL,0,NULL,NULL,2,'ai:feishu:edit','1',3,0,NULL,1,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0),
('2026071300000000034','2026071300000000004','删除',NULL,NULL,0,NULL,NULL,2,'ai:feishu:delete','1',4,0,NULL,1,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0),
('2026071300000000035','2026071300000000004','启用',NULL,NULL,0,NULL,NULL,2,'ai:feishu:enable','1',5,0,NULL,1,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0),
('2026071300000000036','2026071300000000004','禁用',NULL,NULL,0,NULL,NULL,2,'ai:feishu:disable','1',6,0,NULL,1,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0),
('2026071300000000037','2026071300000000004','测试',NULL,NULL,0,NULL,NULL,2,'ai:feishu:test','1',7,0,NULL,1,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0);

-- INSERT IGNORE does not repair an existing root row; keep parent-menu clicks on a real page.
UPDATE `sys_permission`
SET `redirect` = '/multi-agent/agents'
WHERE `id` = '2026071300000000001';

INSERT IGNORE INTO `sys_role_permission` (`id`,`role_id`,`permission_id`,`data_rule_ids`,`operate_date`,`operate_ip`)
SELECT MD5(CONCAT('f6817f48af4fb3af11b9e8bf182f618b', `id`)), 'f6817f48af4fb3af11b9e8bf182f618b', `id`, NULL, NOW(), '127.0.0.1'
FROM `sys_permission` WHERE `id` BETWEEN '2026071300000000001' AND '2026071300000000037';

CREATE TABLE IF NOT EXISTS `ai_pipeline` (
  `id` varchar(36) NOT NULL, `create_by` varchar(50) DEFAULT NULL, `create_time` datetime DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL, `update_time` datetime DEFAULT NULL, `sys_org_code` varchar(64) DEFAULT NULL,
  `tenant_id` varchar(32) NOT NULL DEFAULT '0', `del_flag` tinyint(1) NOT NULL DEFAULT 0,
  `pipeline_code` varchar(64) NOT NULL, `name` varchar(100) NOT NULL, `description` varchar(500) DEFAULT NULL,
  `draft_definition_json` longtext NOT NULL, `draft_ui_json` longtext NOT NULL, `draft_revision` bigint NOT NULL DEFAULT 0,
  `notification_bot_id` varchar(36) DEFAULT NULL, `default_feishu_chat_id` varchar(100) DEFAULT NULL,
  `latest_version` int NOT NULL DEFAULT 0, `latest_version_id` varchar(36) DEFAULT NULL, `enabled` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_ai_pipeline_tenant_code` (`tenant_id`,`pipeline_code`),
  KEY `idx_ai_pipeline_tenant_enabled` (`tenant_id`,`enabled`), KEY `idx_ai_pipeline_org` (`sys_org_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ai_pipeline_version` (
  `id` varchar(36) NOT NULL, `pipeline_id` varchar(36) NOT NULL, `tenant_id` varchar(32) NOT NULL,
  `version` int NOT NULL, `source_draft_revision` bigint NOT NULL, `schema_version` varchar(16) NOT NULL,
  `definition_json` longtext NOT NULL, `ui_json` longtext NOT NULL, `definition_hash` char(64) NOT NULL,
  `publish_request_id` varchar(64) NOT NULL, `published_by` varchar(50) NOT NULL, `published_at` datetime NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_ai_pipeline_version` (`pipeline_id`,`version`),
  UNIQUE KEY `uk_ai_pipeline_publish_request` (`pipeline_id`,`publish_request_id`),
  KEY `idx_ai_pipeline_version_tenant` (`tenant_id`,`pipeline_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ai_pipeline_trigger_key` (
  `id` varchar(36) NOT NULL, `tenant_id` varchar(32) NOT NULL, `pipeline_id` varchar(36) NOT NULL,
  `published_version_id` varchar(36) NOT NULL, `key_type` varchar(8) NOT NULL,
  `display_value` varchar(100) NOT NULL, `normalized_value` varchar(100) COLLATE utf8mb4_bin NOT NULL,
  `create_by` varchar(50) DEFAULT NULL, `create_time` datetime NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_ai_pipeline_trigger_key` (`tenant_id`,`normalized_value`),
  KEY `idx_ai_pipeline_trigger_pipeline` (`pipeline_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO `sys_permission`
(`id`,`parent_id`,`name`,`url`,`component`,`is_route`,`component_name`,`redirect`,`menu_type`,`perms`,`perms_type`,`sort_no`,`always_show`,`icon`,`is_leaf`,`keep_alive`,`hidden`,`hide_tab`,`description`,`create_by`,`create_time`,`update_by`,`update_time`,`del_flag`,`rule_flag`,`status`,`internal_or_external`)
VALUES
('2026071300000000005','2026071300000000001','多 Agent 流程','/multi-agent/pipelines','super/multiagent/pipeline/AiPipelineList',1,'AiPipelineList',NULL,1,NULL,'0',4,0,'ant-design:branches-outlined',1,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0),
('2026071300000000006','2026071300000000001','流程设计器','/multi-agent/pipelines/design','super/multiagent/pipeline/AiPipelineDesigner',1,'AiPipelineDesigner',NULL,1,NULL,'0',5,0,NULL,1,0,1,1,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0),
('2026071300000000041','2026071300000000005','查询',NULL,NULL,0,NULL,NULL,2,'ai:pipeline:list','1',1,0,NULL,1,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0),
('2026071300000000042','2026071300000000005','新增',NULL,NULL,0,NULL,NULL,2,'ai:pipeline:add','1',2,0,NULL,1,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0),
('2026071300000000043','2026071300000000005','编辑',NULL,NULL,0,NULL,NULL,2,'ai:pipeline:edit','1',3,0,NULL,1,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0),
('2026071300000000044','2026071300000000005','删除',NULL,NULL,0,NULL,NULL,2,'ai:pipeline:delete','1',4,0,NULL,1,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0),
('2026071300000000045','2026071300000000005','校验',NULL,NULL,0,NULL,NULL,2,'ai:pipeline:validate','1',5,0,NULL,1,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0),
('2026071300000000046','2026071300000000005','发布',NULL,NULL,0,NULL,NULL,2,'ai:pipeline:publish','1',6,0,NULL,1,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0),
('2026071300000000047','2026071300000000005','启用',NULL,NULL,0,NULL,NULL,2,'ai:pipeline:enable','1',7,0,NULL,1,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0),
('2026071300000000048','2026071300000000005','禁用',NULL,NULL,0,NULL,NULL,2,'ai:pipeline:disable','1',8,0,NULL,1,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0);

INSERT IGNORE INTO `sys_role_permission` (`id`,`role_id`,`permission_id`,`data_rule_ids`,`operate_date`,`operate_ip`)
SELECT MD5(CONCAT('f6817f48af4fb3af11b9e8bf182f618b', `id`)), 'f6817f48af4fb3af11b9e8bf182f618b', `id`, NULL, NOW(), '127.0.0.1'
FROM `sys_permission` WHERE `id` BETWEEN '2026071300000000005' AND '2026071300000000048';

CREATE TABLE IF NOT EXISTS `ai_run` (
  `id` varchar(36) NOT NULL, `create_by` varchar(50) DEFAULT NULL, `create_time` datetime(3) NOT NULL,
  `update_by` varchar(50) DEFAULT NULL, `update_time` datetime(3) DEFAULT NULL, `sys_org_code` varchar(64) DEFAULT NULL,
  `tenant_id` varchar(32) NOT NULL DEFAULT '0', `del_flag` tinyint(1) NOT NULL DEFAULT 0,
  `request_id` varchar(64) NOT NULL, `request_payload_hash` char(64) NOT NULL, `run_type` varchar(16) NOT NULL,
  `source` varchar(16) NOT NULL, `source_event_id` varchar(128) DEFAULT NULL, `source_bot_id` varchar(36) DEFAULT NULL,
  `source_chat_id` varchar(100) DEFAULT NULL, `source_thread_id` varchar(100) DEFAULT NULL,
  `initiator_username` varchar(50) NOT NULL, `pipeline_id` varchar(36) DEFAULT NULL,
  `pipeline_version_id` varchar(36) DEFAULT NULL, `pipeline_version` int DEFAULT NULL,
  `definition_hash` char(64) NOT NULL, `definition_json` longtext NOT NULL, `input_json` longtext NOT NULL,
  `workspace_context_json` longtext NOT NULL, `status` varchar(16) NOT NULL,
  `retry_of_run_id` varchar(36) DEFAULT NULL, `root_run_id` varchar(36) NOT NULL,
  `cancel_request_id` varchar(64) DEFAULT NULL, `cancel_reason` varchar(500) DEFAULT NULL,
  `event_sequence` bigint NOT NULL DEFAULT 0, `started_at` datetime(3) DEFAULT NULL,
  `ended_at` datetime(3) DEFAULT NULL, `version` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_ai_run_tenant_request` (`tenant_id`,`request_id`),
  UNIQUE KEY `uk_ai_run_source_event` (`tenant_id`,`source`,`source_event_id`),
  KEY `idx_ai_run_tenant_status` (`tenant_id`,`status`,`create_time`),
  KEY `idx_ai_run_pipeline` (`tenant_id`,`pipeline_id`,`pipeline_version_id`), KEY `idx_ai_run_retry_of` (`retry_of_run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ai_node_run` (
  `id` varchar(36) NOT NULL, `tenant_id` varchar(32) NOT NULL, `run_id` varchar(36) NOT NULL,
  `node_id` varchar(64) NOT NULL, `stage_code` varchar(64) DEFAULT NULL, `node_type` varchar(16) NOT NULL,
  `status` varchar(16) NOT NULL, `incoming_count` int NOT NULL DEFAULT 0,
  `resolved_incoming_count` int NOT NULL DEFAULT 0, `selected_incoming_count` int NOT NULL DEFAULT 0,
  `attempt_no` int NOT NULL DEFAULT 0, `retry_count` int NOT NULL DEFAULT 0,
  `manual_retry_count` int NOT NULL DEFAULT 0, `resume_generation` int NOT NULL DEFAULT 0,
  `dispatch_version` bigint NOT NULL DEFAULT 0, `dispatch_count` int NOT NULL DEFAULT 0,
  `lease_owner` varchar(128) DEFAULT NULL, `lease_until` datetime(3) DEFAULT NULL,
  `next_retry_at` datetime(3) DEFAULT NULL, `input_json` longtext DEFAULT NULL, `output_json` longtext DEFAULT NULL,
  `result_summary` varchar(1000) DEFAULT NULL, `selected_branch` varchar(8) DEFAULT NULL,
  `error_code` varchar(64) DEFAULT NULL, `error_message` varchar(1000) DEFAULT NULL,
  `started_at` datetime(3) DEFAULT NULL, `ended_at` datetime(3) DEFAULT NULL, `version` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_ai_node_run_node` (`run_id`,`node_id`),
  KEY `idx_ai_node_run_status` (`tenant_id`,`status`,`next_retry_at`),
  KEY `idx_ai_node_run_lease` (`status`,`lease_until`), KEY `idx_ai_node_run_run` (`run_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ai_run_event` (
  `id` varchar(36) NOT NULL, `tenant_id` varchar(32) NOT NULL, `run_id` varchar(36) NOT NULL,
  `node_run_id` varchar(36) DEFAULT NULL, `sequence` bigint NOT NULL, `event_type` varchar(48) NOT NULL,
  `from_status` varchar(16) DEFAULT NULL, `to_status` varchar(16) DEFAULT NULL, `summary` varchar(1000) DEFAULT NULL,
  `error_code` varchar(64) DEFAULT NULL, `trace_id` varchar(64) NOT NULL, `payload_json` longtext DEFAULT NULL,
  `create_time` datetime(3) NOT NULL, PRIMARY KEY (`id`), UNIQUE KEY `uk_ai_run_event_sequence` (`run_id`,`sequence`),
  KEY `idx_ai_run_event_tenant_run` (`tenant_id`,`run_id`,`create_time`), KEY `idx_ai_run_event_node` (`node_run_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ai_outbox` (
  `id` varchar(36) NOT NULL, `tenant_id` varchar(32) NOT NULL, `event_id` varchar(64) NOT NULL,
  `destination` varchar(16) NOT NULL, `event_type` varchar(48) NOT NULL, `aggregate_id` varchar(36) NOT NULL,
  `payload_json` longtext NOT NULL, `status` varchar(16) NOT NULL, `retry_count` int NOT NULL DEFAULT 0,
  `next_retry_at` datetime(3) NOT NULL, `claim_token` varchar(64) DEFAULT NULL, `claimed_until` datetime(3) DEFAULT NULL,
  `last_error` varchar(1000) DEFAULT NULL, `create_time` datetime(3) NOT NULL, `update_time` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_ai_outbox_event` (`event_id`),
  KEY `idx_ai_outbox_dispatch` (`status`,`next_retry_at`,`claimed_until`),
  KEY `idx_ai_outbox_aggregate` (`tenant_id`,`aggregate_id`,`event_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ai_artifact` (
  `id` varchar(36) NOT NULL, `tenant_id` varchar(32) NOT NULL, `run_id` varchar(36) NOT NULL,
  `node_run_id` varchar(36) NOT NULL, `artifact_type` varchar(32) NOT NULL, `name` varchar(200) NOT NULL,
  `uri` varchar(2000) DEFAULT NULL, `content_json` longtext DEFAULT NULL, `checksum` char(64) DEFAULT NULL,
  `artifact_version` varchar(64) NOT NULL, `metadata_json` longtext NOT NULL, `size_bytes` bigint NOT NULL,
  `create_time` datetime(3) NOT NULL, PRIMARY KEY (`id`),
  KEY `idx_ai_artifact_run` (`tenant_id`,`run_id`,`create_time`),
  KEY `idx_ai_artifact_node` (`node_run_id`,`artifact_type`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ai_run_intervention` (
  `id` varchar(36) NOT NULL, `tenant_id` varchar(32) NOT NULL, `run_id` varchar(36) NOT NULL,
  `node_run_id` varchar(36) NOT NULL, `intervention_type` varchar(32) NOT NULL, `status` varchar(16) NOT NULL,
  `prompt` varchar(1000) NOT NULL, `allowed_actions_json` varchar(500) NOT NULL, `resume_token` varchar(64) NOT NULL,
  `resolve_request_id` varchar(64) DEFAULT NULL, `source_message_id` varchar(128) DEFAULT NULL,
  `resolved_action` varchar(24) DEFAULT NULL, `resume_input_json` longtext DEFAULT NULL,
  `resolved_by` varchar(50) DEFAULT NULL, `resolved_at` datetime(3) DEFAULT NULL, `create_time` datetime(3) NOT NULL,
  `open_node_run_id` varchar(36) GENERATED ALWAYS AS (CASE WHEN `status`='OPEN' THEN `node_run_id` ELSE NULL END) STORED,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_ai_intervention_resume` (`resume_token`),
  UNIQUE KEY `uk_ai_intervention_open_node` (`open_node_run_id`),
  UNIQUE KEY `uk_ai_intervention_request` (`tenant_id`,`resolve_request_id`),
  UNIQUE KEY `uk_ai_intervention_source_message` (`tenant_id`,`source_message_id`),
  KEY `idx_ai_intervention_run` (`tenant_id`,`run_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ai_run_dependency` (
  `id` varchar(36) NOT NULL, `tenant_id` varchar(32) NOT NULL, `run_id` varchar(36) NOT NULL,
  `dependency_type` varchar(16) NOT NULL, `dependency_id` varchar(36) NOT NULL, `create_time` datetime(3) NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_ai_run_dependency` (`run_id`,`dependency_type`,`dependency_id`),
  KEY `idx_ai_run_dependency_lookup` (`tenant_id`,`dependency_type`,`dependency_id`), KEY `idx_ai_run_dependency_run` (`run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO `sys_permission`
(`id`,`parent_id`,`name`,`url`,`component`,`is_route`,`component_name`,`redirect`,`menu_type`,`perms`,`perms_type`,`sort_no`,`always_show`,`icon`,`is_leaf`,`keep_alive`,`hidden`,`hide_tab`,`description`,`create_by`,`create_time`,`update_by`,`update_time`,`del_flag`,`rule_flag`,`status`,`internal_or_external`)
VALUES
('2026071300000000051','2026071300000000001','运行查询',NULL,NULL,0,NULL,NULL,2,'ai:run:list','1',51,0,NULL,1,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0),
('2026071300000000052','2026071300000000001','启动运行',NULL,NULL,0,NULL,NULL,2,'ai:run:start','1',52,0,NULL,1,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0),
('2026071300000000053','2026071300000000001','取消运行',NULL,NULL,0,NULL,NULL,2,'ai:run:cancel','1',53,0,NULL,1,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0),
('2026071300000000054','2026071300000000001','重试运行',NULL,NULL,0,NULL,NULL,2,'ai:run:retry','1',54,0,NULL,1,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0),
('2026071300000000055','2026071300000000001','处理介入',NULL,NULL,0,NULL,NULL,2,'ai:run:intervene','1',55,0,NULL,1,0,0,0,NULL,'admin',NOW(),NULL,NULL,0,0,'1',0);

INSERT IGNORE INTO `sys_role_permission` (`id`,`role_id`,`permission_id`,`data_rule_ids`,`operate_date`,`operate_ip`)
SELECT MD5(CONCAT('f6817f48af4fb3af11b9e8bf182f618b', `id`)), 'f6817f48af4fb3af11b9e8bf182f618b', `id`, NULL, NOW(), '127.0.0.1'
FROM `sys_permission` WHERE `id` BETWEEN '2026071300000000051' AND '2026071300000000055';
