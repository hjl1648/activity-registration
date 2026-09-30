# 限量活动报名系统

可启动、可演示、可验证的小型业务项目（机试交付）。

## 技术版本（固定）

| 组件 | 版本 |
|---|---|
| Java | 17 |
| Spring Boot | 3.2.5 |
| MySQL | 8.0.12（InnoDB） |
| Redis | 3.2.100（Windows 版，Lettuce 客户端） |
| 持久层 | Spring JDBC |
| 前端 | 原生 HTML + JavaScript（`src/main/resources/static/index.html`） |
| 深化选项 | **D Python 数据对账工具** |

## 深化声明

本项目选择深化方向 **D：Python 只读对账 CLI**（`tools/reconcile.py`），不叠加 MQ/Mongo/ES。

## 运行条件

- JDK 17、Maven 3.6+
- 真实 MySQL（建议端口 3307，库 `activity_reg`，账号见配置样例）
- 真实 Redis（默认 `127.0.0.1:6379`）
- Python 3.8+（验收脚本与对账，依赖 `pymysql`）

## 初始化

### 1. MySQL

```bash
mysql -h127.0.0.1 -P3307 -uroot -proot --default-character-set=utf8mb4 < db/schema.sql
```

重置数据：

```bash
mysql -h127.0.0.1 -P3307 -uroot -proot --default-character-set=utf8mb4 < db/reset.sql
```

本机演示环境也可将 MySQL 数据目录放在 ASCII 路径（如 `D:\activity-reg-runtime`），避免中文路径导致 `mysqld` 读配置失败。

### 2. Redis

启动 Redis Server，确认 `PING` 返回 `PONG`。

### 3. 应用配置

编辑 `src/main/resources/application.yml`：

- `spring.datasource.*`
- `spring.data.redis.*`
- `app.cache.activity-ttl-seconds`（建议验证时设为 30–60）

**禁止把生产凭据写入仓库。** 当前样例仅为本机演示账号。

## 启动

```bash
set JAVA_HOME=D:\jdk-17.0.6
mvn -DskipTests spring-boot:run
```

或：

```bash
java -jar target/activity-registration-1.0.0.jar
```

- API：`http://127.0.0.1:8080/api/activities`
- 演示页：`http://127.0.0.1:8080/`

## 测试命令

```bash
pip install pymysql
python tools/acceptance_test.py http://127.0.0.1:8080
python tools/reconcile.py --host 127.0.0.1 --port 3307 --user root --password root --database activity_reg
```

## 完成度

- [x] 活动列表/详情（剩余名额以 MySQL 为准）
- [x] 报名：事务、幂等重放、业务去重、参数冲突、并发收敛
- [x] 我的报名查询与跨用户 404 隔离
- [x] 真实 Redis 缓存活动基础信息 + 失败回源
- [x] 演示单页
- [x] 深化 D 对账 CLI
- [x] 交付文档

## 已知缺陷 / 生产差距

- `X-User-Id` 仅为演示身份标记，非生产认证
- 报名 ID 使用短 UUID，非雪花/号段
- Redis 仅缓存基础字段，无预热/热点重建
- 未做多活部署与完整可观测性平台
