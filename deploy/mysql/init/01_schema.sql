-- =====================================================================
-- 出租汽车运营监管平台 - 建表脚本（MySQL 8.0）
-- 规范：无物理外键、逻辑外键、统一软删除 deleted、审计四字段
-- 字符集：utf8mb4 / utf8mb4_0900_ai_ci
-- 版本：阶段 1（v1.0.0）
-- 说明：本脚本覆盖需求第六节全部 25 张表 + 确认补充的 t_cmd_retry /
--       t_pricing_plan / t_pricing_plan_item / t_notice 4 张表。
-- =====================================================================

CREATE DATABASE IF NOT EXISTS `taxi_platform`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

USE `taxi_platform`;

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 一、权限域
-- ---------------------------------------------------------------------

DROP TABLE IF EXISTS `t_user`;
CREATE TABLE `t_user` (
    `id`             BIGINT       NOT NULL COMMENT '主键（雪花 ID）',
    `username`       VARCHAR(64)  NOT NULL COMMENT '登录名',
    `password`       VARCHAR(100) NOT NULL COMMENT 'BCrypt 口令散列',
    `real_name`      VARCHAR(64)           DEFAULT NULL COMMENT '姓名',
    `phone`          VARCHAR(255)          DEFAULT NULL COMMENT '手机号 AES-GCM 密文',
    `phone_hash`     CHAR(64)              DEFAULT NULL COMMENT '手机号 HMAC-SHA256，用于等值检索',
    `id_card`        VARCHAR(255)          DEFAULT NULL COMMENT '身份证 AES-GCM 密文',
    `id_card_mask`   VARCHAR(32)           DEFAULT NULL COMMENT '身份证脱敏值',
    `email`          VARCHAR(128)          DEFAULT NULL COMMENT '邮箱',
    `fleet_id`       BIGINT                DEFAULT NULL COMMENT '所属车队（数据权限）',
    `status`         TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 0 停用',
    `last_login_time` DATETIME             DEFAULT NULL COMMENT '最后登录时间',
    `remark`         VARCHAR(255)          DEFAULT NULL COMMENT '备注',
    `deleted`        TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除 0 未删 1 已删',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by`      BIGINT                DEFAULT NULL COMMENT '创建人',
    `update_by`      BIGINT                DEFAULT NULL COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_fleet_id` (`fleet_id`),
    KEY `idx_phone_hash` (`phone_hash`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '系统用户';

DROP TABLE IF EXISTS `t_role`;
CREATE TABLE `t_role` (
    `id`          BIGINT      NOT NULL COMMENT '主键',
    `role_code`   VARCHAR(64) NOT NULL COMMENT '角色编码',
    `role_name`   VARCHAR(64) NOT NULL COMMENT '角色名称',
    `data_scope`  TINYINT     NOT NULL DEFAULT 3 COMMENT '数据权限 1 全部 2 本车队 3 本人',
    `remark`      VARCHAR(255)         DEFAULT NULL COMMENT '备注',
    `deleted`     TINYINT     NOT NULL DEFAULT 0 COMMENT '软删除',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `create_by`   BIGINT               DEFAULT NULL,
    `update_by`   BIGINT               DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '角色';

DROP TABLE IF EXISTS `t_menu`;
CREATE TABLE `t_menu` (
    `id`          BIGINT      NOT NULL COMMENT '主键',
    `parent_id`   BIGINT      NOT NULL DEFAULT 0 COMMENT '父级 ID，根为 0',
    `menu_type`   TINYINT     NOT NULL DEFAULT 2 COMMENT '1 目录 2 菜单 3 按钮',
    `menu_name`   VARCHAR(64) NOT NULL COMMENT '名称',
    `path`        VARCHAR(255)         DEFAULT NULL COMMENT '前端路由',
    `component`   VARCHAR(255)         DEFAULT NULL COMMENT '前端组件',
    `perm_code`   VARCHAR(128)         DEFAULT NULL COMMENT '权限码，如 system:user:list',
    `icon`        VARCHAR(64)          DEFAULT NULL COMMENT '图标',
    `sort_no`     INT         NOT NULL DEFAULT 0 COMMENT '排序',
    `visible`     TINYINT     NOT NULL DEFAULT 1 COMMENT '1 显示 0 隐藏',
    `deleted`     TINYINT     NOT NULL DEFAULT 0 COMMENT '软删除',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `create_by`   BIGINT               DEFAULT NULL,
    `update_by`   BIGINT               DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_perm_code` (`perm_code`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '菜单与按钮权限';

DROP TABLE IF EXISTS `t_user_role`;
CREATE TABLE `t_user_role` (
    `id`          BIGINT   NOT NULL COMMENT '主键',
    `user_id`     BIGINT   NOT NULL COMMENT '用户 ID（逻辑外键 t_user.id）',
    `role_id`     BIGINT   NOT NULL COMMENT '角色 ID（逻辑外键 t_role.id）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `create_by`   BIGINT            DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
    KEY `idx_role_id` (`role_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户-角色';

DROP TABLE IF EXISTS `t_role_menu`;
CREATE TABLE `t_role_menu` (
    `id`          BIGINT   NOT NULL COMMENT '主键',
    `role_id`     BIGINT   NOT NULL COMMENT '角色 ID（逻辑外键 t_role.id）',
    `menu_id`     BIGINT   NOT NULL COMMENT '菜单 ID（逻辑外键 t_menu.id）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `create_by`   BIGINT            DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_menu` (`role_id`, `menu_id`),
    KEY `idx_menu_id` (`menu_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '角色-菜单';

-- ---------------------------------------------------------------------
-- 二、资源域：车队 / 车辆 / 司机 / 终端 / 绑定
-- ---------------------------------------------------------------------

DROP TABLE IF EXISTS `t_fleet`;
CREATE TABLE `t_fleet` (
    `id`          BIGINT      NOT NULL COMMENT '主键',
    `fleet_code`  VARCHAR(64) NOT NULL COMMENT '车队编码',
    `fleet_name`  VARCHAR(128) NOT NULL COMMENT '车队名称',
    `tax_no`      VARCHAR(32)          DEFAULT NULL COMMENT '销方税号（诺诺开票用）',
    `contact`     VARCHAR(64)          DEFAULT NULL COMMENT '联系人',
    `phone`       VARCHAR(255)         DEFAULT NULL COMMENT '联系电话 AES-GCM 密文',
    `address`     VARCHAR(255)         DEFAULT NULL COMMENT '地址',
    `status`      TINYINT     NOT NULL DEFAULT 1 COMMENT '1 正常 0 停用',
    `deleted`     TINYINT     NOT NULL DEFAULT 0 COMMENT '软删除',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `create_by`   BIGINT               DEFAULT NULL,
    `update_by`   BIGINT               DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_fleet_code` (`fleet_code`),
    KEY `idx_tax_no` (`tax_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '车队';

DROP TABLE IF EXISTS `t_vehicle`;
CREATE TABLE `t_vehicle` (
    `id`                      BIGINT      NOT NULL COMMENT '主键',
    `plate_no`                VARCHAR(16) NOT NULL COMMENT '车牌号',
    `plate_color`             VARCHAR(8)           DEFAULT NULL COMMENT '车牌颜色',
    `vehicle_type`            VARCHAR(32)          DEFAULT NULL COMMENT '车辆类型',
    `vin`                     VARCHAR(32)          DEFAULT NULL COMMENT '车架号',
    `engine_no`               VARCHAR(32)          DEFAULT NULL COMMENT '发动机号',
    `owner_name`              VARCHAR(64)          DEFAULT NULL COMMENT '车主',
    `fleet_id`                BIGINT               DEFAULT NULL COMMENT '所属车队（逻辑外键 t_fleet.id）',
    `driver_id`               BIGINT               DEFAULT NULL COMMENT '默认司机（逻辑外键 t_driver.id）',
    `status`                  TINYINT     NOT NULL DEFAULT 1 COMMENT '1 空闲 2 营运中 3 维修 4 停运',
    `insurance_expire_date`   DATE                 DEFAULT NULL COMMENT '保险到期日',
    `annual_inspect_expire_date` DATE              DEFAULT NULL COMMENT '年检到期日',
    `purchase_date`           DATE                 DEFAULT NULL COMMENT '购置日期',
    `remark`                  VARCHAR(255)         DEFAULT NULL COMMENT '备注',
    `deleted`                 TINYINT     NOT NULL DEFAULT 0 COMMENT '软删除',
    `create_time`             DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`             DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `create_by`               BIGINT               DEFAULT NULL,
    `update_by`               BIGINT               DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plate_no` (`plate_no`),
    KEY `idx_fleet_id` (`fleet_id`),
    KEY `idx_status` (`status`),
    KEY `idx_insurance_expire` (`insurance_expire_date`),
    KEY `idx_inspect_expire` (`annual_inspect_expire_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '车辆档案';

DROP TABLE IF EXISTS `t_driver`;
CREATE TABLE `t_driver` (
    `id`                       BIGINT      NOT NULL COMMENT '主键',
    `driver_code`              VARCHAR(64) NOT NULL COMMENT '司机编号',
    `name`                     VARCHAR(64) NOT NULL COMMENT '姓名',
    `phone`                    VARCHAR(255)         DEFAULT NULL COMMENT '手机号 AES-GCM 密文',
    `phone_hash`               CHAR(64)             DEFAULT NULL COMMENT '手机号 HMAC 摘要',
    `id_card`                  VARCHAR(255)         DEFAULT NULL COMMENT '身份证 AES-GCM 密文',
    `id_card_mask`             VARCHAR(32)          DEFAULT NULL COMMENT '身份证脱敏值',
    `gender`                   TINYINT              DEFAULT NULL COMMENT '1 男 2 女',
    `license_no`               VARCHAR(32)          DEFAULT NULL COMMENT '驾驶证号',
    `license_class`            VARCHAR(8)           DEFAULT NULL COMMENT '准驾车型',
    `license_expire_date`      DATE                 DEFAULT NULL COMMENT '驾驶证到期日',
    `qualification_no`         VARCHAR(32)          DEFAULT NULL COMMENT '从业资格证号',
    `qualification_expire_date` DATE                DEFAULT NULL COMMENT '从业资格证到期日',
    `fleet_id`                 BIGINT               DEFAULT NULL COMMENT '所属车队（逻辑外键 t_fleet.id）',
    `online_status`            TINYINT     NOT NULL DEFAULT 0 COMMENT '1 在线 0 离线',
    `safety_score`             DECIMAL(5, 2)        DEFAULT NULL COMMENT '最新安全评分',
    `status`                   TINYINT     NOT NULL DEFAULT 1 COMMENT '1 在职 2 离职 3 停运',
    `entry_date`               DATE                 DEFAULT NULL COMMENT '入职日期',
    `remark`                   VARCHAR(255)         DEFAULT NULL COMMENT '备注',
    `deleted`                  TINYINT     NOT NULL DEFAULT 0 COMMENT '软删除',
    `create_time`              DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`              DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `create_by`                BIGINT               DEFAULT NULL,
    `update_by`                BIGINT               DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_driver_code` (`driver_code`),
    KEY `idx_fleet_id` (`fleet_id`),
    KEY `idx_phone_hash` (`phone_hash`),
    KEY `idx_license_expire` (`license_expire_date`),
    KEY `idx_qualification_expire` (`qualification_expire_date`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '司机档案';

DROP TABLE IF EXISTS `t_driver_certificate`;
CREATE TABLE `t_driver_certificate` (
    `id`             BIGINT      NOT NULL COMMENT '主键',
    `driver_id`      BIGINT      NOT NULL COMMENT '司机 ID（逻辑外键 t_driver.id）',
    `cert_type`      TINYINT     NOT NULL COMMENT '1 驾驶证 2 从业资格证 3 身份证 4 健康证 9 其他',
    `cert_no`        VARCHAR(64)          DEFAULT NULL COMMENT '证件号',
    `issue_date`     DATE                 DEFAULT NULL COMMENT '发证日期',
    `expire_date`    DATE                 DEFAULT NULL COMMENT '到期日期',
    `file_url`       VARCHAR(512)         DEFAULT NULL COMMENT '证件影像地址',
    `warn_status`    TINYINT     NOT NULL DEFAULT 0 COMMENT '0 正常 1 预警 2 过期',
    `last_warn_time` DATETIME             DEFAULT NULL COMMENT '最近预警时间',
    `deleted`        TINYINT     NOT NULL DEFAULT 0 COMMENT '软删除',
    `create_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `create_by`      BIGINT               DEFAULT NULL,
    `update_by`      BIGINT               DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_driver_cert` (`driver_id`, `cert_type`, `cert_no`),
    KEY `idx_expire` (`cert_type`, `expire_date`),
    KEY `idx_warn_status` (`warn_status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '司机证件';

DROP TABLE IF EXISTS `t_driver_alarm_score`;
CREATE TABLE `t_driver_alarm_score` (
    `id`               BIGINT        NOT NULL COMMENT '主键',
    `driver_id`        BIGINT        NOT NULL COMMENT '司机 ID',
    `stat_date`        DATE          NOT NULL COMMENT '统计日期',
    `period_type`      TINYINT       NOT NULL DEFAULT 1 COMMENT '1 日 2 月',
    `base_score`       DECIMAL(5, 2) NOT NULL DEFAULT 100.00 COMMENT '基础分',
    `alarm_total`      INT           NOT NULL DEFAULT 0 COMMENT '报警总数',
    `level1_cnt`       INT           NOT NULL DEFAULT 0 COMMENT '一级报警数',
    `level2_cnt`       INT           NOT NULL DEFAULT 0 COMMENT '二级报警数',
    `level3_cnt`       INT           NOT NULL DEFAULT 0 COMMENT '三级报警数',
    `sos_cnt`          INT           NOT NULL DEFAULT 0 COMMENT 'SOS 次数',
    `overspeed_cnt`    INT           NOT NULL DEFAULT 0 COMMENT '超速次数',
    `fatigue_cnt`      INT           NOT NULL DEFAULT 0 COMMENT '疲劳次数',
    `dsm_cnt`          INT           NOT NULL DEFAULT 0 COMMENT 'DSM 次数',
    `adas_cnt`         INT           NOT NULL DEFAULT 0 COMMENT 'ADAS 次数',
    `mileage_km`       DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '统计里程',
    `alarm_per_100km`  DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '百公里报警频次',
    `deduct_score`     DECIMAL(5, 2) NOT NULL DEFAULT 0.00 COMMENT '扣分',
    `final_score`      DECIMAL(5, 2) NOT NULL DEFAULT 100.00 COMMENT '最终评分',
    `calc_time`        DATETIME               DEFAULT NULL COMMENT '计算时间',
    `create_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `create_by`        BIGINT                 DEFAULT NULL,
    `update_by`        BIGINT                 DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_driver_period` (`driver_id`, `stat_date`, `period_type`),
    KEY `idx_stat_date` (`stat_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '司机安全评分快照';

DROP TABLE IF EXISTS `t_terminal_device`;
CREATE TABLE `t_terminal_device` (
    `id`                 BIGINT      NOT NULL COMMENT '主键',
    `terminal_id`        VARCHAR(32) NOT NULL COMMENT '终端手机号（808 拨号号）',
    `terminal_code`      VARCHAR(64)          DEFAULT NULL COMMENT '终端编号',
    `sim_no`             VARCHAR(32)          DEFAULT NULL COMMENT 'SIM 卡号',
    `manufacturer_id`    VARCHAR(16)          DEFAULT NULL COMMENT '厂商 ID（808 消息头）',
    `terminal_model`     VARCHAR(64)          DEFAULT NULL COMMENT '终端型号',
    `terminal_sn`        VARCHAR(64)          DEFAULT NULL COMMENT '终端序列号',
    `protocol_version`   VARCHAR(16)          DEFAULT NULL COMMENT '协议版本 2011/2019',
    `auth_code`          VARCHAR(64)          DEFAULT NULL COMMENT '最近鉴权码（Redis 为主，此列为审计留痕）',
    `firmware_version`   VARCHAR(32)          DEFAULT NULL COMMENT '固件版本',
    `online_status`      TINYINT     NOT NULL DEFAULT 0 COMMENT '1 在线 0 离线',
    `last_heartbeat_time` DATETIME            DEFAULT NULL COMMENT '最后心跳时间',
    `last_online_time`   DATETIME             DEFAULT NULL COMMENT '最后上线时间',
    `register_time`      DATETIME             DEFAULT NULL COMMENT '首次注册时间',
    `deleted`            TINYINT     NOT NULL DEFAULT 0 COMMENT '软删除',
    `create_time`        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `create_by`          BIGINT               DEFAULT NULL,
    `update_by`          BIGINT               DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_terminal_id` (`terminal_id`),
    KEY `idx_online_status` (`online_status`),
    KEY `idx_terminal_sn` (`terminal_sn`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '终端档案';

DROP TABLE IF EXISTS `t_device_binding`;
CREATE TABLE `t_device_binding` (
    `id`            BIGINT      NOT NULL COMMENT '主键',
    `vehicle_id`    BIGINT      NOT NULL COMMENT '车辆 ID（逻辑外键 t_vehicle.id）',
    `terminal_id`   VARCHAR(32) NOT NULL COMMENT '终端手机号（逻辑外键 t_terminal_device.terminal_id）',
    `taximeter_sn`  VARCHAR(64) NOT NULL COMMENT '计价器 SN',
    `bind_status`   TINYINT     NOT NULL DEFAULT 1 COMMENT '1 绑定 0 解绑',
    `bind_source`   TINYINT     NOT NULL DEFAULT 3 COMMENT '1 注册扩展字段 2 指令查询上报 3 人工绑定',
    `bind_time`     DATETIME             DEFAULT NULL COMMENT '绑定时间',
    `unbind_time`   DATETIME             DEFAULT NULL COMMENT '解绑时间',
    `operator_id`   BIGINT               DEFAULT NULL COMMENT '操作员 ID',
    `operator_name` VARCHAR(64)          DEFAULT NULL COMMENT '操作员姓名',
    `remark`        VARCHAR(255)         DEFAULT NULL COMMENT '备注',
    -- 生成列：仅在生效绑定时有值，配合唯一索引防止并发重复绑定
    `active_key`    VARCHAR(160) GENERATED ALWAYS AS (
        CASE WHEN `bind_status` = 1
             THEN CONCAT(`vehicle_id`, '#', `terminal_id`, '#', `taximeter_sn`)
             ELSE NULL END
        ) STORED COMMENT '生效绑定唯一键',
    `deleted`       TINYINT     NOT NULL DEFAULT 0 COMMENT '软删除',
    `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `create_by`     BIGINT               DEFAULT NULL,
    `update_by`     BIGINT               DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_active_binding` (`active_key`),
    KEY `idx_vehicle` (`vehicle_id`, `bind_status`),
    KEY `idx_terminal` (`terminal_id`, `bind_status`),
    KEY `idx_taximeter` (`taximeter_sn`, `bind_status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '车辆-终端-计价器绑定';

-- ---------------------------------------------------------------------
-- 三、营运域：原始流水 / 订单 / 明细 / 结算
-- ---------------------------------------------------------------------

-- 计价器原始流水：按日分表，表名 t_taximeter_raw_YYYYMMDD。
-- 下表为模板与当日表，由 TaximeterRawRouter 每日复制 DDL 建表；跨日查询走 t_order 汇总。
DROP TABLE IF EXISTS `t_taximeter_raw_20260101`;
CREATE TABLE `t_taximeter_raw_20260101` (
    `id`                      BIGINT        NOT NULL COMMENT '主键',
    `order_no`                VARCHAR(64)            DEFAULT NULL COMMENT '平台订单号',
    `terminal_id`             VARCHAR(32)   NOT NULL COMMENT '终端手机号',
    `vehicle_id`              BIGINT                 DEFAULT NULL COMMENT '车辆 ID',
    `taximeter_sn`            VARCHAR(64)            DEFAULT NULL COMMENT '计价器 SN',
    `biz_type`                TINYINT       NOT NULL COMMENT '1=0x0B02 营运开始 2=0x0B05 营运结束',
    `msg_seq`                 INT                    DEFAULT NULL COMMENT '808 报文流水号',
    `business_id`             VARCHAR(64)            DEFAULT NULL COMMENT '905 营运 ID',
    `start_time`              DATETIME               DEFAULT NULL COMMENT '上车时间',
    `end_time`                DATETIME               DEFAULT NULL COMMENT '下车时间',
    `pickup_lng`              DECIMAL(10, 7)         DEFAULT NULL COMMENT '上车经度',
    `pickup_lat`              DECIMAL(10, 7)         DEFAULT NULL COMMENT '上车纬度',
    `dropoff_lng`             DECIMAL(10, 7)         DEFAULT NULL COMMENT '下车经度',
    `dropoff_lat`             DECIMAL(10, 7)         DEFAULT NULL COMMENT '下车纬度',
    `amount`                  DECIMAL(10, 2)         DEFAULT NULL COMMENT '计价金额',
    `billing_mileage`         DECIMAL(10, 2)         DEFAULT NULL COMMENT '计费里程 km',
    `empty_mileage`           DECIMAL(10, 2)         DEFAULT NULL COMMENT '空驶里程 km',
    `waiting_duration`        INT                    DEFAULT NULL COMMENT '等候时长（秒）',
    `gps_mileage`             DECIMAL(10, 2)         DEFAULT NULL COMMENT 'GPS 里程 km',
    `mileage_deviation_rate`  DECIMAL(6, 4)          DEFAULT NULL COMMENT 'GPS 与计价里程偏差率',
    `evaluate_score`          TINYINT                DEFAULT NULL COMMENT '乘客评价分',
    `raw_hex`                 MEDIUMTEXT             DEFAULT NULL COMMENT '原始 HEX 报文（对账与排错）',
    `parse_status`            TINYINT       NOT NULL DEFAULT 0 COMMENT '0 待解析 1 解析成功 2 解析失败',
    `parse_error`             VARCHAR(512)           DEFAULT NULL COMMENT '解析失败原因',
    `report_time`             DATETIME      NOT NULL COMMENT '报文上报时间',
    `create_time`             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_business` (`business_id`, `biz_type`),
    KEY `idx_terminal_time` (`terminal_id`, `report_time`),
    KEY `idx_order_no` (`order_no`),
    KEY `idx_vehicle_time` (`vehicle_id`, `report_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '计价器原始流水（日表模板）';

DROP TABLE IF EXISTS `t_order`;
CREATE TABLE `t_order` (
    `id`               BIGINT         NOT NULL COMMENT '主键',
    `order_no`         VARCHAR(64)    NOT NULL COMMENT '平台订单号（幂等键）',
    `order_source`     TINYINT        NOT NULL COMMENT '1 巡游（905） 2 电召（派单）',
    `business_id`      VARCHAR(64)             DEFAULT NULL COMMENT '905 营运 ID（巡游幂等键）',
    `vehicle_id`       BIGINT                  DEFAULT NULL COMMENT '车辆 ID',
    `driver_id`        BIGINT                  DEFAULT NULL COMMENT '司机 ID',
    `fleet_id`         BIGINT                  DEFAULT NULL COMMENT '车队 ID',
    `terminal_id`      VARCHAR(32)             DEFAULT NULL COMMENT '终端手机号',
    `taximeter_sn`     VARCHAR(64)             DEFAULT NULL COMMENT '计价器 SN',
    `status`           VARCHAR(32)    NOT NULL COMMENT '状态机 10 态，见 OrderStatus',
    `pricing_plan_id`  BIGINT                  DEFAULT NULL COMMENT '计价方案 ID（逻辑外键 t_pricing_plan.id）',
    `passenger_name`   VARCHAR(64)             DEFAULT NULL COMMENT '乘客称呼',
    `passenger_phone`  VARCHAR(255)            DEFAULT NULL COMMENT '乘客手机号 AES-GCM 密文',
    `start_address`    VARCHAR(255)            DEFAULT NULL COMMENT '上车地址',
    `start_lng`        DECIMAL(10, 7)          DEFAULT NULL COMMENT '上车经度',
    `start_lat`        DECIMAL(10, 7)          DEFAULT NULL COMMENT '上车纬度',
    `end_address`      VARCHAR(255)            DEFAULT NULL COMMENT '下车地址',
    `end_lng`          DECIMAL(10, 7)          DEFAULT NULL COMMENT '下车经度',
    `end_lat`          DECIMAL(10, 7)          DEFAULT NULL COMMENT '下车纬度',
    `dispatch_time`    DATETIME                DEFAULT NULL COMMENT '派单时间',
    `pickup_time`      DATETIME                DEFAULT NULL COMMENT '上车时间',
    `finish_time`      DATETIME                DEFAULT NULL COMMENT '下车时间',
    `calc_time`        DATETIME                DEFAULT NULL COMMENT '核算时间',
    `mileage`          DECIMAL(10, 2)          DEFAULT NULL COMMENT '计费里程 km',
    `empty_mileage`    DECIMAL(10, 2)          DEFAULT NULL COMMENT '空驶里程 km',
    `waiting_duration` INT                     DEFAULT NULL COMMENT '等候时长（秒）',
    `duration_sec`     INT                     DEFAULT NULL COMMENT '行程总时长（秒）',
    `total_amount`     DECIMAL(10, 2)          DEFAULT NULL COMMENT '计价器金额',
    `discount_amount`  DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '优惠金额',
    `payable_amount`   DECIMAL(10, 2)          DEFAULT NULL COMMENT '应收金额（开票金额）',
    `settle_status`    TINYINT        NOT NULL DEFAULT 0 COMMENT '0 未结算 1 已入结算单',
    `abnormal_reason`  VARCHAR(512)            DEFAULT NULL COMMENT '异常原因',
    `cancel_reason`    VARCHAR(512)            DEFAULT NULL COMMENT '取消原因',
    `remark`           VARCHAR(512)            DEFAULT NULL COMMENT '备注',
    `deleted`          TINYINT        NOT NULL DEFAULT 0 COMMENT '软删除',
    `create_time`      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `create_by`        BIGINT                  DEFAULT NULL,
    `update_by`        BIGINT                  DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    UNIQUE KEY `uk_source_business` (`order_source`, `business_id`),
    KEY `idx_driver_finish` (`driver_id`, `finish_time`),
    KEY `idx_fleet_finish` (`fleet_id`, `finish_time`),
    KEY `idx_vehicle_time` (`vehicle_id`, `create_time`),
    KEY `idx_status_time` (`status`, `create_time`),
    KEY `idx_settle` (`settle_status`, `finish_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '订单主表（巡游+电召）';

DROP TABLE IF EXISTS `t_order_item`;
CREATE TABLE `t_order_item` (
    `id`             BIGINT        NOT NULL COMMENT '主键',
    `order_id`       BIGINT        NOT NULL COMMENT '订单 ID（逻辑外键 t_order.id）',
    `order_no`       VARCHAR(64)   NOT NULL COMMENT '订单号（冗余，便于导出）',
    `item_type`      TINYINT       NOT NULL COMMENT '1 起步价 2 里程费 3 等候费 4 低速费 5 夜间加成 6 燃油附加 7 过路费 8 其他 9 优惠',
    `item_name`      VARCHAR(64)   NOT NULL COMMENT '费用名称',
    `unit_price`     DECIMAL(10, 4)         DEFAULT NULL COMMENT '单价',
    `quantity`       DECIMAL(10, 2)         DEFAULT NULL COMMENT '数量（里程/分钟/次数）',
    `amount`         DECIMAL(10, 2) NOT NULL COMMENT '金额',
    `calc_snapshot`  JSON                   DEFAULT NULL COMMENT '计算规则快照，保证可追溯',
    `sort_no`        INT           NOT NULL DEFAULT 0 COMMENT '排序',
    `deleted`        TINYINT       NOT NULL DEFAULT 0 COMMENT '软删除',
    `create_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `create_by`      BIGINT                 DEFAULT NULL,
    `update_by`      BIGINT                 DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_order_no` (`order_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '订单费用明细';

DROP TABLE IF EXISTS `t_settle_record`;
CREATE TABLE `t_settle_record` (
    `id`                  BIGINT        NOT NULL COMMENT '主键',
    `settle_no`           VARCHAR(64)   NOT NULL COMMENT '结算单号',
    `period_start`        DATE          NOT NULL COMMENT '结算周期开始',
    `period_end`          DATE          NOT NULL COMMENT '结算周期结束',
    `driver_id`           BIGINT        NOT NULL COMMENT '司机 ID',
    `fleet_id`            BIGINT                 DEFAULT NULL COMMENT '车队 ID',
    `order_count`         INT           NOT NULL DEFAULT 0 COMMENT '订单数',
    `total_amount`        DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '营业总额',
    `platform_fee`        DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '平台服务费',
    `driver_share_amount` DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '司机分成',
    `adjust_amount`       DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '调整金额',
    `payable_amount`      DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '应结金额',
    `status`              TINYINT       NOT NULL DEFAULT 1 COMMENT '1 待确认 2 已确认 3 已打款',
    `confirm_time`        DATETIME               DEFAULT NULL COMMENT '确认时间',
    `pay_time`            DATETIME               DEFAULT NULL COMMENT '打款时间',
    `operator_id`         BIGINT                 DEFAULT NULL COMMENT '操作员',
    `remark`              VARCHAR(255)           DEFAULT NULL COMMENT '备注',
    `deleted`             TINYINT       NOT NULL DEFAULT 0 COMMENT '软删除',
    `create_time`         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `create_by`           BIGINT                 DEFAULT NULL,
    `update_by`           BIGINT                 DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_settle_no` (`settle_no`),
    KEY `idx_driver_period` (`driver_id`, `period_start`, `period_end`),
    KEY `idx_fleet_period` (`fleet_id`, `period_start`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '司机结算单';

-- ---------------------------------------------------------------------
-- 四、报警域
-- ---------------------------------------------------------------------

DROP TABLE IF EXISTS `t_alarm_record`;
CREATE TABLE `t_alarm_record` (
    `id`              BIGINT       NOT NULL COMMENT '主键',
    `alarm_no`        VARCHAR(64)  NOT NULL COMMENT '报警单号',
    `alarm_type`      VARCHAR(48)  NOT NULL COMMENT '报警类型，见 AlarmType',
    `alarm_level`     TINYINT      NOT NULL COMMENT '1 一级 2 二级 3 三级',
    `source`          TINYINT      NOT NULL DEFAULT 1 COMMENT '1 808 报警位 2 主动安全扩展 3 平台规则（围栏）',
    `alarm_flag_hex`  VARCHAR(32)           DEFAULT NULL COMMENT '0x0200 报警标志位原值（HEX）',
    `ext_alarm_code`  VARCHAR(16)           DEFAULT NULL COMMENT '主动安全扩展报警码',
    `biz_alarm_id`    VARCHAR(64)           DEFAULT NULL COMMENT '终端报警 ID（用于报警开始/结束配对）',
    `vehicle_id`      BIGINT                DEFAULT NULL COMMENT '车辆 ID',
    `driver_id`       BIGINT                DEFAULT NULL COMMENT '司机 ID',
    `terminal_id`     VARCHAR(32)           DEFAULT NULL COMMENT '终端手机号',
    `order_id`        BIGINT                DEFAULT NULL COMMENT '关联订单 ID',
    `alarm_time`      DATETIME     NOT NULL COMMENT '报警时间',
    `lng`             DECIMAL(10, 7)        DEFAULT NULL COMMENT '经度',
    `lat`             DECIMAL(10, 7)        DEFAULT NULL COMMENT '纬度',
    `speed`           DECIMAL(6, 2)         DEFAULT NULL COMMENT '速度 km/h',
    `address`         VARCHAR(255)          DEFAULT NULL COMMENT '逆地理地址',
    `handle_status`   TINYINT      NOT NULL DEFAULT 0 COMMENT '0 未处置 1 处置中 2 已处置 3 已忽略',
    `handle_user_id`  BIGINT                DEFAULT NULL COMMENT '处置人',
    `handle_time`     DATETIME              DEFAULT NULL COMMENT '处置时间',
    `handle_remark`   VARCHAR(512)          DEFAULT NULL COMMENT '处置说明',
    `escalate_flag`   TINYINT      NOT NULL DEFAULT 0 COMMENT '1 已升级',
    `escalate_time`   DATETIME              DEFAULT NULL COMMENT '升级时间',
    `score_deducted`  DECIMAL(5, 2)         DEFAULT NULL COMMENT '计入安全评分的扣分',
    `raw_hex`         MEDIUMTEXT            DEFAULT NULL COMMENT '原始报文 HEX',
    `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `create_by`       BIGINT                DEFAULT NULL,
    `update_by`       BIGINT                DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_alarm_no` (`alarm_no`),
    KEY `idx_time_level` (`alarm_time`, `alarm_level`),
    KEY `idx_vehicle_time` (`vehicle_id`, `alarm_time`),
    KEY `idx_driver_time` (`driver_id`, `alarm_time`),
    KEY `idx_handle` (`handle_status`, `alarm_level`),
    KEY `idx_biz_alarm` (`terminal_id`, `biz_alarm_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '报警记录';

DROP TABLE IF EXISTS `t_alarm_media`;
CREATE TABLE `t_alarm_media` (
    `id`             BIGINT      NOT NULL COMMENT '主键',
    `alarm_id`       BIGINT      NOT NULL COMMENT '报警 ID（逻辑外键 t_alarm_record.id）',
    `alarm_no`       VARCHAR(64)          DEFAULT NULL COMMENT '报警单号（冗余）',
    `media_type`     TINYINT     NOT NULL COMMENT '1 图片 2 音频 3 视频',
    `channel_no`     TINYINT              DEFAULT NULL COMMENT '1078 通道 1 DSM 2 车内全景 3 ADAS 前路 4 车尾侧视',
    `stream_type`    TINYINT              DEFAULT NULL COMMENT '1 主码流 2 子码流',
    `media_id`       BIGINT               DEFAULT NULL COMMENT '808 多媒体数据 ID',
    `file_url`       VARCHAR(512)         DEFAULT NULL COMMENT '对象存储地址',
    `file_size`      BIGINT               DEFAULT NULL COMMENT '文件大小（字节）',
    `duration_sec`   INT                  DEFAULT NULL COMMENT '时长（秒）',
    `start_time`     DATETIME             DEFAULT NULL COMMENT '媒体开始时间',
    `end_time`       DATETIME             DEFAULT NULL COMMENT '媒体结束时间',
    `snapshot_time`  DATETIME             DEFAULT NULL COMMENT '抓拍时间',
    `upload_status`  TINYINT     NOT NULL DEFAULT 0 COMMENT '0 待上传 1 上传中 2 已归档 3 失败',
    `retain_until`   DATETIME             DEFAULT NULL COMMENT '存证到期时间',
    `deleted`        TINYINT     NOT NULL DEFAULT 0 COMMENT '软删除',
    `create_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `create_by`      BIGINT               DEFAULT NULL,
    `update_by`      BIGINT               DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_alarm_id` (`alarm_id`),
    KEY `idx_media_id` (`media_id`),
    KEY `idx_upload_status` (`upload_status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '报警媒体';

DROP TABLE IF EXISTS `t_alarm_rule_config`;
CREATE TABLE `t_alarm_rule_config` (
    `id`               BIGINT      NOT NULL COMMENT '主键',
    `rule_code`        VARCHAR(64) NOT NULL COMMENT '规则编码，如 OVERSPEED_DEFAULT',
    `alarm_type`       VARCHAR(48) NOT NULL COMMENT '报警类型，见 AlarmType',
    `alarm_level`      TINYINT     NOT NULL COMMENT '等级 1/2/3',
    `scope_type`       TINYINT     NOT NULL DEFAULT 1 COMMENT '1 全局 2 车队 3 车辆',
    `scope_id`         BIGINT      NOT NULL DEFAULT 0 COMMENT '车队/车辆 ID，全局为 0',
    `ext_alarm_code`   VARCHAR(16)          DEFAULT NULL COMMENT '终端主动安全扩展报警码',
    `threshold_json`   JSON                 DEFAULT NULL COMMENT '阈值：速度/持续秒数/窗口次数等',
    `notify_channel`   VARCHAR(32)          DEFAULT NULL COMMENT '通知渠道位串：SITE/SMS/WS',
    `popup_flag`       TINYINT     NOT NULL DEFAULT 0 COMMENT '1 弹窗',
    `voice_flag`       TINYINT     NOT NULL DEFAULT 0 COMMENT '1 提示音',
    `escalate_sec`     INT                  DEFAULT NULL COMMENT '超时升级秒数',
    `record_before_sec` INT                 DEFAULT NULL COMMENT '联动录像前置秒数',
    `record_after_sec`  INT                 DEFAULT NULL COMMENT '联动录像后置秒数',
    `enabled`          TINYINT     NOT NULL DEFAULT 1 COMMENT '1 启用 0 停用',
    `effective_time`   DATETIME             DEFAULT NULL COMMENT '生效时间',
    `deleted`          TINYINT     NOT NULL DEFAULT 0 COMMENT '软删除',
    `create_time`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `create_by`        BIGINT               DEFAULT NULL,
    `update_by`        BIGINT               DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_scope` (`rule_code`, `scope_type`, `scope_id`),
    KEY `idx_alarm_type` (`alarm_type`),
    KEY `idx_enabled` (`enabled`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '报警规则';

-- ---------------------------------------------------------------------
-- 五、发票域
-- ---------------------------------------------------------------------

DROP TABLE IF EXISTS `t_invoice_record`;
CREATE TABLE `t_invoice_record` (
    `id`              BIGINT        NOT NULL COMMENT '主键',
    `order_id`        BIGINT        NOT NULL COMMENT '订单 ID（诺诺 orderNo 即取此值）',
    `order_no`        VARCHAR(64)   NOT NULL COMMENT '平台订单号',
    `nuonuo_order_no` VARCHAR(64)            DEFAULT NULL COMMENT '诺诺订单号回执',
    `invoice_no`      VARCHAR(64)            DEFAULT NULL COMMENT '发票号码',
    `invoice_type`    TINYINT       NOT NULL DEFAULT 1 COMMENT '1 蓝票 2 红票',
    `tax_no`          VARCHAR(32)            DEFAULT NULL COMMENT '销方税号（车队）',
    `buyer_name`      VARCHAR(128)           DEFAULT NULL COMMENT '购方名称',
    `buyer_email`     VARCHAR(128)           DEFAULT NULL COMMENT '购方邮箱',
    `buyer_tax_no`    VARCHAR(32)            DEFAULT NULL COMMENT '购方税号',
    `amount`          DECIMAL(10, 2)         DEFAULT NULL COMMENT '开票金额',
    `tax_amount`      DECIMAL(10, 2)         DEFAULT NULL COMMENT '税额',
    `status`          TINYINT       NOT NULL DEFAULT 1 COMMENT '1 待开 2 开票中 3 成功 4 失败 5 已红冲',
    `qr_code_url`     VARCHAR(512)           DEFAULT NULL COMMENT '发票二维码地址',
    `pdf_url`         VARCHAR(512)           DEFAULT NULL COMMENT '发票 PDF 地址',
    `fail_code`       VARCHAR(32)            DEFAULT NULL COMMENT '诺诺失败码',
    `fail_msg`        VARCHAR(512)           DEFAULT NULL COMMENT '失败原因',
    `retry_count`     INT           NOT NULL DEFAULT 0 COMMENT '已重试次数',
    `request_id`      VARCHAR(64)            DEFAULT NULL COMMENT '请求幂等号',
    `invoice_time`    DATETIME               DEFAULT NULL COMMENT '开票成功时间',
    `red_time`        DATETIME               DEFAULT NULL COMMENT '红冲时间',
    `red_reason`      VARCHAR(255)           DEFAULT NULL COMMENT '红冲原因',
    `orig_invoice_id` BIGINT                 DEFAULT NULL COMMENT '红冲对应的原蓝票 ID',
    `deleted`         TINYINT       NOT NULL DEFAULT 0 COMMENT '软删除',
    `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `create_by`       BIGINT                 DEFAULT NULL,
    `update_by`       BIGINT                 DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_id` (`order_id`),
    KEY `idx_invoice_no` (`invoice_no`),
    KEY `idx_status` (`status`),
    KEY `idx_invoice_time` (`invoice_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '电子发票记录（幂等核心表）';

DROP TABLE IF EXISTS `t_terminal_invoice_dispatch`;
CREATE TABLE `t_terminal_invoice_dispatch` (
    `id`                BIGINT      NOT NULL COMMENT '主键',
    `invoice_id`        BIGINT      NOT NULL COMMENT '发票记录 ID',
    `order_id`          BIGINT      NOT NULL COMMENT '订单 ID',
    `terminal_id`       VARCHAR(32)          DEFAULT NULL COMMENT '目标终端',
    `vehicle_id`        BIGINT               DEFAULT NULL COMMENT '车辆 ID',
    `dispatch_type`     TINYINT     NOT NULL COMMENT '1 自定义 0x8F01 2 文本 0x8300 降级',
    `msg_seq`           INT                  DEFAULT NULL COMMENT '下发报文流水号',
    `dispatch_content`  TEXT                 DEFAULT NULL COMMENT '下发内容（HEX 或文本/短链）',
    `dispatch_status`   TINYINT     NOT NULL DEFAULT 0 COMMENT '0 待发 1 已发 2 成功 3 失败 4 超限终止',
    `retry_count`       INT         NOT NULL DEFAULT 0 COMMENT '重试次数',
    `next_retry_time`   DATETIME             DEFAULT NULL COMMENT '下次重试时间',
    `last_send_time`    DATETIME             DEFAULT NULL COMMENT '最后发送时间',
    `resp_code`         VARCHAR(16)          DEFAULT NULL COMMENT '终端应答结果',
    `resp_msg`          VARCHAR(255)         DEFAULT NULL COMMENT '终端应答描述',
    `create_time`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_status_retry` (`dispatch_status`, `next_retry_time`),
    KEY `idx_invoice_id` (`invoice_id`),
    KEY `idx_terminal_id` (`terminal_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '发票下发任务（0x8F01/0x8300）';

-- ---------------------------------------------------------------------
-- 六、系统域：操作日志 / 参数 / 围栏 / 通知 / 重试任务
-- ---------------------------------------------------------------------

DROP TABLE IF EXISTS `t_operation_log`;
CREATE TABLE `t_operation_log` (
    `id`                BIGINT      NOT NULL COMMENT '主键',
    `trace_id`          VARCHAR(64)          DEFAULT NULL COMMENT '全链路追踪号',
    `user_id`           BIGINT               DEFAULT NULL COMMENT '操作人 ID',
    `user_name`         VARCHAR(64)          DEFAULT NULL COMMENT '操作人',
    `module`            VARCHAR(64)          DEFAULT NULL COMMENT '模块',
    `action`            VARCHAR(64)          DEFAULT NULL COMMENT '动作',
    `biz_type`          VARCHAR(64)          DEFAULT NULL COMMENT '业务类型',
    `biz_id`            VARCHAR(64)          DEFAULT NULL COMMENT '业务主键',
    `method`            VARCHAR(255)         DEFAULT NULL COMMENT '类名.方法名',
    `request_uri`       VARCHAR(512)         DEFAULT NULL COMMENT '请求地址',
    `request_param`     TEXT                 DEFAULT NULL COMMENT '请求参数（4KB 截断）',
    `response_snapshot` TEXT                 DEFAULT NULL COMMENT '响应快照（4KB 截断）',
    `result`            TINYINT     NOT NULL DEFAULT 1 COMMENT '1 成功 0 失败',
    `error_msg`         VARCHAR(1024)        DEFAULT NULL COMMENT '异常信息',
    `ip`                VARCHAR(64)          DEFAULT NULL COMMENT '来源 IP',
    `user_agent`        VARCHAR(512)         DEFAULT NULL COMMENT 'UA',
    `cost_ms`           BIGINT               DEFAULT NULL COMMENT '耗时毫秒',
    `op_time`           DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    `deleted`           TINYINT     NOT NULL DEFAULT 0 COMMENT '软删除（日志不物理删）',
    `create_time`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `create_by`         BIGINT               DEFAULT NULL,
    `update_by`         BIGINT               DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_op_time` (`op_time`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_biz` (`biz_type`, `biz_id`),
    KEY `idx_trace` (`trace_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '全链路操作日志';

DROP TABLE IF EXISTS `t_sys_config`;
CREATE TABLE `t_sys_config` (
    `id`           BIGINT      NOT NULL COMMENT '主键',
    `config_key`   VARCHAR(128) NOT NULL COMMENT '参数键',
    `config_value` TEXT                 DEFAULT NULL COMMENT '参数值',
    `value_type`   VARCHAR(16) NOT NULL DEFAULT 'STRING' COMMENT 'STRING/INT/DECIMAL/BOOLEAN/JSON',
    `scope_type`   TINYINT     NOT NULL DEFAULT 1 COMMENT '1 全局 2 车队 3 车辆',
    `scope_id`     BIGINT      NOT NULL DEFAULT 0 COMMENT '车队/车辆 ID，全局为 0',
    `config_group` VARCHAR(64)          DEFAULT NULL COMMENT '分组',
    `description`  VARCHAR(255)         DEFAULT NULL COMMENT '说明',
    `editable`     TINYINT     NOT NULL DEFAULT 1 COMMENT '1 可编辑 0 只读',
    `deleted`      TINYINT     NOT NULL DEFAULT 0 COMMENT '软删除',
    `create_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `create_by`    BIGINT               DEFAULT NULL,
    `update_by`    BIGINT               DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_config_scope` (`config_key`, `scope_type`, `scope_id`),
    KEY `idx_group` (`config_group`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '系统参数（全局/车队/车辆三级）';

DROP TABLE IF EXISTS `t_electronic_fence`;
CREATE TABLE `t_electronic_fence` (
    `id`           BIGINT       NOT NULL COMMENT '主键',
    `fence_no`     VARCHAR(64)  NOT NULL COMMENT '围栏编号',
    `fence_name`   VARCHAR(128) NOT NULL COMMENT '围栏名称',
    `fence_type`   TINYINT      NOT NULL DEFAULT 1 COMMENT '1 圆形 2 多边形',
    `center_lng`   DECIMAL(10, 7)        DEFAULT NULL COMMENT '圆心经度',
    `center_lat`   DECIMAL(10, 7)        DEFAULT NULL COMMENT '圆心纬度',
    `radius`       INT                   DEFAULT NULL COMMENT '半径（米）',
    `points_json`  JSON                  DEFAULT NULL COMMENT '多边形顶点 [{lng,lat}]',
    `fence_action` TINYINT      NOT NULL DEFAULT 3 COMMENT '1 进 2 出 3 进出',
    `scope_type`   TINYINT      NOT NULL DEFAULT 1 COMMENT '1 车队 2 车辆 3 司机',
    `scope_id`     BIGINT       NOT NULL DEFAULT 0 COMMENT '范围对象 ID',
    `alarm_level`  TINYINT      NOT NULL DEFAULT 1 COMMENT '触发报警等级',
    `start_time`   DATETIME              DEFAULT NULL COMMENT '生效开始',
    `end_time`     DATETIME              DEFAULT NULL COMMENT '生效结束',
    `enabled`      TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 0 停用',
    `deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `create_by`    BIGINT                DEFAULT NULL,
    `update_by`    BIGINT                DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_fence_no` (`fence_no`),
    KEY `idx_enabled` (`enabled`),
    KEY `idx_scope` (`scope_type`, `scope_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '电子围栏';

DROP TABLE IF EXISTS `t_notice`;
CREATE TABLE `t_notice` (
    `id`          BIGINT      NOT NULL COMMENT '主键',
    `notice_type` VARCHAR(32) NOT NULL COMMENT '类型：ALARM/INVOICE/CERT/SYSTEM',
    `channel`     VARCHAR(16) NOT NULL COMMENT '渠道：SITE 站内 / SMS / WS',
    `receiver`    VARCHAR(64)          DEFAULT NULL COMMENT '接收人（用户 ID 或手机号密文）',
    `biz_type`    VARCHAR(32)          DEFAULT NULL COMMENT '关联业务类型',
    `biz_id`      VARCHAR(64)          DEFAULT NULL COMMENT '关联业务 ID',
    `title`       VARCHAR(128)         DEFAULT NULL COMMENT '标题',
    `content`     VARCHAR(1024)        DEFAULT NULL COMMENT '内容',
    `send_status` TINYINT     NOT NULL DEFAULT 0 COMMENT '0 待发 1 已发 2 失败',
    `fail_msg`    VARCHAR(255)         DEFAULT NULL COMMENT '失败原因',
    `send_time`   DATETIME             DEFAULT NULL COMMENT '发送时间',
    `deleted`     TINYINT     NOT NULL DEFAULT 0 COMMENT '软删除',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `create_by`   BIGINT               DEFAULT NULL,
    `update_by`   BIGINT               DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_receiver` (`receiver`),
    KEY `idx_biz` (`biz_type`, `biz_id`),
    KEY `idx_status` (`send_status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '消息通知';

DROP TABLE IF EXISTS `t_cmd_retry`;
CREATE TABLE `t_cmd_retry` (
    `id`              BIGINT      NOT NULL COMMENT '主键',
    `biz_type`        VARCHAR(32) NOT NULL COMMENT 'PARAM_SET 参数下发 / SNAPSHOT 抓拍 / INVOICE_DISPATCH 发票下发',
    `biz_id`          VARCHAR(64)          DEFAULT NULL COMMENT '业务 ID',
    `terminal_id`     VARCHAR(32) NOT NULL COMMENT '目标终端',
    `order_no`        VARCHAR(64)          DEFAULT NULL COMMENT '关联订单号',
    `msg_id`          VARCHAR(8)           DEFAULT NULL COMMENT '下行消息 ID，如 0x8103',
    `msg_seq`         INT                  DEFAULT NULL COMMENT '下行流水号',
    `payload`         TEXT                 DEFAULT NULL COMMENT '下发内容（HEX）',
    `retry_count`     INT         NOT NULL DEFAULT 0 COMMENT '已重试次数',
    `max_retry`       INT         NOT NULL DEFAULT 5 COMMENT '最大重试次数',
    `next_retry_time` DATETIME    NOT NULL COMMENT '下次执行时间',
    `status`          TINYINT     NOT NULL DEFAULT 0 COMMENT '0 待发 1 已发待应答 2 成功 3 失败待重试 4 超限终止',
    `last_resp`       VARCHAR(255)         DEFAULT NULL COMMENT '最近应答',
    `trace_id`        VARCHAR(64)          DEFAULT NULL COMMENT '追踪号',
    `deleted`         TINYINT     NOT NULL DEFAULT 0 COMMENT '软删除',
    `create_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `create_by`       BIGINT               DEFAULT NULL,
    `update_by`       BIGINT               DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_status_retry` (`status`, `next_retry_time`),
    KEY `idx_terminal` (`terminal_id`),
    KEY `idx_biz` (`biz_type`, `biz_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '下行指令重试任务表';

DROP TABLE IF EXISTS `t_pricing_plan`;
CREATE TABLE `t_pricing_plan` (
    `id`                   BIGINT         NOT NULL COMMENT '主键',
    `plan_code`            VARCHAR(64)    NOT NULL COMMENT '方案编码',
    `plan_name`            VARCHAR(128)   NOT NULL COMMENT '方案名称',
    `strategy_type`        VARCHAR(32)    NOT NULL DEFAULT 'STANDARD' COMMENT '计价策略：STANDARD 标准 / NIGHT 夜间 / AIRPORT 机场 / CUSTOM 自定义',
    `scope_type`           TINYINT        NOT NULL DEFAULT 1 COMMENT '1 全局 2 车队 3 车辆',
    `scope_id`             BIGINT         NOT NULL DEFAULT 0 COMMENT '车队/车辆 ID',
    `base_fare`            DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '起步价',
    `base_mileage`         DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '起步里程 km',
    `per_km_price`         DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '每公里单价',
    `waiting_price_per_min` DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '每分钟等候费',
    `low_speed_price_per_min` DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '低速行驶每分钟费用',
    `night_rate_json`      JSON                    DEFAULT NULL COMMENT '夜间加成时段与系数',
    `fuel_surcharge`       DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '燃油附加费',
    `driver_share_rate`    DECIMAL(5, 4)  NOT NULL DEFAULT 1.0000 COMMENT '司机分成比例',
    `priority`             INT            NOT NULL DEFAULT 0 COMMENT '优先级，大者优先',
    `effective_time`       DATETIME                DEFAULT NULL COMMENT '生效时间',
    `enabled`              TINYINT        NOT NULL DEFAULT 1 COMMENT '1 启用 0 停用',
    `remark`               VARCHAR(255)            DEFAULT NULL COMMENT '备注',
    `deleted`              TINYINT        NOT NULL DEFAULT 0 COMMENT '软删除',
    `create_time`          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `create_by`            BIGINT                  DEFAULT NULL,
    `update_by`            BIGINT                  DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plan_code` (`plan_code`),
    KEY `idx_scope` (`scope_type`, `scope_id`),
    KEY `idx_enabled` (`enabled`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '计价方案（策略模式配置）';

DROP TABLE IF EXISTS `t_pricing_plan_item`;
CREATE TABLE `t_pricing_plan_item` (
    `id`          BIGINT         NOT NULL COMMENT '主键',
    `plan_id`     BIGINT         NOT NULL COMMENT '方案 ID（逻辑外键 t_pricing_plan.id）',
    `item_type`   TINYINT        NOT NULL COMMENT '1 起步价 2 阶梯里程 3 等候 4 低速 5 夜间 6 附加',
    `seg_start`   DECIMAL(10, 2)          DEFAULT NULL COMMENT '区间起点（km 或 分钟）',
    `seg_end`     DECIMAL(10, 2)          DEFAULT NULL COMMENT '区间终点',
    `unit_price`  DECIMAL(10, 2) NOT NULL COMMENT '单价',
    `extra_json`  JSON                    DEFAULT NULL COMMENT '扩展参数',
    `sort_no`     INT            NOT NULL DEFAULT 0 COMMENT '排序',
    `deleted`     TINYINT        NOT NULL DEFAULT 0 COMMENT '软删除',
    `create_time` DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `create_by`   BIGINT                  DEFAULT NULL,
    `update_by`   BIGINT                  DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_plan_id` (`plan_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '计价方案阶梯明细';

-- ---------------------------------------------------------------------
-- 阶段 3 补充：订单状态流转日志（C7）
-- 每次流转一条，供订单时间线与状态审计；与 t_operation_log（接口级）互补
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `t_order_status_log`;
CREATE TABLE `t_order_status_log` (
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
