-- =====================================================================
-- 阶段 2 增量脚本：车队 / 车辆 / 司机 / 证件 / 终端绑定 菜单与权限
-- 幂等：全部使用 ON DUPLICATE KEY UPDATE，可重复执行
-- 执行顺序：01_schema.sql -> 02_seed.sql -> 03_stage2_menu.sql
-- =====================================================================

-- ------------------------- 菜单（页面 + 按钮权限） -------------------------
INSERT INTO `t_menu` (`id`, `parent_id`, `menu_type`, `menu_name`, `path`, `component`, `perm_code`, `icon`, `sort_no`, `visible`)
VALUES
    -- 系统管理 -> 车队 / 通知
    (105, 1, 2, '车队管理', '/system/fleet', 'system/fleet/index', 'fleet:list', 'OfficeBuilding', 5, 1),
    (1051, 105, 3, '新增车队', NULL, NULL, 'fleet:add', NULL, 1, 1),
    (1052, 105, 3, '修改车队', NULL, NULL, 'fleet:edit', NULL, 2, 1),
    (1053, 105, 3, '删除车队', NULL, NULL, 'fleet:delete', NULL, 3, 1),
    (106, 1, 2, '通知记录', '/system/notice', 'system/notice/index', 'notice:list', 'Bell', 7, 1),

    -- 车辆管理 -> 车辆档案 / 终端绑定
    (201, 2, 2, '车辆档案', '/vehicle/list', 'vehicle/list/index', 'vehicle:list', 'Van', 1, 1),
    (2011, 201, 3, '新增车辆', NULL, NULL, 'vehicle:add', NULL, 1, 1),
    (2012, 201, 3, '修改车辆', NULL, NULL, 'vehicle:edit', NULL, 2, 1),
    (2013, 201, 3, '删除车辆', NULL, NULL, 'vehicle:delete', NULL, 3, 1),
    (2014, 201, 3, '变更车辆状态', NULL, NULL, 'vehicle:status', NULL, 4, 1),
    (202, 2, 2, '终端绑定', '/vehicle/terminal', 'vehicle/terminal/index', 'terminal:list', 'Connection', 2, 1),
    (2021, 202, 3, '新增终端', NULL, NULL, 'terminal:add', NULL, 1, 1),
    (2022, 202, 3, '修改终端', NULL, NULL, 'terminal:edit', NULL, 2, 1),
    (2023, 202, 3, '删除终端', NULL, NULL, 'terminal:delete', NULL, 3, 1),
    (2024, 202, 3, '设备绑定/解绑', NULL, NULL, 'terminal:bind', NULL, 4, 1),

    -- 司机管理 -> 司机档案 / 证件管理
    (301, 3, 2, '司机档案', '/driver/list', 'driver/list/index', 'driver:list', 'User', 1, 1),
    (3011, 301, 3, '新增司机', NULL, NULL, 'driver:add', NULL, 1, 1),
    (3012, 301, 3, '修改司机', NULL, NULL, 'driver:edit', NULL, 2, 1),
    (3013, 301, 3, '删除司机', NULL, NULL, 'driver:delete', NULL, 3, 1),
    (302, 3, 2, '证件管理', '/driver/cert', 'driver/cert/index', 'driver:cert:list', 'Postcard', 2, 1),
    (3021, 302, 3, '新增证件', NULL, NULL, 'driver:cert:add', NULL, 1, 1),
    (3022, 302, 3, '修改证件', NULL, NULL, 'driver:cert:edit', NULL, 2, 1),
    (3023, 302, 3, '删除证件', NULL, NULL, 'driver:cert:delete', NULL, 3, 1),
    (3024, 302, 3, '执行到期扫描', NULL, NULL, 'cert:warn:scan', NULL, 4, 1)
ON DUPLICATE KEY UPDATE `menu_name`  = VALUES(`menu_name`),
                        `perm_code`  = VALUES(`perm_code`),
                        `sort_no`    = VALUES(`sort_no`),
                        `deleted`    = 0;

-- ------------------------- 角色授权 -------------------------
-- 超级管理员：阶段 2 全部菜单
INSERT INTO `t_role_menu` (`id`, `role_id`, `menu_id`)
SELECT 900000 + m.`id`, 1001, m.`id`
FROM `t_menu` m
WHERE m.`id` IN (105, 1051, 1052, 1053, 106,
                 201, 2011, 2012, 2013, 2014, 202, 2021, 2022, 2023, 2024,
                 301, 3011, 3012, 3013, 302, 3021, 3022, 3023, 3024)
ON DUPLICATE KEY UPDATE `menu_id` = VALUES(`menu_id`);

-- 车队管理员：本车队车辆/司机/证件全量，终端只读+绑定，车队只读，通知可查
INSERT INTO `t_role_menu` (`id`, `role_id`, `menu_id`)
SELECT 910000 + m.`id`, 1002, m.`id`
FROM `t_menu` m
WHERE m.`id` IN (105, 106,
                 201, 2011, 2012, 2014, 202, 2024,
                 301, 3011, 3012, 302, 3021, 3022, 3023, 3024)
ON DUPLICATE KEY UPDATE `menu_id` = VALUES(`menu_id`);

-- 调度员不授予车辆/司机档案权限，保持阶段 1 的菜单边界不变

-- ------------------------- 系统参数 -------------------------
INSERT INTO `t_sys_config` (`id`, `config_key`, `config_value`, `value_type`, `scope_type`, `scope_id`, `config_group`, `description`)
VALUES (11, 'cert.warn.days', '30', 'INT', 1, 0, 'warn', '证件与车辆证照到期预警提前天数')
ON DUPLICATE KEY UPDATE `config_value` = VALUES(`config_value`);
