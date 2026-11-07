-- =====================================================================
-- 阶段 3 增量脚本：订单状态流转日志 + 订单调度菜单与权限
-- 幂等：CREATE TABLE IF NOT EXISTS + ON DUPLICATE KEY UPDATE
-- 执行顺序：01_schema.sql -> 02_seed.sql -> 03_stage2_menu.sql -> 04_stage3_order.sql
-- =====================================================================

-- ------------------------- 订单状态流转日志 -------------------------
CREATE TABLE IF NOT EXISTS `t_order_status_log` (
    `id`            BIGINT      NOT NULL COMMENT '主键',
    `order_id`      BIGINT      NOT NULL COMMENT '订单 ID（逻辑外键 t_order.id）',
    `order_no`      VARCHAR(64) NOT NULL COMMENT '订单号（冗余）',
    `from_status`   VARCHAR(32)          DEFAULT NULL COMMENT '流转前状态，建单时为空',
    `to_status`     VARCHAR(32) NOT NULL COMMENT '流转后状态',
    `action`        VARCHAR(32) NOT NULL COMMENT '动作 CREATE/DISPATCH/PICKUP/FINISH/CALCULATE/CANCEL/MARK_ABNORMAL 等',
    `operator_id`   BIGINT               DEFAULT NULL COMMENT '操作员 ID，系统任务为空',
    `operator_name` VARCHAR(64)          DEFAULT NULL COMMENT '操作员姓名',
    `source`        TINYINT     NOT NULL DEFAULT 1 COMMENT '1 人工 2 终端 808 3 系统任务',
    `remark`        VARCHAR(512)         DEFAULT NULL COMMENT '说明',
    `deleted`       TINYINT     NOT NULL DEFAULT 0 COMMENT '软删除',
    `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `create_by`     BIGINT               DEFAULT NULL,
    `update_by`     BIGINT               DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_order` (`order_id`, `id`),
    KEY `idx_status_time` (`to_status`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '订单状态流转日志';

-- ------------------------- 菜单与按钮权限 -------------------------
INSERT INTO `t_menu` (`id`, `parent_id`, `menu_type`, `menu_name`, `path`, `component`, `perm_code`, `icon`, `sort_no`, `visible`)
VALUES
    (401, 4, 2, '订单列表', '/order/list', 'order/list/index', 'order:list', 'Tickets', 1, 1),
    (4011, 401, 3, '创建电召订单', NULL, NULL, 'order:create', NULL, 1, 1),
    (4012, 401, 3, '派单/接客/结束', NULL, NULL, 'order:dispatch', NULL, 2, 1),
    (4013, 401, 3, '费用核算', NULL, NULL, 'order:calculate', NULL, 3, 1),
    (4014, 401, 3, '取消订单', NULL, NULL, 'order:cancel', NULL, 4, 1),
    (4015, 401, 3, '转异常订单', NULL, NULL, 'order:abnormal', NULL, 5, 1)
ON DUPLICATE KEY UPDATE `menu_name` = VALUES(`menu_name`),
                        `perm_code` = VALUES(`perm_code`),
                        `sort_no`   = VALUES(`sort_no`),
                        `deleted`   = 0;

-- 超级管理员：全部订单菜单
INSERT INTO `t_role_menu` (`id`, `role_id`, `menu_id`)
SELECT 900000 + m.`id`, 1001, m.`id`
FROM `t_menu` m
WHERE m.`id` IN (401, 4011, 4012, 4013, 4014, 4015)
ON DUPLICATE KEY UPDATE `menu_id` = VALUES(`menu_id`);

-- 车队管理员：只读订单
INSERT INTO `t_role_menu` (`id`, `role_id`, `menu_id`)
SELECT 910000 + m.`id`, 1002, m.`id`
FROM `t_menu` m
WHERE m.`id` IN (401)
ON DUPLICATE KEY UPDATE `menu_id` = VALUES(`menu_id`);

-- 调度员：建单、派单/接客/结束、取消（不含核算与异常，异常由车队或平台处理）
INSERT INTO `t_role_menu` (`id`, `role_id`, `menu_id`)
SELECT 920000 + m.`id`, 1003, m.`id`
FROM `t_menu` m
WHERE m.`id` IN (401, 4011, 4012, 4014)
ON DUPLICATE KEY UPDATE `menu_id` = VALUES(`menu_id`);
