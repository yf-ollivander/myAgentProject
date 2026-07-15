-- The parent route renders only the layout, so direct menu clicks must open the first child page.
UPDATE `sys_permission`
SET `redirect` = '/multi-agent/agents'
WHERE `id` = '2026071300000000001';
