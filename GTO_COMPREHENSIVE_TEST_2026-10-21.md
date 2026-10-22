# 2026-10-21 全面测试记录（gto.4）

此报告记录清除 NeoECO **之前**的 LT `2.1.2-gto.4` / Core `2.0.3-gto.4`。清除后的 `gto.5` 结果另见 [NeoECO 清除记录](GTO_NEOECO_REMOVAL_2026-10-21.md)。原始失败与补测分别保留，不能将补测成功当作整体没有问题。

## 制品与证据

隔离测试目录：`D:/codex-builds/ae2lt-gto-realtest-20261019/comprehensive-20261021`。包含冻结制品、源码备份、完整单测 XML、服务端日志、客户端日志和截图。测试只运行在隔离 GTO 实例与独立测试世界。

| 正式 JAR | SHA-256 |
| --- | --- |
| `ae2lt-forge-1.20.1-2.1.2-gto.4.jar` | `8e1ef03d0a6f318b7cef3284ff5710cffd7ec6bea2fa8703085e2f85e0637136` |
| `thunderbolt-reborn-forge-1.20.1-2.0.3-gto.4.jar` | `ebcc77289d015b037d69637bbb699623d426127900dc6a7a6b1c907a791ed250` |

## 完整单元与静态检查

| 检查 | 总数 | 通过 | 跳过 | 失败 |
| --- | ---: | ---: | ---: | ---: |
| LT check（强制重跑） | 1,534 | 1,529 | 5 | 0 |
| Core check（含三个冷启动测试任务） | 1,556 | 1,543 | 13 | 0 |

Core 发行包 refmap / Mixin / JarJar 校验通过。LT 与配套 QA 变体的生产文件一致；测试期间正式配套制品哈希未改变。

静态审计检查 5,328 处外部成员引用、515 个目标类。37 处未解析引用全部属于未安装的 AdvancedAE / AppFlux / AddonLib，已安装依赖没有缺失成员。1,883 个具体 LT 类没有遗漏抽象方法。1,435 个 JSON 解析通过；中英文各 1,541 个键，缺失键与格式占位符差异均为零。历史同值重复键 `jei.ae2lt.crystal_catalyzer.matrix_note` 保留。

ForgeGradle 首次构建停在证书环境探测，线程证据见 `lt-startup-threads.txt`。通过命令行 `-Dnet.minecraftforge.gradle.check.certs=false` 重跑，未修改生产构建设置。此环境停顿与单元测试失败分开记录。

## 完整服务端首轮

每组使用新世界，并检查 CPU / STORAGE / PLANNER 三个启动探针。十组共 137 项，136 通过、1 失败。所有服务端正常退出且没有超时。

| 组 | 执行 | 通过 | 失败 | 条件未执行 |
| --- | ---: | ---: | ---: | --- |
| base | 11 | 11 | 0 | — |
| exact | 19 | 19 | 0 | 1 项 NeoECO |
| dispatch | 19 | 18 | 1 | — |
| interfaces | 6 | 6 | 0 | — |
| batch | 4 | 4 | 0 | 1 项 NeoECO |
| io | 40 | 40 | 0 | — |
| infinite | 1 | 1 | 0 | — |
| content-recipes（正式 LT） | 13 | 13 | 0 | — |
| machineWithIF | 20 | 20 | 0 | 2 项可选反应工厂 |
| machineWithoutIF | 4 | 4 | 0 | 16 项 IF 不注册，2 项可选反应工厂 |

无限存储存档另行解压核对：9 个库存 UUID，77,831 个物理条目、45,063 个唯一键，总数量 49,159,114,688。包括 45,056 个 NBT 变体及 7 个混合键，逐项验证 1,024 字节 JavaRandom 载荷。证据为 `infinite-disk-check.json` 与 `infinite-disk-check.log`。

## 未解决的替代材料唤醒失败

`nativeSubstitutionWaitsForAlternativeMaterialsWithoutPeriodicAttempts` 使用真实可替代木板配方：材料耗尽后停车 40 tick，随后返回白桦木板 1 个、橡木板 1 个，预期派发一次合成，实际没有派发。

- 首轮完整 dispatch：18 通过、1 失败。
- 第二轮完整 dispatch 新世界：19 项通过。
- 第三轮独立用例新世界：再次失败。

第三轮诊断：`dispatched=0; before=1/1; after=1/1; queries=1; busy=true`。库存已收到材料，但任务仍保持 busy。现有证据尚不足以认定根因；本轮没有修改相关生产调度算法。证据见 `followup-dispatch-results.json`、`runtime-dispatch-r3/server-dispatch.log`。该问题必须继续保留，不能用第二轮通过覆盖失败结果。

## 图形客户端与补测

最终客户端 smoke、原生/天枢大数终端、提供器高级界面、正常主菜单进入后的 EMI、多方块预览与自由视角探针均通过。客户端 smoke 检查了 93 个启用 Mixin、73 个目标、8 个条件跳过，并通过配方转移关键断言。

提供器界面实际点击工具栏、进入高级页、切换开关，验证网络发往服务端、GuiSync 回传及关闭后重新打开保留设置。截图为 `runtime/provider-gui-provider-adaptation-on.png` 和 `runtime/provider-gui-provider-adaptation-off.png`。

原生/天枢终端验证 `10^100 + 12345` 的库存显示、完整提示及扣除 37 后缓存刷新。初次探针因静态字段提前创建 AEItemKey 导致 GTO 材料注册未完成而崩溃；改为延迟获取键后新运行通过。这是测试夹具修正，未改变生产 JAR。证据为 `followup-stock-results.json` 及 `runtime-stock-r2`。

QuickPlay + EMI 本轮补测通过，退出码 0，86.2 秒；历史重复配方注册故障本轮未复现，尚不能据此认定已永久修复。证据为 `followup-quickplay-results.json` 和 `runtime-quickplay`。

自由视角探针为验证运动临时将三个 HV 每 tick 消耗设为零，FE 保持正常；退出后配置恢复原始字节。未安装 Tweakerge。

本轮 gto.4 正式包联机补测在用户要求清除 NeoECO 时尚未执行。联机验证转到新 gto.5 配套版本，不能将历史联机结果计入本轮通过项。

## 范围

以上测试没有穷举整合包所有配方、所有可选附属及长期服务器运行。当前确定存在一项可重复但并非每次发生的替代材料唤醒失败。NeoECO 专用适配已按后续用户要求从 gto.5 清除，本报告仅保存其清除前的历史证据。
