CREATE TABLE `ai_pipeline` (
  `id` varchar(36) NOT NULL,
  `create_by` varchar(50) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `sys_org_code` varchar(64) DEFAULT NULL,
  `tenant_id` varchar(32) NOT NULL DEFAULT '0',
  `del_flag` tinyint(1) NOT NULL DEFAULT 0,
  `pipeline_code` varchar(64) NOT NULL,
  `name` varchar(100) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `draft_definition_json` longtext NOT NULL,
  `draft_ui_json` longtext NOT NULL,
  `draft_revision` bigint NOT NULL DEFAULT 0,
  `notification_bot_id` varchar(36) DEFAULT NULL,
  `default_feishu_chat_id` varchar(100) DEFAULT NULL,
  `latest_version` int NOT NULL DEFAULT 0,
  `latest_version_id` varchar(36) DEFAULT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_pipeline_tenant_code` (`tenant_id`,`pipeline_code`),
  KEY `idx_ai_pipeline_tenant_enabled` (`tenant_id`,`enabled`),
  KEY `idx_ai_pipeline_org` (`sys_org_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ai_pipeline_version` (
  `id` varchar(36) NOT NULL,
  `pipeline_id` varchar(36) NOT NULL,
  `tenant_id` varchar(32) NOT NULL,
  `version` int NOT NULL,
  `source_draft_revision` bigint NOT NULL,
  `schema_version` varchar(16) NOT NULL,
  `definition_json` longtext NOT NULL,
  `ui_json` longtext NOT NULL,
  `definition_hash` char(64) NOT NULL,
  `publish_request_id` varchar(64) NOT NULL,
  `published_by` varchar(50) NOT NULL,
  `published_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_pipeline_version` (`pipeline_id`,`version`),
  UNIQUE KEY `uk_ai_pipeline_publish_request` (`pipeline_id`,`publish_request_id`),
  KEY `idx_ai_pipeline_version_tenant` (`tenant_id`,`pipeline_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ai_pipeline_trigger_key` (
  `id` varchar(36) NOT NULL,
  `tenant_id` varchar(32) NOT NULL,
  `pipeline_id` varchar(36) NOT NULL,
  `published_version_id` varchar(36) NOT NULL,
  `key_type` varchar(8) NOT NULL,
  `display_value` varchar(100) NOT NULL,
  `normalized_value` varchar(100) COLLATE utf8mb4_bin NOT NULL,
  `create_by` varchar(50) DEFAULT NULL,
  `create_time` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_pipeline_trigger_key` (`tenant_id`,`normalized_value`),
  KEY `idx_ai_pipeline_trigger_pipeline` (`pipeline_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `sys_permission`
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

INSERT INTO `sys_role_permission` (`id`,`role_id`,`permission_id`,`data_rule_ids`,`operate_date`,`operate_ip`)
SELECT MD5(CONCAT('f6817f48af4fb3af11b9e8bf182f618b', `id`)), 'f6817f48af4fb3af11b9e8bf182f618b', `id`, NULL, NOW(), '127.0.0.1'
FROM `sys_permission` WHERE `id` IN ('2026071300000000005','2026071300000000006','2026071300000000041','2026071300000000042','2026071300000000043','2026071300000000044','2026071300000000045','2026071300000000046','2026071300000000047','2026071300000000048');
