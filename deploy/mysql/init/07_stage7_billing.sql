-- 阶段 7：计价方案与结算菜单
INSERT INTO `t_menu` (`id`, `parent_id`, `menu_type`, `menu_name`, `path`, `component`, `perm_code`, `icon`, `sort_no`, `visible`)
VALUES
    (7, 0, 1, '计价结算', '/billing', NULL, NULL, 'Money', 60, 1),
    (701, 7, 2, '计价方案', '/billing/plan', 'billing/plan/index', 'pricing:view', 'PriceTag', 1, 1),
    (7011, 701, 3, '编辑方案', NULL, NULL, 'pricing:edit', NULL, 1, 1),
    (702, 7, 2, '结算单', '/billing/settle', 'billing/settle/index', 'settle:view', 'Wallet', 2, 1),
    (7021, 702, 3, '生成结算', NULL, NULL, 'settle:generate', NULL, 1, 1),
    (7022, 702, 3, '确认结算', NULL, NULL, 'settle:confirm', NULL, 2, 1),
    (7023, 702, 3, '标记打款', NULL, NULL, 'settle:pay', NULL, 3, 1),
    (7024, 702, 3, '导出结算', NULL, NULL, 'settle:export', NULL, 4, 1)
ON DUPLICATE KEY UPDATE `menu_name`=VALUES(`menu_name`), `perm_code`=VALUES(`perm_code`), `deleted`=0;

INSERT INTO `t_role_menu` (`id`, `role_id`, `menu_id`)
SELECT 950000 + m.`id`, 1001, m.`id` FROM `t_menu` m
WHERE m.`id` IN (7,701,7011,702,7021,7022,7023,7024)
ON DUPLICATE KEY UPDATE `menu_id`=VALUES(`menu_id`);

INSERT INTO `t_role_menu` (`id`, `role_id`, `menu_id`)
SELECT 951000 + m.`id`, 1002, m.`id` FROM `t_menu` m
WHERE m.`id` IN (7,701,702,7021,7022,7023,7024)
ON DUPLICATE KEY UPDATE `menu_id`=VALUES(`menu_id`);
