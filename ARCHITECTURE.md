# 架构说明

## 模块关系

```
HTTP (Controller)
  ├─ ActivityController / RegistrationController
  │     参数与 X-User-Id 校验、HTTP 状态码
  ↓
Service
  ├─ ActivityService      列表/详情；详情组装时读缓存基础信息 + DB 剩余名额
  └─ RegistrationService  判定顺序、事务报名、幂等重放、并发冲突解释
  ↓
Repo (JDBC)
  ├─ ActivityRepository      FOR UPDATE / 条件扣减
  └─ RegistrationRepository  唯一键约束承载去重
  ↓
MySQL(InnoDB)          Redis(Lettuce)
活动/报名真相源         仅缓存 title/status/totalQuota
```

增加“取消报名”时：主要改 `RegistrationService` + 仓库扣减回补 SQL + 契约测试；HTTP 层只加路由。增加登录时：替换 `X-User-Id` 解析，业务层用户标识接口不变。

## 数据模型

- `activities(id, title, status, total_quota, remaining_quota)`  
  约束：`remaining_quota >= 0` 且 `<= total_quota`
- `registrations(id, activity_id, user_id, request_id, status, created_at)`  
  唯一键：`(user_id, activity_id)`、`(user_id, request_id)`

## 报名判定顺序（与实现一致）

1. 校验 `X-User-Id`、`activityId`、`requestId` 格式  
2. 同用户同 `requestId` 已存在 → 同活动重放 200 / 跨活动 `IDEMPOTENCY_CONFLICT`  
3. 同用户同活动已存在 → `ALREADY_REGISTERED`  
4. `SELECT ... FOR UPDATE` 锁定活动行 → 不存在 404 / `CLOSED` / `SOLD_OUT`  
5. 条件扣减 `remaining_quota` + 插入报名，同事务提交  
6. 唯一键并发冲突：事务回滚后解释为重放或已报名，**不向客户端暴露底层唯一键异常**

失败请求不写入报名，故不绑定 `requestId`。

## 事务 / 并发 / 幂等

- 事务：`TransactionTemplate` 包裹扣减与插入，失败整单回滚  
- 并发名额：行锁 + `remaining_quota > 0` 条件更新  
- 幂等：业务先查 + DB 唯一键兜底；同键并发收敛同一报名 ID  

## 缓存

- Key：`activity:base:{id}`，TTL 可配置（默认 60s）  
- 写：详情回源后写入；读失败/超时仅打日志并回源  
- **报名写路径绝不读取缓存余量**  

## 深化 D

`tools/reconcile.py` 只读核对：`total == remaining + successCount`，并检测重复用户活动/重复请求键。不一致退出码非 0；不自动修复。

## 生产差距

演示身份头、单机 Redis、无审计/MQ、无细粒度限流与完整 tracing。
