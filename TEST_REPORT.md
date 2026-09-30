# 测试报告

测试环境：JDK 17.0.6 / Spring Boot 3.2.5 / MySQL 8.0.12@3307 / Redis 3.2.100@6379 / 本机单实例。

## 1. 接口契约与业务（真实接入）

命令：

```bash
python tools/acceptance_test.py http://127.0.0.1:8080
```

结果（重置数据后）：

| 用例 | 结果 |
|---|---|
| normal_and_replay（首次 201 + 同键重放 200 同 ID） | PASS |
| sold_out_and_closed（1003 SOLD_OUT / 1004 ACTIVITY_CLOSED） | PASS |
| already_registered（换键重复） | PASS |
| idempotency_conflict（同键跨活动） | PASS |
| concurrent_quota（争抢后剩余 0，成功数=原剩余） | PASS |
| same_key_concurrent（20 并发：1 新建 + 19 重放，同一 ID） | PASS |

## 2. 数据库一致性（真实核对，非只看 HTTP）

命令：

```bash
python tools/reconcile.py --host 127.0.0.1 --port 3307 --user root --password root --database activity_reg
```

结果：`consistent: true`，`duplicateUserActivity/duplicateUserRequest` 为空；各活动满足 `totalQuota == remainingQuota + successCount`。

验收脚本跑完后抽样：活动 1002 剩余 0、成功 10；对账退出码 0。

## 3. Redis 缓存证据（真实写入/命中）

1. 调用 `GET /api/activities/1001` 两次  
2. 应用日志出现 `Redis WRITE` / `Redis MISS` / `Redis HIT`  
3. `redis-cli GET activity:base:1001` 返回 JSON（含 title/status/totalQuota，**不含剩余名额**）  
4. `TTL` 约为配置的 `app.cache.activity-ttl-seconds`（样例 60）

报名路径不以缓存余量为准；缓存失败仅回源，见 `ActivityCache` 异常吞掉并打 WARN。

## 4. 跨用户隔离

用户 A 报名成功后，用户 B 访问 `GET /api/registrations/{id}` 返回 404 / `REGISTRATION_NOT_FOUND`。

## 5. 同用户不同键并发

对活动 1007 同用户 20 个不同 `requestId` 并发：仅 1 次 HTTP 201，其余 409 `ALREADY_REGISTERED`；库中仅 1 行，名额只扣 1。

## 6. 事务失败边界

唯一键冲突时事务回滚（含已执行的名额扣减），外层在事务外解释为重放或已报名，避免可重复读快照导致误判，且不向客户端返回底层唯一键异常。

## 7. 区分说明

| 能力 | 状态 |
|---|---|
| MySQL / Redis / HTTP API / 演示页 | 真实接入 |
| 深化 D 对账 CLI | 真实只读运行 |
| MQ / Mongo / ES | 未实现（未选择） |
| 生产级登录认证 | 明确不做，仅 X-User-Id 演示标记 |

## 8. 对账异常样例（深化 D）

故意将活动 1001 的 `remaining_quota` +1 后运行对账：`consistent: false`，退出码 1，并指出 `expectedRemaining`。恢复后再次对账退出码 0。证明能定位不一致且默认不改业务数据。

## 已知限制

- 未在本报告中附多 JVM 实例共享库的压测截图；单实例并发已覆盖名额与同键场景。  
- Redis 进程级停机回源可手工验证：停止 redis-server 后详情接口仍返回 DB 数据，报名 API 仍可用。
