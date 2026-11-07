-- =====================================================================
-- TDengine 轨迹库（阶段 5 启用，阶段 1 仅建库与超级表，便于环境就绪）
-- 版本要求：TDengine 3.x
-- 数据量估算：500 车 × 5~10s 上报 ≈ 50~100 条/s，日 430~860 万条
-- =====================================================================

CREATE DATABASE IF NOT EXISTS taxi_track
    KEEP 180
    DURATION 30
    PRECISION 'ms'
    MINROWS 100
    MAXROWS 4096;

USE taxi_track;

-- 轨迹超级表：一车一子表 t_track_{vehicleId}
CREATE STABLE IF NOT EXISTS st_track (
    ts           TIMESTAMP,
    lng          DOUBLE,
    lat          DOUBLE,
    speed        INT,
    direction    INT,
    altitude     INT,
    mileage      DOUBLE,
    acc_state    TINYINT,
    alarm_flag   BIGINT,
    status_flag  BIGINT,
    order_no     NCHAR(64)
) TAGS (
    vehicle_id   BIGINT,
    terminal_id  NCHAR(32),
    plate_no     NCHAR(16)
);

-- 示例子表（模拟器联调车辆）
CREATE TABLE IF NOT EXISTS t_track_1 USING st_track TAGS (1, '0138000001', '沪A00001');
