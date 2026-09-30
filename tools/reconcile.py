#!/usr/bin/env python3
"""只读对账：活动名额与报名一致性检查。不修改任何业务数据。"""
from __future__ import annotations

import argparse
import json
import sys
from collections import defaultdict
from typing import Any

try:
    import pymysql
except ImportError:
    print("请先安装: pip install pymysql", file=sys.stderr)
    sys.exit(2)


def connect(args: argparse.Namespace):
    return pymysql.connect(
        host=args.host,
        port=args.port,
        user=args.user,
        password=args.password,
        database=args.database,
        charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor,
        autocommit=True,
        # 对账视图：以一次快照读取为主；并发写入期可能短暂不一致，需重跑
    )


def reconcile(conn) -> dict[str, Any]:
    with conn.cursor() as cur:
        cur.execute(
            "SELECT id, title, status, total_quota, remaining_quota FROM activities ORDER BY id"
        )
        activities = cur.fetchall()
        cur.execute(
            "SELECT id, activity_id, user_id, request_id, status FROM registrations"
        )
        regs = cur.fetchall()

    by_activity: dict[int, list] = defaultdict(list)
    user_activity = set()
    user_request = set()
    dup_user_activity = []
    dup_user_request = []

    for r in regs:
        by_activity[int(r["activity_id"])].append(r)
        ua = (int(r["user_id"]), int(r["activity_id"]))
        ur = (int(r["user_id"]), r["request_id"])
        if ua in user_activity:
            dup_user_activity.append({"userId": ua[0], "activityId": ua[1], "registrationId": r["id"]})
        else:
            user_activity.add(ua)
        if ur in user_request:
            dup_user_request.append({"userId": ur[0], "requestId": ur[1], "registrationId": r["id"]})
        else:
            user_request.add(ur)

    activity_reports = []
    inconsistent = False
    for a in activities:
        aid = int(a["id"])
        success = len(by_activity.get(aid, []))
        total = int(a["total_quota"])
        remaining = int(a["remaining_quota"])
        ok = (remaining >= 0) and (total == remaining + success)
        if not ok:
            inconsistent = True
        activity_reports.append(
            {
                "activityId": aid,
                "title": a["title"],
                "status": a["status"],
                "totalQuota": total,
                "remainingQuota": remaining,
                "successCount": success,
                "consistent": ok,
                "expectedRemaining": total - success,
            }
        )

    if dup_user_activity or dup_user_request:
        inconsistent = True

    return {
        "consistent": not inconsistent,
        "activities": activity_reports,
        "duplicateUserActivity": dup_user_activity,
        "duplicateUserRequest": dup_user_request,
        "registrationCount": len(regs),
        "note": "并发写入期间可能短暂不一致；请安全重跑。本工具只读，不会自动修复。",
    }


def main() -> int:
    p = argparse.ArgumentParser(description="活动报名只读对账 CLI")
    p.add_argument("--host", default="127.0.0.1")
    p.add_argument("--port", type=int, default=3306)
    p.add_argument("--user", default="root")
    p.add_argument("--password", default="root")
    p.add_argument("--database", default="activity_reg")
    args = p.parse_args()

    conn = connect(args)
    try:
        report = reconcile(conn)
    finally:
        conn.close()

    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report["consistent"] else 1


if __name__ == "__main__":
    sys.exit(main())
