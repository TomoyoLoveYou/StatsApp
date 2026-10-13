# -*- coding: utf-8 -*-
"""独立核算统计数字，与 app 界面/日志对比验证（M4 统计验收）"""
import json
from collections import defaultdict
from datetime import datetime, timedelta

TZ = timedelta(hours=8)  # 手机时区 GMT+8

with open(r"C:/Users/Administrator/Documents/StatsApp/debug-utils/dump.json", encoding="utf-8") as f:
    dump = json.load(f)

records = dump["records"]
checkins = dump["checkIns"]

def to_dt(ms):
    return datetime.utcfromtimestamp(ms / 1000 + TZ.total_seconds())

def to_date(ms):
    return to_dt(ms).date()

today = to_date(int(datetime.now().timestamp() * 1000))
# 用手机时区的"今天"：本机也是同时区，直接取
today = datetime.now().date()

# --- 积分（流水净额）---
pts_day = defaultdict(int)
for r in records:
    pts_day[to_date(r["createdAt"])] += r["value"]

sum_where = lambda pred: sum(v for d, v in pts_day.items() if pred(d))

# 周界（周一）
monday_this = today - timedelta(days=today.weekday())
monday_prev = monday_this - timedelta(days=7)
monday_last2 = monday_prev - timedelta(days=7)

print("今天是:", today, "（周%d）" % (today.weekday() + 1))
print()
print("=== 积分（对比 app 各视图）===")
print("今日 =", sum_where(lambda d: d == today), " （app 日视图: 30）")
print("本周(周一至今) =", sum_where(lambda d: monday_this <= d <= today), " （app 周视图: 170）")
print("上周 =", sum_where(lambda d: monday_prev <= d < monday_this), " （app 周视图上期: 225）")
print("本月(10月) =", sum_where(lambda d: d.year == 2026 and d.month == 10), " （app 月视图: 275）")
print("上月(9月) =", sum_where(lambda d: d.year == 2026 and d.month == 9), " （app 月视图上期: 905）")
print("今年 =", sum_where(lambda d: d.year == 2026), " （app 年视图: 2405）")
print()

# --- 打卡次数 ---
ci_day = defaultdict(int)
for c in checkins:
    if not c["revoked"]:
        ci_day[to_date(c["completedAt"])] += 1

sum_ci = lambda pred: sum(v for d, v in ci_day.items() if pred(d))
print("=== 打卡次数 ===")
print("今日 =", sum_ci(lambda d: d == today), " （app: 2）")
print("本周 =", sum_ci(lambda d: monday_this <= d <= today), " （app: 11）")
print("本月 =", sum_ci(lambda d: d.year == 2026 and d.month == 10), " （app: 17）")
print("今年 =", sum_ci(lambda d: d.year == 2026), " （app: 155）")
print()

# --- 连续打卡 ---
active = set(ci_day.keys())
cursor = today if today in active else today - timedelta(days=1)
streak = 0
while cursor in active:
    streak += 1
    cursor -= timedelta(days=1)
print("连续打卡 =", streak, " （app: 14）")
print()

# --- 近 7 天趋势（对比柱状图 15/35/45/20/30/10/30）---
print("=== 近 7 天趋势 ===")
for i in range(6, -1, -1):
    d = today - timedelta(days=i)
    print(f"{d.month}/{d.day} = {pts_day.get(d, 0)}", end="  ")
print()
print()

# --- 近 7 周趋势（对比 175/265/175/230/160/225/170）---
print("=== 近 7 周趋势 ===")
for i in range(6, -1, -1):
    mon = monday_this - timedelta(days=7 * i)
    sun = mon + timedelta(days=6)
    v = sum_where(lambda d, mon=mon, sun=sun: mon <= d <= sun)
    print(f"W{mon.isocalendar()[1]} = {v}", end="  ")
print()
print()

# --- 分类构成（10 月）---
cat_names = {c["id"]: c["name"] for c in dump["categories"]}
cat_sum = defaultdict(int)
for r in records:
    d = to_date(r["createdAt"])
    if d.year == 2026 and d.month == 10:
        cat_sum[r["categoryId"]] += r["value"]
print("=== 10 月分类构成（app 显示: 学习67% 生活33%）===")
for cid, v in sorted(cat_sum.items(), key=lambda x: -x[1]):
    print(f"{cat_names.get(cid, '?')} = {v}")

print()
print("=== 记录总数 ===")
print("checkIns =", len(checkins), " records =", len(records), " 总积分 =", sum(r['value'] for r in records))
