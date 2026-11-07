-- =====================================================================
-- 出租汽车运营监管平台 - 初始化数据（MySQL 8.0）
-- 依赖：01_schema.sql 已执行
-- 默认管理员：admin / Admin@123（首次登录后强制修改，生产环境必须改）
-- 说明：口令为 BCrypt 散列，强度 10；明文不入库、不入日志
-- =====================================================================

USE `taxi_platform`;
SET NAMES utf8mb4;

-- ------------------------- 角色 -------------------------
INSERT INTO `t_role` (`id`, `role_code`, `role_name`, `data_scope`, `remark`)
VALUES (1001, 'SUPER_ADMIN', '超级管理员', 1, '全部数据权限，可管理系统配置'),
       (1002, 'FLEET_ADMIN', '车队管理员', 2, '仅本车队数据'),
       (1003, 'DISPATCHER', '调度员', 2, '订单派单、报警处置')
ON DUPLICATE KEY UPDATE `role_name` = VALUES(`role_name`);

-- ------------------------- 菜单与权限 -------------------------
-- 阶段 1 落地系统管理子树；业务模块目录先占位，对应权限码在阶段 2~9 增补
INSERT INTO `t_menu` (`id`, `parent_id`, `menu_type`, `menu_name`, `path`, `component`, `perm_code`, `icon`, `sort_no`, `visible`)
VALUES
    (1, 0, 1, '系统管理', '/system', NULL, NULL, 'Setting', 90, 1),
    (101, 1, 2, '用户管理', '/system/user', 'system/user/index', 'system:user:list', 'User', 1, 1),
    (1011, 101, 3, '新增用户', NULL, NULL, 'system:user:add', NULL, 1, 1),
    (1012, 101, 3, '修改用户', NULL, NULL, 'system:user:edit', NULL, 2, 1),
    (1013, 101, 3, '删除用户', NULL, NULL, 'system:user:delete', NULL, 3, 1),
    (1014, 101, 3, '重置口令', NULL, NULL, 'system:user:reset-pwd', NULL, 4, 1),
    (1015, 101, 3, '分配角色', NULL, NULL, 'system:user:assign-role', NULL, 5, 1),
    (102, 1, 2, '角色管理', '/system/role', 'system/role/index', 'system:role:list', 'Avatar', 2, 1),
    (1021, 102, 3, '新增角色', NULL, NULL, 'system:role:add', NULL, 1, 1),
    (1022, 102, 3, '修改角色', NULL, NULL, 'system:role:edit', NULL, 2, 1),
    (1023, 102, 3, '删除角色', NULL, NULL, 'system:role:delete', NULL, 3, 1),
    (1024, 102, 3, '分配菜单', NULL, NULL, 'system:role:assign-menu', NULL, 4, 1),
    (103, 1, 2, '菜单管理', '/system/menu', 'system/menu/index', 'system:menu:list', 'Menu', 3, 1),
    (1031, 103, 3, '新增菜单', NULL, NULL, 'system:menu:add', NULL, 1, 1),
    (1032, 103, 3, '修改菜单', NULL, NULL, 'system:menu:edit', NULL, 2, 1),
    (1033, 103, 3, '删除菜单', NULL, NULL, 'system:menu:delete', NULL, 3, 1),
    (104, 1, 2, '操作日志', '/system/log', 'system/log/index', 'system:log:list', 'Document', 4, 1),
    (2, 0, 1, '车辆管理', '/vehicle', NULL, NULL, 'Van', 10, 1),
    (3, 0, 1, '司机管理', '/driver', NULL, NULL, 'UserFilled', 20, 1),
    (4, 0, 1, '订单调度', '/order', NULL, NULL, 'Tickets', 30, 1),
    (5, 0, 1, '实时监控', '/monitor', NULL, NULL, 'Monitor', 40, 1),
    (6, 0, 1, '报警管理', '/alarm', NULL, NULL, 'Warning', 50, 1),
    (7, 0, 1, '发票管理', '/invoice', NULL, NULL, 'Money', 60, 1),
    (8, 0, 1, '统计大屏', '/dashboard', NULL, NULL, 'DataLine', 70, 1)
ON DUPLICATE KEY UPDATE `menu_name` = VALUES(`menu_name`);

-- 超级管理员：全量菜单
INSERT INTO `t_role_menu` (`id`, `role_id`, `menu_id`)
SELECT 900000 + m.`id`, 1001, m.`id` FROM `t_menu` m
ON DUPLICATE KEY UPDATE `menu_id` = VALUES(`menu_id`);

-- 车队管理员：系统管理中的日志 + 全部业务目录
INSERT INTO `t_role_menu` (`id`, `role_id`, `menu_id`)
SELECT 910000 + m.`id`, 1002, m.`id` FROM `t_menu` m
WHERE m.`id` IN (1, 104, 2, 3, 4, 5, 6, 7, 8)
ON DUPLICATE KEY UPDATE `menu_id` = VALUES(`menu_id`);

-- 调度员：订单、监控、报警、发票
INSERT INTO `t_role_menu` (`id`, `role_id`, `menu_id`)
SELECT 920000 + m.`id`, 1003, m.`id` FROM `t_menu` m
WHERE m.`id` IN (4, 5, 6, 7)
ON DUPLICATE KEY UPDATE `menu_id` = VALUES(`menu_id`);

-- ------------------------- 管理员账号 -------------------------
-- 口令散列由 tools/GenPassword 生成，明文 Admin@123，仅限首次登录
INSERT INTO `t_user` (`id`, `username`, `password`, `real_name`, `email`, `status`, `remark`)
VALUES (1, 'admin', '$2a$10$e762XbIHaToW1ir/wgz3eurRIFSQmd2.7F.JJSb4lY2GzBA9Xrh5C', '系统管理员', 'admin@example.com', 1, '内置超级管理员')
ON DUPLICATE KEY UPDATE `real_name` = VALUES(`real_name`);

INSERT INTO `t_user_role` (`id`, `user_id`, `role_id`)
VALUES (1, 1, 1001)
ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);

-- ------------------------- 演示车队 / 车辆 / 司机 / 终端 -------------------------
INSERT INTO `t_fleet` (`id`, `fleet_code`, `fleet_name`, `tax_no`, `contact`, `phone`, `address`, `status`)
VALUES (1, 'FLEET-DEMO', '演示出租汽车队', '91310000MA1FL0000X', '张队长', NULL, '演示市示例区示例路 1 号', 1)
ON DUPLICATE KEY UPDATE `fleet_name` = VALUES(`fleet_name`);

INSERT INTO `t_vehicle` (`id`, `plate_no`, `plate_color`, `vehicle_type`, `fleet_id`, `status`,
                         `insurance_expire_date`, `annual_inspect_expire_date`)
VALUES (1, '沪A00001', '蓝', '巡游出租汽车', 1, 1, DATE_ADD(CURDATE(), INTERVAL 20 DAY), DATE_ADD(CURDATE(), INTERVAL 90 DAY)),
       (2, '沪A00002', '蓝', '巡游出租汽车', 1, 1, DATE_ADD(CURDATE(), INTERVAL 200 DAY), DATE_ADD(CURDATE(), INTERVAL 240 DAY))
ON DUPLICATE KEY UPDATE `vehicle_type` = VALUES(`vehicle_type`);

INSERT INTO `t_driver` (`id`, `driver_code`, `name`, `license_no`, `license_class`, `license_expire_date`,
                        `qualification_no`, `qualification_expire_date`, `fleet_id`, `safety_score`, `status`)
VALUES (1, 'DRV-0001', '王师傅', '310101198001011234', 'C1', DATE_ADD(CURDATE(), INTERVAL 30 DAY),
        '310101198001011234', DATE_ADD(CURDATE(), INTERVAL 25 DAY), 1, 100.00, 1),
       (2, 'DRV-0002', '李师傅', '310101198502022345', 'C1', DATE_ADD(CURDATE(), INTERVAL 400 DAY),
        '310101198502022345', DATE_ADD(CURDATE(), INTERVAL 400 DAY), 1, 96.50, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

INSERT INTO `t_terminal_device` (`id`, `terminal_id`, `terminal_code`, `manufacturer_id`, `terminal_model`,
                                 `terminal_sn`, `protocol_version`, `online_status`)
VALUES (1, '0138000001', 'TERM-DEMO-01', 'JT808', 'DEMO-808', 'SN-DEMO-0001', '2019', 0),
       (2, '0138000002', 'TERM-DEMO-02', 'JT808', 'DEMO-808', 'SN-DEMO-0002', '2019', 0)
ON DUPLICATE KEY UPDATE `terminal_model` = VALUES(`terminal_model`);

INSERT INTO `t_device_binding` (`id`, `vehicle_id`, `terminal_id`, `taximeter_sn`, `bind_status`, `bind_source`,
                                `bind_time`, `operator_name`, `remark`)
VALUES (1, 1, '0138000001', 'TXM-DEMO-0001', 1, 3, NOW(), '系统初始化', '演示绑定'),
       (2, 2, '0138000002', 'TXM-DEMO-0002', 1, 3, NOW(), '系统初始化', '演示绑定')
ON DUPLICATE KEY UPDATE `bind_status` = VALUES(`bind_status`);

-- ------------------------- 司机证件（用于到期预警自测） -------------------------
INSERT INTO `t_driver_certificate` (`id`, `driver_id`, `cert_type`, `cert_no`, `expire_date`, `warn_status`)
VALUES (1, 1, 2, '310101198001011234', DATE_ADD(CURDATE(), INTERVAL 25 DAY), 1),
       (2, 2, 1, '310101198502022345', DATE_ADD(CURDATE(), INTERVAL 400 DAY), 0)
ON DUPLICATE KEY UPDATE `warn_status` = VALUES(`warn_status`);

-- ------------------------- 系统参数（三级配置） -------------------------
INSERT INTO `t_sys_config` (`id`, `config_key`, `config_value`, `value_type`, `scope_type`, `scope_id`, `config_group`, `description`)
VALUES (1, 'alarm.overspeed.threshold', '80', 'INT', 1, 0, 'alarm', '超速阈值（km/h）'),
       (2, 'alarm.overspeed.duration.sec', '10', 'INT', 1, 0, 'alarm', '持续超速判定秒数'),
       (3, 'alarm.escalate.seconds', '300', 'INT', 1, 0, 'alarm', '报警未处置升级秒数'),
       (4, 'alarm.record.before.sec', '10', 'INT', 1, 0, 'alarm', '联动录像前置秒数'),
       (5, 'alarm.record.after.sec', '20', 'INT', 1, 0, 'alarm', '联动录像后置秒数'),
       (6, 'taximeter.mileage.deviation.threshold', '0.20', 'DECIMAL', 1, 0, 'taximeter', 'GPS 与计价里程偏差阈值（0.2=20%）'),
       (7, 'invoice.auto.enable', 'false', 'BOOLEAN', 1, 0, 'invoice', '行程结束后是否自动开票'),
       (8, 'login.fail.max', '5', 'INT', 1, 0, 'security', '登录失败次数上限'),
       (9, 'login.lock.minutes', '15', 'INT', 1, 0, 'security', '登录锁定分钟数'),
       (10, 'report.export.max.rows', '50000', 'INT', 1, 0, 'report', '导出最大行数')
ON DUPLICATE KEY UPDATE `config_value` = VALUES(`config_value`);

-- ------------------------- 报警规则默认值（全局） -------------------------
INSERT INTO `t_alarm_rule_config` (`id`, `rule_code`, `alarm_type`, `alarm_level`, `scope_type`, `scope_id`,
                                   `threshold_json`, `notify_channel`, `popup_flag`, `voice_flag`,
                                   `escalate_sec`, `record_before_sec`, `record_after_sec`, `enabled`)
VALUES (1, 'SOS_DEFAULT', 'SOS', 3, 1, 0, NULL, 'SITE,SMS,WS', 1, 1, 60, 10, 30, 1),
       (2, 'OVERSPEED_DEFAULT', 'OVERSPEED', 2, 1, 0, JSON_OBJECT('speed', 80, 'durationSec', 10), 'SITE,SMS,WS', 1, 1, 300, 10, 20, 1),
       (3, 'FATIGUE_DEFAULT', 'FATIGUE', 2, 1, 0, JSON_OBJECT('durationSec', 60), 'SITE,SMS,WS', 1, 1, 300, 10, 20, 1),
       (4, 'DSM_SMOKING_DEFAULT', 'DSM_SMOKING', 2, 1, 0, JSON_OBJECT('durationSec', 5), 'SITE,WS', 1, 1, 300, 10, 10, 1),
       (5, 'DSM_PHONE_CALL_DEFAULT', 'DSM_PHONE_CALL', 2, 1, 0, JSON_OBJECT('durationSec', 5), 'SITE,WS', 1, 1, 300, 10, 10, 1),
       (6, 'DSM_EYE_OFF_ROAD_DEFAULT', 'DSM_EYE_OFF_ROAD', 2, 1, 0, JSON_OBJECT('durationSec', 5), 'SITE,WS', 1, 1, 300, 10, 10, 1),
       (7, 'DSM_HANDS_OFF_WHEEL_DEFAULT', 'DSM_HANDS_OFF_WHEEL', 2, 1, 0, JSON_OBJECT('durationSec', 5), 'SITE,WS', 1, 1, 300, 10, 10, 1),
       (8, 'ADAS_LANE_DEPARTURE_DEFAULT', 'ADAS_LANE_DEPARTURE', 2, 1, 0, NULL, 'SITE,WS', 1, 1, 300, 10, 10, 1)
ON DUPLICATE KEY UPDATE `alarm_level` = VALUES(`alarm_level`);

-- ------------------------- 默认计价方案 -------------------------
INSERT INTO `t_pricing_plan` (`id`, `plan_code`, `plan_name`, `strategy_type`, `scope_type`, `scope_id`,
                              `base_fare`, `base_mileage`, `per_km_price`, `waiting_price_per_min`,
                              `low_speed_price_per_min`, `night_rate_json`, `fuel_surcharge`,
                              `driver_share_rate`, `priority`, `enabled`, `remark`)
VALUES (1, 'PLAN-STANDARD', '标准巡游计价', 'STANDARD', 1, 0, 14.00, 3.00, 2.50, 0.50, 0.50,
        JSON_OBJECT('startTime', '23:00', 'endTime', '05:00', 'rate', 1.3), 0.00, 1.0000, 100, 1, '演示方案，上线前按当地运价文件调整')
ON DUPLICATE KEY UPDATE `plan_name` = VALUES(`plan_name`);

INSERT INTO `t_pricing_plan_item` (`id`, `plan_id`, `item_type`, `seg_start`, `seg_end`, `unit_price`, `sort_no`)
VALUES (1, 1, 1, 0.00, 3.00, 14.00, 1),
       (2, 1, 2, 3.00, 10.00, 2.50, 2),
       (3, 1, 2, 10.00, NULL, 3.20, 3)
ON DUPLICATE KEY UPDATE `unit_price` = VALUES(`unit_price`);

-- ------------------------- 演示电子围栏 -------------------------
INSERT INTO `t_electronic_fence` (`id`, `fence_no`, `fence_name`, `fence_type`, `center_lng`, `center_lat`, `radius`,
                                  `fence_action`, `scope_type`, `scope_id`, `alarm_level`, `enabled`)
VALUES (1, 'FENCE-DEMO-01', '演示城区围栏', 1, 121.4737000, 31.2304000, 5000, 3, 1, 1, 1, 1)
ON DUPLICATE KEY UPDATE `fence_name` = VALUES(`fence_name`);
