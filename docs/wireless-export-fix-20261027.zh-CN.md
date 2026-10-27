已修复无线 FAST 导出的两处批处理接线遗漏，导入和导出的原性能预算均通过。发行包版本为 **AE2LT 2.1.2-beta3**，配套 **Thunderbolt Core GTL 2.0.3-beta4**。NeoECO 20.5.1 集成保留。

`OverloadedInterfaceBlockEntity.runExport` 接收到 `WirelessExportStockBatch` 后，原先仍向单次导出传入原始 ME 存储。在 1024 个目标、27 种物品持续补货时，每个目标每个键都触发网络抽取、盘摘要与 NBT 更新。现将批量库存接入精确识别的原版木桶、有界物品导出路径；通用能力、流体及无限请求继续按原有接口处理。预取余量和扣料后拒收的实际回执归入接口的持久缓冲，能量仅按实际交付量计费。

同一路径还在每个目标创建新的 `EnergyAccess`，没有启用已有的预付批次，因此每键都重复查询服务并扣电。现使用接口已有的持久能量托管对象，每个目标开始批次、每键检查预算、结束时退回剩余能量，并复用接口的 grid/overflow 回调。满电网络拒收的能量退款仍由接口保管；同时补齐 `loadTag` 对 `ae2ltTransferEnergy` 的读取，旧存档缺字段或非法数值清零。新增测试验证预取余量、目标突变拒收、精确扣电、退款拒收、离线恢复和真实接口 NBT 往返。

前一轮遗漏的木桶通知 Mixin 注册与通知分配优化一并保留。首次写入仍同步标脏，比较器通知在最外层批次关闭后发送；三项真实木桶回归在本轮 I/O 套件中执行。

Core 默认构件从普通 Forge 坐标切换为 `com.moakiee.thunderbolt:thunderbolt-reborn-forge-1.20.1-gtl:2.0.3-beta4`，最低运行版本也改为 beta4，使默认 Radium NBT 修复成为运行要求。中英文 README 同步更新。旧普通版坐标下已观察到不同 JAR/映射哈希；独立 GTL 坐标可隔离两种构件的覆盖风险。本轮四个压力 JVM、I/O GameTest JVM 和两个正式客户端 JVM 均未复现 `NbtCopyOptions` / `EjectRegistrationSavedData` 缺类，但没有捕获历史失败瞬间的文件/加载器状态，不能把旧缺类异常的直接根因认定为已经证明。

| 本轮验证 | 结果 |
| --- | --- |
| 定向库存/能量回归 | 23/23，包含新增四项，不与全量结果叠加计数 |
| 全量 JUnit，Forge 47.1.47、Java 17、项目默认 GTL beta4 依赖 | 1692/1692，零失败、零跳过 |
| adaptiveBatchStress | 6/6 |
| check / build / 发行包审计 | PASS，Java 17 字节码、Mixin/refmap、可选依赖/配方、许可证及开发夹具排除 |
| I/O GameTest，Forge 47.4.10、NeoECO/LDLib/EAEP | 78 条引擎成功，含新能量 NBT 往返和三项木桶通知；沿用的四个条件专项未启用，不计行为覆盖 |
| 1024×27 导入/导出 control 与 stress | 4 个独立 JVM 各完成 1 条真实 GameTest、1200 份有效采样；两项原预算 PASS |
| 92 模组正式 GTL 客户端，Forge 47.4.16、Java 17.0.20、默认 Radium NBT | 冷启动、真实合成、取消守恒、long 菜单及 200 tick 空闲 PASS |
| 同存档独立 JVM 重启 | 正常保存并退出后恢复 99 个原木、无复活 busy 作业，继续合成 4 木板后剩余 98 个原木，PASS |

压力测试仍为 3 GiB 游戏堆、单 worker、独立世界、200 tick 预热和 1200 tick 采样，包含 eligibility，关闭额外 diagnostics；没有调整场景、消费负载、超时或预算。开发压力未安装 Radium，默认 Radium 覆盖来自正式整包客户端。EAEP 在开发验证中为 1.5.5，正式整包中为 1.5.4.1 和 GTL 附加包 1.0.3；Core 侧单独测试的 EAEP 1.6.3 不计入本轮整包范围。

| 采样 | mean MSPT | P99 MSPT | I/O P99 ms | >50 ms tick | 正确性 |
| --- | ---: | ---: | ---: | ---: | --- |
| import-control | 1.709 | 4.353 | 0.764 | 0/1200 | PASS |
| import-stress | 5.315 | 9.364 | 2.619 | 0/1200 | PASS |
| export-control | 1.742 | 11.190 | 8.264 | 0/1200 | PASS |
| export-stress | 8.520 | 22.300 | 10.733 | 1/1200 | PASS |

同机原 beta4 失败轮次的导出 mean/P99/I/O P99 为 25.616/64.622/41.204 ms，7 项预算超限；只接通批量扣料的中间轮次为 15.529/27.368/15.293 ms，仍有 2 项超限。补齐能量批次后的最终完整配对通过全部原预算。这些是各一轮配对观测，失败和中间结果全部保留，不代表统计意义的版本提速或长期稳定性；`capacityTPS` 是 mean MSPT 推导值，不是墙钟 TPS。JFR 支持网络抽取/盘摘要及能量计费热路径的诊断，后台负载贡献未单独量化。

导出数量账本和全部稳态事件通过，导入实际生产、抽取、入网总量相等且缓冲/剩余为零。正式客户端的其余 91 个模组（含 Core、探针）逐一核对哈希，与上一轮默认 Radium 实例相同；仅替换 AE2LT 发行包。两次 debug 日志明确记录 Radium `NbtCompoundMixin` 与 Core `CompoundTagCopyMixin` 同时作用于 `CompoundTag`，配置没有关闭 NBT。

| 固定制品 | SHA256 |
| --- | --- |
| ae2lt-forge-1.20.1-gtl-2.1.2-beta3.jar | `43ec2dc192e25a09cf3220b456c54e736038a34f5d366da1c7a33f07dc724d95` |
| thunderbolt-forge-1.20.1-2.0.3-beta4.jar | `374e5f5035601b6191d1f9dac117b5e6c676f1afa81846595d8d7d77c896cb75` |

完整日志、失败记录、JFR、输入清单、XML 和结构化结果保留在被忽略的 `build/validation-export-fix-20261027/`。两次定向回归准备错误分别为 PowerShell 拆分 Forge 版本参数、旧夹具同时引入 beta3/beta4；均在实际测试前被检查拦截，原记录保留，其后的有效执行独立记录。I/O 引擎实际完成 78 项并正常保存退出后，复制的 init 将游戏目录改为新位置，但共享后处理仍读取旧目录，导致 Gradle 返回 1。已修正夹具，使用实际目录对同一轮结果及源码摘要独立复核通过；原失败返回码和更正记录均保留，没有重跑并叠加 78 项，也没有称原 Gradle 任务成功。NeoECO 成型机器作业跨 JVM 恢复、独立专服多客户端竞争、长期资源增长和全部第三方版本组合仍未覆盖。正式日志中 Useless 大整数 API 缺席时回退普通派发，该可选 API 也不计为已覆盖。
