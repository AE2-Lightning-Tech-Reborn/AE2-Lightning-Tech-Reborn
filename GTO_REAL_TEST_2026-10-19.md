# GTO 实机测试记录 · 2026-10-19

在本机运行完整 Forge/GTO 专用服及真实图形客户端，使用隔离目录和一次性世界。服务端 34 项游戏测试通过；原生/天枢终端精确库存同步、EMI 多方块预览和正式包独立客户端联机通过。本轮发现并修复两个 LT 兼容问题。

## 环境与制品

| 组件 | 实测版本 |
| --- | --- |
| GTO | 0.6.0-dev15，公开快照 `3650c7d330ade1b744ade0bc303fe5111eac98fa` |
| Minecraft / Java / Forge | 1.20.1 / 21 / 47.4.20 |
| GTOCore / 内嵌 AE2-GTO | 26.10.5 / 15.2610.8 |
| Thunderbolt / LT | 2.0.3-gto.1 / 2.1.2-gto.1 |
| EMI / JEI | 1.1.24+1.20.1+forge / 15.59.0.212 |

测试根目录：`D:\codex-builds\ae2lt-gto-realtest-20261019`。依据快照的 155 个模组清单逐项核对下载哈希，并保留快照直接提供的 3 个 JAR。GTOCore 原件 SHA-256 为 `8e6260ac596091ca876506e70b7715249b7198ddbd5d833059c3609ac751e465`，没有修改 GTOCore、GTOLib 或其内嵌 AE2/GTCEu。复制快照配置；两项可选 AE 明暗 UI 资源包未解析下载。

专用服只监听 `127.0.0.1:25585`；客户端使用测试离线身份 `GtoQA`。没有读取启动器账号，也没有加载或修改用户原实例的存档。服务端各测试组使用独立世界。

正式制品：`build/libs/ae2lt-forge-1.20.1-2.1.2-gto.1.jar`。其 2106 个生产类与附加测试夹具的 LT 包逐字节一致，本轮 `gtoQa` 类没有进入正式制品。QA 中间 JAR 不能用于正式游戏。

正式 LT SHA-256：`be2c648c498fd00407c854aeabe42953dd8f859ac124ec471e3c2093179a1c93`。配套 TB SHA-256：`b25024154b4237477f7674f581404138b4ea11637d1c903300e7494b77066f54`。

## 结果

| 项目 | 结果 | 覆盖范围 |
| --- | --- | --- |
| Gradle `check` | 1489 项；1484 通过、5 跳过，0 失败/错误 | 移植回归及现有单元测试 |
| 专用服 `base` | 11/11 通过 | GTO 实体 CPU 超载投料、暂停、存档、变体回料、普通任务回退、拆除库存/缓冲完整回收及重复调用防复制；时轮状态与共享种子闭环 |
| 专用服 `interfaces` | 6/6 通过 | 本地稀疏补货公平性、64 普通/256 快速无线工厂补货、实际采矿工厂处理 |
| 专用服 `batch` | 4/4 通过；另 1 项跳过 | 全局批量核算、部分接收、退款、拒绝及不确定所有权 |
| 专用服 `exact` | 13/13 通过；另 1 项跳过 | `10^100` 库存/订单、已形成 CPU/矩阵、混合普通供应器、真实等待队列与回料、存档恢复、取消退款、有限 long 批次、原生报告包 |
| 图形客户端基础检查 | 通过 | 进入集成世界、真实渲染、终端精确数量格式作用域、界面 Mixin |
| 原生/天枢终端库存 | 通过 | `10^100 + 12345` 库存、真实槽位提示完整数值、减至 37 后清理缓存、恢复精确值 |
| GTO 默认 EMI 多方块预览 | 通过 | 注册 LT 原生分类、打开真实 EMI 页面、绘制/命中尺寸一致、旋转、缩放、外壳开关 |
| 正式包独立客户端联机 | 通过 | 两端均使用正式 LT JAR；独立 JVM 登录、加入世界、真实渲染、精确格式作用域和界面 Mixin 检查；双方正常退出 |

四组专用服还分别通过 CPU、STORAGE、PLANNER 启动检查。34 项不含这些重复启动检查，也不含未安装 NeoECO 的两项跳过用例；接口性能基准默认未启用。

EMI 修复后日志明确出现 `Initializing plugin from ae2lt`、`Loading plugin from ae2lt`，并成功构建 100088 条配方。EMI 探针结果为：

```text
PASS: native GTO EMI registry, recipe screen, rendered widget bounds, rotation, zoom and shell toggle.
```

## 本轮修复

1. **天枢编码终端打开越界。** GTO 删除了实体空白样板槽，菜单原先直接访问 `getSlots(BLANK_PATTERN).get(0)`。现在仅遍历并禁用实际存在的槽位。真实客户端打开界面并完成大数库存探针验证。
2. **LT 的 EMI/JEI 插件未被 GTO 发现。** GTO 用 `GTOApi.EMI_PLUGIN_EVENT` 和 `JEI_PLUGIN_EVENT` 替换常规注解发现。新增仅客户端加载的注册桥，通过其公开事件注册 LT 插件，并保持可选查看器的类加载隔离。真实 EMI 分类与交互探针验证通过；JEI 在默认整合包中由 JEMI 接管。

## 排查记录与边界

- 早期 EMI 运行遇到 GTOCore `AssemblyLine.lambda$init$74` 的配方加载 NPE，紧邻缺失 `gtceu:normal_laser_pipe` 日志。移除 TB/LT/QA 的原包对照没有复现此异常，成功加载 98834 条配方；装入修复后的 LT 也成功加载。不能据此断言该异常来自上游或已经被彻底修复，保留失败日志供后续追踪。
- 早期 QuickPlay 联机曾超时或报 `ConcurrentModificationException`。最终移除 QuickPlay，等待主菜单稳定 20 秒后连接，成功加入专用服并通过客户端断言；没有修改生产网络代码来掩盖该启动时序问题，也没有验证所有快速启动方式。
- 原包和带 LT 的日志均出现缺少两个 Ad Astra globe ID、GuideME 模型及其他资源警告；这些警告没有阻止上述已通过的测试。
- EMI 探针成功后自动退出，后台 EMI 搜索/ModernUI 在窗口销毁阶段仍有第三方线程异常。本记录不把这些退出日志解释为正常游戏中已修复的问题。
- 曾用 `-Dthunderbolt.mixinAudit=true` 启动专用服，调试审计因提前加载客户端类失败；正常运行不带此调试参数。没有将该审计记为通过。
- 移除 EMI 的原生 JEI 配置未成功完成启动，没有原生 JEI 独立环境通过结论。默认 GTO 的 EMI/JEMI 已实测。
- 不包含未安装的可选附属、所有整合包配方的功能穷举、长时间多人压力或用户旧存档迁移测试。

## 复现与证据

构建采用 Java 21：

```powershell
.\gradlew.bat check gtoQaJar gtoQaLtJar -PgtoQa `
  '-Pthunderbolt_local_repository=../Thunderbolt-Core-GTO/repo'
```

在隔离的 GTO 实例部署 TB、`gto-compat-qa.jar` 与 `ae2lt-gto-qa-instrumented.jar`，后者替代正式 LT。用 `-Dae2lt.gtoQaSuite=base|interfaces|batch|exact` 分组启动专用服，分别使用新世界。客户端使用 `-Dae2lt.gtoQaSuite=none`，库存探针加 `-Dae2lt.bigTerminalProbe=true`，EMI 探针加 `-Dae2lt.emiMultiblockProbe=true`。测试会放置方块、强制区块加载并为无人专用服打开 GTO 的玩家加入调度条件，只能在测试世界运行。

正式包联机检查改用正式 LT JAR 和 QA 启动模组，两端均设置 `-Dae2lt.gtoQaSuite=none`，客户端加 `-Dae2lt.gtoQaConnect=127.0.0.1:25585`，从主菜单等待后自动连接；隔离客户端首次运行的无障碍引导已完成。

测试根目录下保留：

- `manifest-verified.json`：整合包清单与哈希核对。
- `qa-build14-check.log`：最终生产代码的检查及打包；`qa-build15.log`：客户端连接探针更新。
- `server-base-final-console.log`、`server-interfaces-final-console.log`、`server-batch-final-console.log`、`server-exact-active-console.log`：四组最终通过日志。早期夹具失败日志不作为最终结果。
- `client-smoke3-console.log`、`client-stock2-console.log`、`client-emi-fixed-console.log`：客户端通过日志。
- `server-multiplayer-console.log`、`client-multiplayer-console.log`：正式包最终联机通过日志；服务端记录 `GtoQA joined the game`，客户端记录 `GTO_CLIENT_QA PASS`。
- `client/big-terminal-result.txt`、`client/emi-multiblock-result.txt`：客户端断言结果。
- `client/screenshots/exact-terminal-0.png`、`exact-terminal-1.png`、`emi-multiblock.png`：已查看的真实游戏截图。
- `client-baseline-console.log`：移除 TB/LT/QA 的配方库对照；`client-emi-console.log`：早期配方加载失败。
- `artifact-hashes-final.json`：制品 SHA-256 与生产类一致性结果。
- `real-test-results.json`：从最终日志和 JUnit XML 提取、核对的结果及用例名称。

旧的 `server-matrix-results.json` 包含夹具开发时的失败，不能替代这里列出的最终日志。
