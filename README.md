# 统计 Stats

> 自用的安卓效率应用：**任务便签 + 打卡积分 + 统计图表**。

用打卡记录每天坚持的事，按分类累计积分，再用图表回看自己的节奏。

## 功能

### 任务
- 六种周期类型：每日 / 每周 / 每月 / 每年 / **单次**（完成一次即结束）/ **常驻**（不限次数，随时记录）
- 四级提醒强度：静默 / 通知 / 横幅 / **全屏闹钟**（锁屏也能响铃提醒）
- **日界线**（0–4 点可设）：凌晨该点之前完成的打卡记入前一天，熬夜党友好
- 软删除：删除任务不丢历史，全部打卡与积分保留，可在「设置 → 历史任务」找回

### 打卡与积分
- 分类着色：每个类别一种颜色，贯穿统计图表与桌面图标
- 流水记账：打卡记正分、撤销补负分（不删记录），按当时分值反向记账
- 单次任务完成后自动从列表消失，可随时撤销完成

### 统计
- 四档周期：日 / 周 / 月 / 年，左右滑动切换（相邻两页并排、滚动进度同步）
- 积分构成环形图 · 积分趋势柱状图 · 任务排行 Top 5 · 15 周活跃度热力图
- 周期选择器按范围分别提供日历 / 周列表 / 月份网格 / 年份网格
- 底部导航再次点击「统计」→ 回到今天（带过渡动画）

### 个性化
- **中英双语**：中文 / 英文 / 跟随本机（非中英语言自动回退英文）
- **动态应用图标**：图标颜色跟随「昨日积分最高的分类」，13 款预置图标动态切换
- 浅色 / 深色 / 跟随系统

### 数据
- 导出备份 / 快速恢复 / 从文件导入；导入前自动在应用内留存数据快照

## 技术栈

| 用途 | 技术 |
| --- | --- |
| UI | Jetpack Compose + Material 3 |
| 状态 | ViewModel + StateFlow + 协程 |
| 存储 | Room + SharedPreferences |
| 依赖注入 | Hilt |
| 提醒 | AlarmManager（精确闹钟）+ 全屏 Intent + 通知渠道 |
| 构建 | AGP 8.13 · Kotlin 2.3 · compileSdk 36 · minSdk 31 |

## 构建

需要 Android SDK（compileSdk 36）与 JDK 17+。

1. 在项目根目录创建 `local.properties`，指向本机 SDK：

   ```properties
   sdk.dir=/path/to/Android/sdk
   ```

2. 构建 debug 包：

   ```bash
   ./gradlew :app:assembleDebug
   ```

产物位于 `app/build/outputs/apk/debug/app-debug.apk`。首次构建需下载依赖，耗时较长。

## 项目结构

```
app/src/main/java/com/stats/app/
├── data/            数据层：Room 实体与 DAO、仓库、备份、日期工具
├── reminder/        提醒链路：闹钟调度、通知、全屏闹钟、补账机制
├── launcher/        动态图标：activity-alias 切换
├── ui/              Compose 界面：任务 / 统计 / 设置 / 分类 / 历史
└── util/            日志、应用语言

app/src/main/res/
├── values/                 默认资源（英文）
├── values-zh-rCN/          中文资源
└── mipmap-anydpi-v26/      13 款自适应图标（默认色 + 分类调色板 12 色）
```

## 开发工具（debug-utils/）

| 脚本 | 用途 |
| --- | --- |
| `gen_icons.py` | 生成 13 款自适应图标与预览图（改图标样式后重跑即可覆盖资源） |
| `ui_find.py` | 解析 uiautomator dump，按文本查找控件中心坐标（自动化测试用） |

应用内调试广播（需应用在前台）：

```bash
adb shell am broadcast -a com.stats.app.DEBUG -p com.stats.app --es cmd dump_status
```

可用 `cmd`：`dump_status` · `dump_db` · `dump_reminders` · `set_time` · `mk_auto` · `set_action` · `catch_up` · `test_notify` · `dump_channels` · `open_alarm`

## 说明

个人自用项目，按现状提供，未做通用性适配与多机型测试（开发机为 iQOO Neo 10 / Android 16）。
