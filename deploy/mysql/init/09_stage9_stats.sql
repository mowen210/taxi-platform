-- 阶段 9：报表与大屏菜单
INSERT INTO `t_menu` (`id`, `parent_id`, `menu_type`, `menu_name`, `path`, `component`, `perm_code`, `icon`, `sort_no`, `visible`)
VALUES
    (9, 0, 1, '统计报表', '/stats', NULL, NULL, 'DataAnalysis', 80, 1),
    (901, 9, 2, '运营报表', '/stats/report', 'stats/report/index', 'stats:view', 'TrendCharts', 1, 1),
    (902, 9, 2, '数据大屏', '/stats/screen', 'stats/screen/index', 'stats:screen', 'FullScreen', 2, 1)
ON DUPLICATE KEY UPDATE `menu_name`=VALUES(`menu_name`), `perm_code`=VALUES(`perm_code`), `deleted`=0;

INSERT INTO `t_role_menu` (`id`, `role_id`, `menu_id`)
SELECT 970000 + m.`id`, r.`role_id`, m.`id`
FROM `t_menu` m JOIN (SELECT 1001 AS `role_id` UNION ALL SELECT 1002 UNION ALL SELECT 1003) r
WHERE m.`id` IN (9,901,902)
ON DUPLICATE KEY UPDATE `menu_id`=VALUES(`menu_id`);
