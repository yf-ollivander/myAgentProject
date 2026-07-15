ALTER TABLE `ai_feishu_bot`
  ADD COLUMN `entry_mode` varchar(20) NOT NULL DEFAULT 'DIRECT_AGENT' AFTER `default_chat_id`,
  ADD COLUMN `command_enabled` tinyint(1) NOT NULL DEFAULT 0 AFTER `entry_mode`,
  ADD KEY `idx_ai_feishu_bot_entry_mode` (`entry_mode`, `enabled`, `command_enabled`);
