-- =====================================================================
-- 阶段 6 增量脚本：报警中心菜单与权限（表结构已在 01_schema.sql 建好）
-- 依赖：t_alarm_record / t_alarm_media / t_alarm_rule_config
-- 幂等：ON DUPLICATE KEY UPDATE
-- =====================================================================

INSERT INTO `t_menu` (`id`, `parent_id`, `menu_type`, `menu_name`, `path`, `component`, `perm_code`, `icon`, `sort_no`, `visible`)
VALUES
    (601, 6, 2, '报警中心', '/alarm/list', 'alarm/list/index', 'alarm:view', 'Warning', 1, 1),
    (6011, 601, 3, '报警处置', NULL, NULL, 'alarm:handle', NULL, 1, 1),
    (6012, 601, 3, '联动抓拍/预览', NULL, NULL, 'media:live', NULL, 2, 1),
    (602, 6, 2, '媒体库', '/alarm/media', 'alarm/media/index', 'media:view', 'Picture', 2, 1),
    (6021, 602, 3, '媒体下载', NULL, NULL, 'media:download', NULL, 1, 1),
    (603, 6, 2, '报警规则', '/alarm/rule', 'alarm/rule/index', 'alarm:rule:view', 'SetUp', 3, 1),
    (6031, 603, 3, '修改规则', NULL, NULL, 'alarm:rule:edit', NULL, 1, 1)
ON DUPLICATE KEY UPDATE `menu_name` = VALUES(`menu_name`),
                        `path` = VALUES(`path`),
                        `component` = VALUES(`component`),
                        `perm_code` = VALUES(`perm_code`),
                        `sort_no` = VALUES(`sort_no`),
                        `deleted` = 0;

-- 超级管理员：全部
INSERT INTO `t_role_menu` (`id`, `role_id`, `menu_id`)
SELECT 940000 + m.`id`, 1001, m.`id`
FROM `t_menu` m
WHERE m.`id` IN (6, 601, 6011, 6012, 602, 6021, 603, 6031)
ON DUPLICATE KEY UPDATE `menu_id` = VALUES(`menu_id`);

-- 车队管理员：处置、媒体、规则查看与修改
INSERT INTO `t_role_menu` (`id`, `role_id`, `menu_id`)
SELECT 941000 + m.`id`, 1002, m.`id`
FROM `t_menu` m
WHERE m.`id` IN (6, 601, 6011, 6012, 602, 6021, 603, 6031)
ON DUPLICATE KEY UPDATE `menu_id` = VALUES(`menu_id`);

-- 调度员：查看、处置、在线预览，不授予媒体下载与规则修改
INSERT INTO `t_role_menu` (`id`, `role_id`, `menu_id`)
SELECT 942000 + m.`id`, 1003, m.`id`
FROM `t_menu` m
WHERE m.`id` IN (6, 601, 6011, 6012, 602, 603)
ON DUPLICATE KEY UPDATE `menu_id` = VALUES(`menu_id`);
