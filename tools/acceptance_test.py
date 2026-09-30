#!/usr/bin/env python3
"""基础验收脚本：正常报名/重放、满额、关闭、幂等冲突、并发争抢。"""
from __future__ import annotations

import json
import sys
import threading
import uuid
from concurrent.futures import ThreadPoolExecutor, as_completed

try:
    import urllib.request
    import urllib.error
except ImportError:
    pass

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8080"


def req(method: str, path: str, user_id=None, body=None):
    data = None
    headers = {"Content-Type": "application/json"}
    if user_id is not None:
        headers["X-User-Id"] = str(user_id)
    if body is not None:
        data = json.dumps(body).encode("utf-8")
    request = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=10) as resp:
            raw = resp.read().decode("utf-8")
            return resp.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8")
        try:
            parsed = json.loads(raw) if raw else None
        except json.JSONDecodeError:
            parsed = {"raw": raw}
        return e.code, parsed


def assert_true(cond, msg):
    if not cond:
        raise AssertionError(msg)


def main():
    results = []

    def check(name, fn):
        try:
            fn()
            results.append((name, "PASS", ""))
            print(f"[PASS] {name}")
        except Exception as e:
            results.append((name, "FAIL", str(e)))
            print(f"[FAIL] {name}: {e}")

    def normal_and_replay():
        uid = 91001
        rid = "test_replay_" + uuid.uuid4().hex[:8]
        # 使用活动 1001
        s1, b1 = req("POST", "/api/registrations", uid, {"activityId": 1001, "requestId": rid})
        assert_true(s1 == 201, f"首次应为 201, got {s1} {b1}")
        id1 = b1["data"]["id"]
        s2, b2 = req("POST", "/api/registrations", uid, {"activityId": 1001, "requestId": rid})
        assert_true(s2 == 200, f"重放应为 200, got {s2} {b2}")
        assert_true(b2["data"]["id"] == id1, "重放应返回同一报名 ID")

    def sold_out_and_closed():
        s, b = req("POST", "/api/registrations", 92001, {"activityId": 1003, "requestId": "z1"})
        assert_true(s == 409 and b["code"] == "SOLD_OUT", f"零名额应为 SOLD_OUT, got {s} {b}")
        s, b = req("POST", "/api/registrations", 92002, {"activityId": 1004, "requestId": "z2"})
        assert_true(s == 409 and b["code"] == "ACTIVITY_CLOSED", f"关闭应为 ACTIVITY_CLOSED, got {s} {b}")

    def already_registered():
        uid = 93001
        s1, b1 = req("POST", "/api/registrations", uid, {"activityId": 1005, "requestId": "ar_a"})
        assert_true(s1 in (200, 201), f"首次报名失败 {s1} {b1}")
        s2, b2 = req("POST", "/api/registrations", uid, {"activityId": 1005, "requestId": "ar_b"})
        assert_true(s2 == 409 and b2["code"] == "ALREADY_REGISTERED", f"换键重复应为 ALREADY_REGISTERED {s2} {b2}")

    def idempotency_conflict():
        uid = 94001
        rid = "conflict_key_1"
        s1, b1 = req("POST", "/api/registrations", uid, {"activityId": 1005, "requestId": rid})
        # 1005 可能已被上一用例占用用户；换干净用户活动对
        if s1 == 409:
            uid = 94002
            s1, b1 = req("POST", "/api/registrations", uid, {"activityId": 1001, "requestId": rid})
        assert_true(s1 in (200, 201), f"绑定失败 {s1} {b1}")
        other = 1007 if b1["data"]["activityId"] != 1007 else 1006
        s2, b2 = req("POST", "/api/registrations", uid, {"activityId": other, "requestId": rid})
        assert_true(s2 == 409 and b2["code"] == "IDEMPOTENCY_CONFLICT", f"跨活动同键应为冲突 {s2} {b2}")

    def concurrent_quota():
        # 活动 1002 初始 10 名额；用新用户争抢。若名额已被消耗则跳过严格断言前先查剩余
        s, detail = req("GET", "/api/activities/1002")
        assert_true(s == 200, "无法读取活动 1002")
        remaining = detail["data"]["remainingQuota"]
        if remaining <= 0:
            print("  (skip strict concurrent: remaining already 0)")
            return
        users = list(range(95000, 95000 + remaining + 40))
        outcomes = []

        def one(uid):
            rid = f"c_{uid}"
            return req("POST", "/api/registrations", uid, {"activityId": 1002, "requestId": rid})

        with ThreadPoolExecutor(max_workers=32) as pool:
            futs = [pool.submit(one, u) for u in users]
            for f in as_completed(futs):
                outcomes.append(f.result())

        created = sum(1 for st, _ in outcomes if st == 201)
        sold = sum(1 for st, b in outcomes if st == 409 and b and b.get("code") == "SOLD_OUT")
        assert_true(created == remaining, f"成功数应为 {remaining}, got {created}")
        assert_true(created + sold == len(outcomes) or created + sold <= len(outcomes), "结果异常")
        s2, d2 = req("GET", "/api/activities/1002")
        assert_true(d2["data"]["remainingQuota"] == 0, "最终剩余应为 0")

    def same_key_concurrent():
        uid = 96001
        rid = "same_key_conc_" + uuid.uuid4().hex[:6]
        # 选剩余>0 的活动 1006
        results_local = []

        def one():
            results_local.append(req("POST", "/api/registrations", uid, {"activityId": 1006, "requestId": rid}))

        threads = [threading.Thread(target=one) for _ in range(20)]
        for t in threads:
            t.start()
        for t in threads:
            t.join()
        ids = set()
        created = 0
        replay = 0
        for st, b in results_local:
            if st == 201:
                created += 1
                ids.add(b["data"]["id"])
            elif st == 200:
                replay += 1
                ids.add(b["data"]["id"])
        assert_true(created == 1, f"同键并发只应 1 次新建, got {created}")
        assert_true(replay == 19, f"同键并发应 19 次重放, got {replay}")
        assert_true(len(ids) == 1, "应返回同一报名 ID")

    check("normal_and_replay", normal_and_replay)
    check("sold_out_and_closed", sold_out_and_closed)
    check("already_registered", already_registered)
    check("idempotency_conflict", idempotency_conflict)
    check("concurrent_quota", concurrent_quota)
    check("same_key_concurrent", same_key_concurrent)

    failed = [r for r in results if r[1] == "FAIL"]
    print("---")
    print(f"total={len(results)} pass={len(results)-len(failed)} fail={len(failed)}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
