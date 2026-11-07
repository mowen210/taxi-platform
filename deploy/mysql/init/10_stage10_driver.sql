-- 阶段 10：司机端账号与菜单
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_driver' AND COLUMN_NAME = 'user_id');
SET @ddl := IF(@col = 0, 'ALTER TABLE `t_driver` ADD COLUMN `user_id` BIGINT DEFAULT NULL COMMENT ''司机端账号'' AFTER `id`, ADD UNIQUE KEY `uk_user_id` (`user_id`)', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO `t_role` (`id`, `role_code`, `role_name`, `data_scope`, `remark`)
VALUES (1004, 'DRIVER', '司机', 3, '司机端：仅本人订单')
ON DUPLICATE KEY UPDATE `role_name`=VALUES(`role_name`);

INSERT INTO `t_user` (`id`, `username`, `password`, `real_name`, `status`, `remark`)
VALUES (5, 'driver01', '$2a$10$e762XbIHaToW1ir/wgz3eurRIFSQmd2.7F.JJSb4lY2GzBA9Xrh5C', '王师傅', 1, '司机端演示账号')
ON DUPLICATE KEY UPDATE `real_name`=VALUES(`real_name`);

INSERT INTO `t_user_role` (`id`, `user_id`, `role_id`) VALUES (5, 5, 1004)
ON DUPLICATE KEY UPDATE `role_id`=VALUES(`role_id`);

UPDATE `t_driver` SET `user_id` = 5 WHERE `id` = 1 AND (`user_id` IS NULL OR `user_id` = 5);

INSERT INTO `t_menu` (`id`, `parent_id`, `menu_type`, `menu_name`, `path`, `component`, `perm_code`, `icon`, `sort_no`, `visible`)
VALUES
    (10, 0, 1, '司机端', '/driver-app', NULL, NULL, 'Cellphone', 100, 1),
    (1101, 10, 2, '我的行程', '/driver-app/orders', 'driver/orders/index', 'driver:app', 'List', 1, 1)
ON DUPLICATE KEY UPDATE `menu_name`=VALUES(`menu_name`), `perm_code`=VALUES(`perm_code`), `deleted`=0;

INSERT INTO `t_role_menu` (`id`, `role_id`, `menu_id`)
VALUES (980010, 1004, 10), (980011, 1004, 1101)
ON DUPLICATE KEY UPDATE `menu_id`=VALUES(`menu_id`);
