# GTOCore 文档对应优化

参考 GTOCore `0.6.0` 的固定提交
[`b244589d2407362cdc4d60f1fb270db61b0c11b4`](https://github.com/GregTech-Odyssey/GTOCore-Main/tree/b244589d2407362cdc4d60f1fb270db61b0c11b4)。
以下改动属于 LT Forge 1.20.1 GTO；完整移植验证状态见
[移植记录](GTO_UPSTREAM_PORT_2026-10-31.md)。

## 已落地的热点调整

| 文档依据 | LT 调整 | 保持的语义 |
|---|---|---|
| `CODING_GUIDELINES.md` §17：冷路径发现并缓存反射访问器 | `MultiblockLinkReadiness` 用 `ClassValue` 缓存 cluster、bounds、updateSubType 方法及缺失结果；重复检查不再扫描父类或设置访问权限 | 每次仍读取当前实体／cluster 状态，调用失败后仍可恢复，不缓存世界或实体实例 |
| §3、§5、§13：引用容器、快速迭代、预分配 | `NetworkDeviceIndex` 改用 `Reference2ObjectOpenHashMap`；遍历只取 key/value，不让可复用 entry 逃逸；快照列表与 owner 集合按节点数预分配 | 节点按引用区分、拓扑回调维护、20 tick 刷新、UUID 及只读快照边界保留 |
| §1、§2：原始类型集合 | 无线派发上下文按 int 频率索引，使用 `Int2ObjectOpenHashMap` 的原始类型访问 | 派发账本、顺序和每 tick 预算不变 |

这些调整消除重复发现、装箱或容器增长路径；未将源码层面的变化换算成未经测量的 TPS、GC 或百分比性能结论。回归检查应覆盖继承／缺失／暂时失败的访问器、相等但不同引用的节点、真实无线派发和管理页面。

## 依赖与兼容边界

依照 [`docs/forked-dependencies.md`](https://github.com/GregTech-Odyssey/GTOCore-Main/blob/b244589d2407362cdc4d60f1fb270db61b0c11b4/docs/forked-dependencies.md)，兼容以实际 fork 制品为准，不能沿用普通版 GTM／AE2 的方法签名。

AppFlux `1.20-1.3.7-forge` 的旧 GTM 入口引用
`capability.forge.GTCapability`；实际 GTM `26.10.70`／`26.10.80` 使用
`GTCapabilityHelper.getEnergyContainer(BlockEntity, Direction)`，能量接受接口还增加了 source 参数。新接口的数量及回执单位是 EU，不能按旧接口的安培数乘电压。本轮在 LT 内限定 GTO／AppFlux 组合桥接，保留原有 FluxNetworks 优先级、配置开关、实际扣款与拒收余量处理；不改外部依赖 JAR。

这项能量桥已在 Dev23 实跑。Dev25 加装同一 AppFlux `1.3.7` 的真实启动另因
其 `FluxKey` 覆盖 AE2 `.40` 的 final `hashCode()` 而失败，未进入 FX 行为；
不能把能量桥回归通过视作该外部键 ABI 已支持。

GTO AE2 两个支持版本的身份键继承关系不同；继续使用 GTO 自带的键规范化，普通版额外 key reuse Mixin 不启用。Core 自定义 AEKey 子类须同时通过 `.31` 与 `.40` 的真实链接检查。

Dev23 的 `IClientRepo.getAllEntries()` 返回 `Set`，Dev25 改为 `Collection`，两种返回类型对应不同 JVM 描述符。`ClientRepoAccess` 在冷路径绑定真实 getter，更新代理同时提供两个描述符；保留底层集合的实时视图，不复制库存快照、不丢条目。维护终端、模式终端与 JEI 转移的旧描述符调用一并替换。实际两版 `Repo` 均持有 final map，完整网络更新清空并重填原 map，因此缓存的集合视图仍能观察完整更新。

NeoECO `20.5.0-gto.1`／`20.5.2-gto.1` 的实际闪电转换回调位于 `LightningBolt` 中，而非已删除的事件类。LT 通过 Mixin 归属、完整签名与唯一写入点约束包装该回调，运行时检查保留在独立 helper，避免早期转换器加载游戏类。原随机判定、周围转换、闪电与资源所有权不变；不修改依赖 JAR。

两版 EMI 均通过 JEMI 提供 JEI 浏览接口。JEI 服务识别别名编辑器时，按模组存在检查惰性调用公开 `EmiApi.getHandledScreen()`，保留菜单和玩家身份校验。验证明确区分实际 JEMI 浏览、JEI 按钮／服务和 EMI 按钮路由。

## 验证状态

- 与这些调整直接相关的 JUnit 为 **15／15 通过**：GTM 回执与拒收 8 项、多方块访问器 4 项、节点引用身份 1 项、库存双 ABI 与实时更新 2 项。两个 ABI 分别在独立加载器中实际调用，检查增删、迭代器移除、完整网络更新和代理转发。
- r9 新版 NeoECO、Mixin 门控与 JEI 定向回归 **34／34 通过、0 跳过**；真实 NeoECO 双版字节码检查要求其余随机／分支／副作用指令保持不变，并拒绝归属或 ABI 歧义。r9 已构建和校验，真实专项结果单独记录。
- Core `2.0.4-gto.2` 配套 r6 在 Dev23 实际管理组 **10／10**、能源 API **29／29** 通过，管理组包含真实 GTM 能源仓和 FE 守恒。
- r7 正式发行字节码已经确认同时包含 `()Ljava/util/Set;` 和 `()Ljava/util/Collection;`；2351 个生产类与测试副本逐字节一致，QA 与正式包隔离检查通过。
- Dev25 同一 r7 包的真实 EMI 图形复测已完成有线／无线两条上传别名流程，来源选择、配方转移和目标选择各 2 次，4 张新截图齐全；原维护终端 ABI 崩溃消失。Dev23 r10 双人 TCP 图形与确认页通过，实际收齐 8 个唯一状态回执；独立 JEI、Useless 和其余双人矩阵仍未通过或未执行。
- r9 Dev23 无线快组 **19／19**、准入慢组 **3／3** 通过；真实磁盘卸载保留原 1600 tick 和三个完整循环。冻结字节码复核了唯一成功出口；该轮 INFO 诊断未显示，具体事件 tick 无法恢复，见 [无线 QA](GTO_WIRELESS_CHUNK_QA_2026-10-31.md)。
- 当前仍有 Core NeoECO 契约、Dev25 规划器键及原生批量映射 ABI 链接失败。独立 JEI API 经 JEMI 浏览也在熔炼分类处停止。定向回归通过不代表整项移植验收通过。精确制品、失败和未测范围以 [移植验证](GTO_UPSTREAM_PORT_2026-10-31.md) 为准。

## 适用范围

- 持久账本仍由世界的 `SavedData` 管理，网格索引属于 `IGridService`；不为了更换容器引入第二套存档。`ClassValue` 只保存类元数据，管理统计保留弱节点引用与停服清理。
- 网络同步保持独立的紧凑编码；本次热点调整不改协议字段或存档格式，也不额外引入 DataSyncLib／FastCollection 依赖。
- GTOCore §38 明确固定数据包且不支持 `/reload`。实际 Dev25 的 GTOLib loot Mixin 会复用静态 loot 缓存，即使创建新的 `LootDataManager` 也不重新解析。保留真实 reload 失败记录并区分原生 loot 与 LT 产物缓存，不用 LT 全局补丁改写整合包约定。
- 实际 GTO 分开处理区块不可访问与排队卸载，且按 80 server tick 放行卸载队列。快组只验证不可访问期间不投递、不加载并能恢复；真正的 Unload 事件、旧能力失效、新 BE／能力和三次磁盘恢复由原 1600 tick 慢组条件等待。未扩大预算或手动使能力失效，见 [QA 边界说明](GTO_WIRELESS_CHUNK_QA_2026-10-31.md)。
- [`docs/emi-integration.md`](https://github.com/GregTech-Odyssey/GTOCore-Main/blob/b244589d2407362cdc4d60f1fb270db61b0c11b4/docs/emi-integration.md) 中的原生 EMI 接口须以实际 fork 核验。EMI 重载尚未完成时不启动依赖配方索引的 QA 操作，原测试总预算与页面关系断言保持不变。
- 外部文档针对当前 GTOCore 的 Java 或 API 建议不改变 LT 已支持的 Java 21、Dev23／Dev25 版本范围。没有机械替换空集合工厂、移除异常清理或取消 NBT 隔离；这些操作不必然优化本项目，并可能破坏实际边界。
