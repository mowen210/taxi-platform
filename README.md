# 出租汽车运营监管平台（taxi-platform）

面向巡游出租汽车的「终端—平台—监管」一体化平台。终端侧为计价器 + ISU（串口 + 808/905/1078），平台侧负责协议解析、订单、报警、发票、监控与结算。

> 当前版本：**阶段 6~10（报警与媒体、计价结算、发票、报表大屏、司机端）**
> 阶段 1~10 均已真实编译通过、单元测试通过（65 个用例），并在 MySQL + Redis + 真 TCP 报文环境实测。

## 一、技术栈

| 层 | 选型 |
|---|---|
| 后端 | Spring Boot 3.3.5 + MyBatis-Plus 3.5.7 + Netty 4.1（808 接入） |
| 数据库 | MySQL 8.0（utf8mb4，无物理外键，逻辑外键 + 软删除） |
| 缓存 | Redis 7（鉴权码、会话映射、权限缓存、限流、幂等） |
| 时序库 | TDengine 3.x（轨迹，生产）；开发机可用 `track.provider=mysql` 兜底表联调 |
| 认证 | JWT（HS256，jti 黑名单）+ BCrypt 口令 |
| 文档 | SpringDoc OpenAPI（/swagger-ui.html） |
| 部署 | Docker Compose + Nginx |

## 二、模块与依赖方向

```
taxi-api ─┬→ taxi-application → taxi-infra → taxi-domain → taxi-common
          └→ taxi-protocol（808/905 帧编解码、消息体、分包重组，纯 JDK）→ taxi-common
```

各模块职责：

| 模块 | 职责 |
|---|---|
| taxi-common | 统一返回/错误码/异常/加解密/脱敏/JWT/枚举/上下文 |
| taxi-domain | 实体、仓储接口、订单状态机 |
| taxi-infra | MyBatis-Plus Mapper 与仓储实现、Redis、审计填充、原始报文落库 |
| taxi-application | 用例编排、事务边界、认证与 RBAC 服务、808 接入用例 |
| taxi-protocol | JT/T 808 拆帧/转义/校验/分包重组、0x0100/0x0102/0x0002/0x0200/0x0B02/0x0B05 编解码 |
| taxi-api | 控制器、JWT 过滤器、权限切面、限流、操作日志、808 Netty 服务、启动类 |

依赖严格单向。1078 流媒体与报警媒体复用 `taxi-protocol` + `taxi-application`，不单开模块。

## 三、快速启动（Docker Compose）

```bash
cp config/taxi.env.example config/taxi.env   # 唯一配置源：数据库、Redis、密钥、端口、媒体等
# 编辑 config/taxi.env（Docker 的 DB_HOST/REDIS_HOST 用 mysql/redis）
mvn -B -DskipTests package                     # 生成 taxi-api/target/taxi-platform.jar
docker compose -f deploy/docker-compose.yml --env-file config/taxi.env up -d
```

Spring Boot 启动时会自动加载 `config/taxi.env`；代码只引用 `${KEY:默认值}`，
不再从多处读取配置。加载优先级：系统环境变量/`-D` > `config/taxi.env` > `application.yml` 默认值。

首次启动会自动执行 `deploy/mysql/init/01_schema.sql`、`02_seed.sql`、`03_stage2_menu.sql`、`04_stage3_order.sql`、`05_stage5_monitor.sql`、`06_stage6_alarm.sql`、`07_stage7_billing.sql`、`08_stage8_invoice.sql`、`09_stage9_stats.sql`、`10_stage10_driver.sql`。

- 健康检查：`GET http://localhost:8080/api/v1/health`
- 接口文档：`http://localhost:8080/swagger-ui.html`
- 实时监控：`http://localhost:8080/monitor.html`（高德地图加 `?amapKey=`，默认 Canvas）
- 报警中心：`http://localhost:8080/alarm.html`（WebSocket 实时推送）
- 数据大屏：`http://localhost:8080/dashboard.html`
- 司机端：`http://localhost:8080/driver.html`（演示 `driver01 / Admin@123`）
- 实时推送：`ws://localhost:8080/ws/v1/monitor?token=<JWT>`
- 808 终端接入：`tcp://localhost:7611`（容器内同名端口，需在 compose 中映射）
- 默认账号：`admin / Admin@123`（**首次登录后必须修改**）

## 四、本地开发

```bash
# 1. 准备 MySQL8 与 Redis，执行建表与种子脚本
mysql -uroot -p < deploy/mysql/init/01_schema.sql
mysql -uroot -p < deploy/mysql/init/02_seed.sql
mysql -uroot -p < deploy/mysql/init/03_stage2_menu.sql
mysql -uroot -p < deploy/mysql/init/04_stage3_order.sql
mysql -uroot -p < deploy/mysql/init/05_stage5_monitor.sql
mysql -uroot -p < deploy/mysql/init/06_stage6_alarm.sql
mysql -uroot -p < deploy/mysql/init/07_stage7_billing.sql
mysql -uroot -p < deploy/mysql/init/08_stage8_invoice.sql
mysql -uroot -p < deploy/mysql/init/09_stage9_stats.sql
mysql -uroot -p < deploy/mysql/init/10_stage10_driver.sql

# 2. 编译打包
mvn -DskipTests package

# 3. 启动（数据库/Redis 连接可用环境变量覆盖）
java -jar taxi-api/target/taxi-platform.jar

# 4. 单元测试
mvn test

# 5. 终端模拟器（平台启动后）
java simulator/Jt808Simulator.java --host 127.0.0.1 --port 7611 \
  --terminal 0138000001 --plate 沪A00001 --business BIZ-20261107-001
```

模拟器说明见 `simulator/README.md`；Python 版本为 `simulator/jt808_simulator.py`。

## 五、阶段进度

| 阶段 | 内容 | 状态 |
|---|---|---|
| 1 | 脚手架、建库、RBAC、统一返回、全局异常、JWT、日志 | ✅ 已交付 |
| 2 | 车辆/司机 CRUD、证件预警、车队管理 | ✅ 已交付 |
| 3 | 订单模型、状态机、电召派单 | ✅ 已交付 |
| 4 | 808 底座、注册鉴权心跳、905 营运报文、模拟器 | ✅ 已交付 |
| 5 | 定位解析、WebSocket、高德监控、轨迹回放 | ✅ 已交付 |
| 6 | 报警解析与三级处置、1078 流媒体、抓拍归档 | ✅ 已交付 |
| 7 | 策略计价引擎、分成结算、导出 | ✅ 已交付 |
| 8 | 诺诺开票、0x8F01 下发 + 0x8300 降级 | ✅ 已交付（mock） |
| 9 | 统计报表、大屏 | ✅ 已交付 |
| 10 | 司机小程序（H5） | ✅ 已交付 |

## 六、编码强制规范落地位置

| 规范 | 实现位置 |
|---|---|
| REST 统一返回 | `com.taxi.common.result.R`，控制器一律 `R.ok(...)` |
| 全局异常拦截 | `com.taxi.api.advice.GlobalExceptionHandler` |
| 参数校验 | DTO record + `jakarta.validation` + `@Valid` |
| 写操作幂等 | 订单幂等键（Redis `IdempotencyStore`）+ 905 `business_id` 业务幂等 + 唯一索引 |
| 本地事务 + 重试任务表 | `@Transactional` + `t_cmd_retry`（阶段 4 起启用） |
| 手机号/身份证 AES | `AesGcmCipher`（AES-256/GCM）+ 仓储层加解密 + `phone_hash` 检索 |
| 页面脱敏 | `MaskUtil` + `UserAssembler`，接口不返回明文 |
| 软删除 | `@TableLogic` + `deleted TINYINT DEFAULT 0` |
| 审计字段 | `AuditMetaObjectHandler` 自动填充 |
| 日志分级 | `logback-spring.xml`（INFO/WARN/ERROR 分文件 + traceId） |
| 原始报文落表 | `t_taximeter_raw_YYYYMMDD`（按日自动建表，成功/失败均留痕） |

## 七、安全说明

- 生产环境必须通过环境变量覆盖 `JWT_SECRET`、`AES_KEY`、`HMAC_SECRET`，默认值仅供本地联调。
- AES 密钥为 Base64 编码的 32 字节，启动时校验长度，不合格直接拒绝启动。
- 令牌注销采用 jti 黑名单，有效期与令牌一致。
- 权限码缓存于 Redis，角色/菜单变更即时失效，无需等令牌过期。

## 八、文档索引

- `docs/api/阶段1-接口文档.md`：接口契约与示例
- `docs/api/阶段4-接口文档.md`：808 帧格式、消息体字段、应答码、905 契约
- `docs/api/阶段5-接口文档.md`：轨迹、WebSocket、监控页面与轨迹库配置
- `docs/api/阶段6-10-接口文档.md`：报警/媒体、计价结算、发票、报表、司机端接口
- `docs/部署文档.md`：Docker Compose、裸机、TDengine、MediaMTX、Nginx、备份与排障
- `config/taxi.env.example`：唯一运行时配置模板（数据库、Redis、密钥、端口、媒体、报警、结算）
- `docs/test/阶段1-自测说明.md`：环境准备、用例步骤、验收标准
- `docs/test/阶段4-自测说明.md`：808 实测记录、缺陷与修复、清理 SQL
- `docs/test/阶段5-自测说明.md`：轨迹与 WebSocket 实测、未测项、清理 SQL
- `docs/test/阶段6-10-自测说明.md`：报警、结算、开票、统计、司机端实测与限制
- `simulator/README.md`：终端模拟器用法
- `docs/design/00-启动设计.md`：架构、ER、目录、状态机、808 时序与报警位映射
