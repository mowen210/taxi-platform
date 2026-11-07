# 终端模拟器（808 + 905）

两个等价实现，任选其一，都只依赖本机运行时，不需要装第三方库：

| 文件 | 运行时 | 运行方式 |
|---|---|---|
| `Jt808Simulator.java` | JDK 17+ | `java Jt808Simulator.java --host ...`（单文件源码启动） |
| `jt808_simulator.py` | Python 3.8+ | `python jt808_simulator.py --host ...` |

## 一、前置条件

1. 平台已启动，808 端口（默认 7611）在监听；
2. 终端档案存在，且该终端有**生效**的车辆绑定：

```sql
SELECT d.terminal_id, b.vehicle_id, b.taximeter_sn, b.bind_status
  FROM t_terminal_device d
  JOIN t_device_binding b ON b.terminal_id = d.terminal_id AND b.bind_status = 1
 WHERE d.deleted = 0;
```

本机演示数据为终端 `0138000001` → 车辆 沪A00001 → 计价器 `TXM-DEMO-0001`。

## 二、跑一遍完整流程

```bash
# Java
java Jt808Simulator.java --host 127.0.0.1 --port 7611 \
  --terminal 0138000001 --plate 沪A00001 --business BIZ-20261107-001

# Python
python jt808_simulator.py --host 127.0.0.1 --port 7611 \
  --terminal 0138000001 --plate 沪A00001 --business BIZ-20261107-001
```

流程：`0x0100 注册 → 0x0102 鉴权 → 0x0002 心跳 → 0x0200 位置 → 0x0B02 营运开始 → 0x0B05 营运结束 → 0x0002 心跳`。

每条报文都会打印发送帧 HEX 与平台应答结果码，便于和平台日志逐条对照。

## 三、常用参数

| 参数 | 默认 | 说明 |
|---|---|---|
| `--host` / `--port` | 127.0.0.1 / 7611 | 平台 808 接入地址 |
| `--terminal` | 0138000001 | 终端手机号，必须已备案并绑定车辆 |
| `--plate` | 沪A00001 | 车牌，注册时会与档案比对 |
| `--business` | 当前时间戳 | 905 营运 ID，重复使用可验证幂等 |
| `--mileage` | 8.6 | 计费里程 km |
| `--amount` / `--discount` | 32.5 / 2.5 | 计价金额 / 优惠金额（元） |
| `--waiting` | 120 | 等候时长（秒） |
| `--locations` | 1 | 0x0200 连报点数，用于生成可回放的轨迹（每点经度 +0.0012、纬度 +0.0008） |
| `--register-only` | - | 只注册（验证未备案终端，预期 result=2） |
| `--skip-auth` | - | 跳过鉴权直接发 0x0B02（预期 result=1） |

## 四、验证要点

- 注册成功返回 `0x8100 result=0` 且携带 8 位鉴权码；
- 鉴权、心跳、位置均返回 `0x8001 result=0`；
- `0x0B02` 后订单状态 `PICKUP`，`0x0B05` 后 `CALCULATED`，应收 = 计价 − 优惠；
- 同一 `--business` 重跑不会新增订单和时间线；
- 平台侧原始报文在 `t_taximeter_raw_YYYYMMDD`，`raw_hex` 应与模拟器打印的帧一致（去帧头帧尾后）。

## 五、注意

模拟器只用于联调，不实现真实终端的断线缓存补传、重连退避、多包上传等策略；这些属于 ISU 固件范围，平台不模拟。
