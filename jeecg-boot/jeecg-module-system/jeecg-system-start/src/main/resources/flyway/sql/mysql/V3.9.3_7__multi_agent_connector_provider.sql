-- update-begin---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】新增模型 Provider 配置且保持旧 Connector 为 Custom-----------
ALTER TABLE `ai_connector`
  ADD COLUMN `provider_type` varchar(32) NOT NULL DEFAULT 'CUSTOM' AFTER `result_contract_version`,
  ADD COLUMN `model_name` varchar(128) NULL AFTER `provider_type`,
  ADD COLUMN `model_options` text NULL AFTER `model_name`,
  ADD COLUMN `model_response_mode` varchar(16) NOT NULL DEFAULT 'TEXT' AFTER `model_options`,
  ADD KEY `idx_ai_connector_provider` (`provider_type`, `enabled`);

UPDATE `sys_permission`
SET `name` = '模型 Connector'
WHERE `id` = '2026071300000000003';
-- update-end---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】新增模型 Provider 配置且保持旧 Connector 为 Custom-----------
