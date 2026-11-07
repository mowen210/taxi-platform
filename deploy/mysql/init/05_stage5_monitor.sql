-- =====================================================================
-- 阶段 5 增量脚本：轨迹兜底表（MySQL 本地联调）+ 实时监控菜单与权限
-- 生产环境轨迹走 TDengine（deploy/tdengine/01_track.sql），本表由
-- track.provider=mysql 时使用，便于没有时序库的开发机完整联调。
-- 幂等：CREATE TABLE IF NOT EXISTS + ON DUPLICATE KEY UPDATE
-- =====================================================================

CREATE TABLE IF NOT EXISTS `t_track` (
    `id`          BIGINT        NOT NULL COMMENT '主键',
    `vehicle_id`  BIGINT        NOT NULL COMMENT '车辆 ID',
    `terminal_id` VARCHAR(32)   NOT NULL COMMENT '终端手机号',
    `plate_no`    VARCHAR(16)            DEFAULT NULL COMMENT '车牌（冗余，便于排错）',
    `ts`          DATETIME(3)   NOT NULL COMMENT '定位时间（终端上报）',
    `lng`         DECIMAL(10, 7)         DEFAULT NULL COMMENT '经度',
    `lat`         DECIMAL(10, 7)         DEFAULT NULL COMMENT '纬度',
    `speed`       INT                    DEFAULT NULL COMMENT '速度 km/h',
    `direction`   INT                    DEFAULT NULL COMMENT '方向 0-359',
    `altitude`    INT                    DEFAULT NULL COMMENT '海拔 米',
    `mileage`     DOUBLE                 DEFAULT NULL COMMENT '累计里程 km',
    `acc_state`   TINYINT                DEFAULT NULL COMMENT 'ACC 状态 0 关 1 开',
    `alarm_flag`  BIGINT                 DEFAULT 0 COMMENT '808 报警标志位',
    `status_flag` BIGINT                 DEFAULT 0 COMMENT '808 状态位',
    `order_no`    VARCHAR(64)            DEFAULT NULL COMMENT '关联订单号（可空）',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
    PRIMARY KEY (`id`),
    KEY `idx_vehicle_ts` (`vehicle_id`, `ts`),
    KEY `idx_terminal_ts` (`terminal_id`, `ts`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '车辆轨迹（MySQL 兜底，生产走 TDengine）';

-- ------------------------- 菜单与权限 -------------------------
INSERT INTO `t_menu` (`id`, `parent_id`, `menu_type`, `menu_name`, `path`, `component`, `perm_code`, `icon`, `sort_no`, `visible`)
VALUES
    (501, 5, 2, '监控地图', '/monitor/map', 'monitor/map/index', 'monitor:view', 'Location', 1, 1),
    (502, 5, 2, '轨迹回放', '/monitor/track', 'monitor/track/index', 'track:view', 'VideoPlay', 2, 1)
ON DUPLICATE KEY UPDATE `menu_name` = VALUES(`menu_name`),
                        `path`      = VALUES(`path`),
                        `component` = VALUES(`component`),
                        `perm_code` = VALUES(`perm_code`),
                        `sort_no`   = VALUES(`sort_no`),
                        `deleted`   = 0;

-- 超级管理员、车队管理员、调度员：监控只读（数据范围仍由 ScopeGuard 收敛到本车队）
INSERT INTO `t_role_menu` (`id`, `role_id`, `menu_id`)
SELECT 930000 + r.`role_id` * 10 + m.`id`, r.`role_id`, m.`id`
FROM `t_menu` m
JOIN (SELECT 1001 AS `role_id` UNION ALL SELECT 1002 UNION ALL SELECT 1003) r
WHERE m.`id` IN (501, 502)
ON DUPLICATE KEY UPDATE `menu_id` = VALUES(`menu_id`);
