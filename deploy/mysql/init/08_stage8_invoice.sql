-- 阶段 8：发票菜单
INSERT INTO `t_menu` (`id`, `parent_id`, `menu_type`, `menu_name`, `path`, `component`, `perm_code`, `icon`, `sort_no`, `visible`)
VALUES
    (8, 0, 1, '发票管理', '/invoice', NULL, NULL, 'Document', 70, 1),
    (801, 8, 2, '发票记录', '/invoice/list', 'invoice/list/index', 'invoice:view', 'Tickets', 1, 1),
    (8011, 801, 3, '申请开票', NULL, NULL, 'invoice:issue', NULL, 1, 1),
    (8012, 801, 3, '发票红冲', NULL, NULL, 'invoice:red', NULL, 2, 1),
    (8013, 801, 3, '重新下发', NULL, NULL, 'invoice:retry', NULL, 3, 1)
ON DUPLICATE KEY UPDATE `menu_name`=VALUES(`menu_name`), `perm_code`=VALUES(`perm_code`), `deleted`=0;

INSERT INTO `t_role_menu` (`id`, `role_id`, `menu_id`)
SELECT 960000 + m.`id`, 1001, m.`id` FROM `t_menu` m
WHERE m.`id` IN (8,801,8011,8012,8013)
ON DUPLICATE KEY UPDATE `menu_id`=VALUES(`menu_id`);

INSERT INTO `t_role_menu` (`id`, `role_id`, `menu_id`)
SELECT 961000 + m.`id`, 1002, m.`id` FROM `t_menu` m
WHERE m.`id` IN (8,801,8011,8013)
ON DUPLICATE KEY UPDATE `menu_id`=VALUES(`menu_id`);
