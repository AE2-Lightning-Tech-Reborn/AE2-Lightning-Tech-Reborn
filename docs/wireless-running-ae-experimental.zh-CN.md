# 已运行网络 FE→AE 实验分支与验证

当前管理界面已拆为 ME 网络设备与供电管理，统一采用 AE2 原版控件；详细统计语义、权限与验证见 [无线能源实现说明](wireless-energy-implementation.zh-CN.md)。这项界面改动不启用 FE→AE 实验。

## 2026-10-15：高级控制器的 FE 管理页

本节是继续开发后的当前状态；后文保留 10 月 13 日的验收快照。功能分支为 `feature/wireless-running-ae-experimental-20261013`，目标仓库为 `AE2-Lightning-Tech-Reborn/AE2-Lightning-Tech-Reborn`。

- 高级无线超载控制器首页新增“无线供能”入口，每页显示 3 个目标的位置、当前状态、上次访问投递 FE；悬停显示维度、接入面、累计投递与距上次访问 tick。显示的是每次访问量，不冒充 FE/t。
- 页面每 20 个客户端 tick 请求一次，只在已打开且仍有效的高级控制器菜单中响应。服务端读取控制器当前频率，重新检查访问权限；每菜单每 tick 最多一次查询、一次断开，自动刷新不会占掉操作额度。
- 断开按端点 UUID 精确匹配连接代次。目标可在远处或未加载区块；该操作不获取目标方块、不加载区块、不探测 FE，也不修改托管或不确定收据账户。仅绑定者或该频率管理者可断开；同类型重绑产生新 ID，旧页面请求不能删除新连接。手动断开会保留自动准入排除记录。
- 删除末页唯一目标后自动回到有效页。权限被撤销时列表清空并显示无权限，不保留旧的可操作行。

当前验证（Minecraft 1.21.1 / NeoForge 21.1.220 / 官方 Thunderbolt 2.0.3-beta2）：

- `build` 成功；JUnit 共 1497 项，1493 通过、4 项既有跳过。
- 无线能源 GameTest **36/36**：新增菜单有效性、查询/编辑限流、越权、远程断开、旧 UUID 重放与无探测检查，其余记账、拓扑、调度和自动准入回归全部通过。
- 真实集成客户端通过原生控制器右键打开、3+1 分页、单目标断开、末页回退和服务端数量核对；截图已实际查看，无文字与按钮重叠。客户端测试机为注册 FE capability 的桶，截图刻意没有 FE 来源，显示“FE 不足”。
- 测试入口：`scripts/wireless-energy-test.init.gradle`；客户端在其后叠加 `scripts/wireless-energy-client-test.init.gradle`，任务 `runWirelessEnergyClient`，仅使用独立 `WirelessEnergyQA` 存档。可通过 `-PwirelessEnergyClientDirectory=...` 更换测试目录。
- 本机日志与截图保留在忽略目录 `build/verification/`、`build/wireless-energy-client-final/`，不进入提交；验证仍使用已校验的官方 TB jar 本地依赖注入。

仍未实现：供能历史曲线/平均 FE/t、孤立与异常账户恢复、手动解除 `historyIncomplete`、非原生 FE 自动观察桥。FE→AE 运行补能继续默认关闭，冷启动禁令保持；本轮未重新做长期负载或断电恢复测试。


基于已验收的手动能源与自动准入切片 `106b052a33c450ce4c387b9fd8e28c02a27bc507`，本次继续工作只在 `feature/wireless-running-ae-experimental-20261013`。原 `feature/wireless-energy-ledger-20261013` 保持不动；未修改大型工厂主工作区或猪咪工作区，无推送、PR、合并或发布。

2026-10-13，用户明确授权功能分支上的改动，只要不影响其他工作。此前自动审批曾因新增 FE→AE 范围拒绝测试；补充该授权后，同一测试正常获准执行，不再是当前阻塞。服务端实验配置 `wirelessControllerEnergy.runningNetworkFeFallback` 默认 **false**，本次不决定最终 UI、玩法默认值或新增冷启动机制。

## 实现边界

- 仅高级无线过载控制器所在、已运行的同一网格参与。AE2 原 AE 提取先执行，再在同次 `extractAEPower` 返回前补实际缺额。`hasPower` 与 `publicHasPower` 必须都已为真；库存有 FE 不等于允许冷启动。
- 使用构建实际声明的 AE2 19.2.17、Applied Flux Curse 文件 7429565。实际单位 API 为 `PowerUnit.FE.convertTo(PowerUnit.AE, 1)`；原扣电倍率转换成原始 AE 一次，返回调用方时还原一次。
- 不把 FE 虚报成 AE 电池容量，不反向将普通 AE 或控制器被动 AE 当成 FE。直接使用当前网格 `NetworkStorage` 的 FE 提取路径，线程级和网格级重入闸门阻止存储回调再次借用本网或另一网格的 FE。
- 模拟仅预览库存、余量和可用预算，不扣款、创建节点账户或改变持久账本。正式支付只依赖实际 FE 回执；所有外部回调完成后才扣减并返回 AE。
- 整数 FE 仍在节点 UUID 的 `EnergyEscrow` 中；已转换原始 AE 用十进制字符串独立持久化。小数余额不因兑换率变化重新定价，不能反向发成 FE。节点分合迁移账户，不复制网格余额；移除后仍保留原节点归属。
- 外部 FE 提取前记录未确认的提取上界。没有回执的上界不是已拥有 FE，不可花费或退款；异常/重启保留后停止自动补能，避免重复提取。退款拒收保留整数余额，退款回调异常进入原有 uncertain 隔离；不因此扣掉尚未返回的 AE。
- 正常世界保存/退出是持久化保证的边界；不承诺跨 Minecraft 与第三方存储文件的断电事务。实验存档包含新增 AE 与未确认提取字段，不应用旧切片重新保存同一实验世界。

## 维护优先与拓扑预算

独立审查在 `5c446e33` 真实复现了两个 P2：同 tick 拆除空驱动器触发拓扑暂停，或者 32 次小请求耗尽访问机会，都会让库存仍足的运行网络在维护扣电时停电，并被“不冷启动”规则挡住恢复。修复不能删除冷启动限制或增加一份不计入总量的预算。

当前修复为普通请求与本功能外供预留按当前维护需求、倍率和 FE 换算率向上取整的 FE 额度，并在共享访问次数内留一次维护机会。默认 32 次中普通操作最多使用 31 次；硬 FE 限额不变。此预留是工程策略，无法预测同 tick 之后突然增加的维护负载，也不保证超出硬预算的网络持续运行；实际储能或预算不足仍按 AE2 缺电规则处理。

当 tick 预算作为瞬态对象，经 AE2 `saveNodeData`/`addNode` 的节点迁移交接。拆分共享已花预算；合并按对象归并、去重累计；下一 tick 才分别获得新额度。交接 UUID 不表示能源归属，不从磁盘恢复。外供仍在拓扑变化当 tick 暂停；运行补能允许调用前已完成的拓扑变化，但调用中任何节点变化、来源移除或兑换率变化都会中止支付并保留已提取能源。

## 已保存检查点与测试证据

- `69ee0765`：初始实现；14 项账本单测通过，默认关闭路径的既有 35 项 GameTest 通过。
- `5c446e33`：13 项新 GameTest 通过，包括真实 Applied Flux 电盘、普通 AE 优先、同调用维护、真实耗尽后不自启、模拟不变、倍率/小数、共享预算、同网/跨网重入、部分回执、拒退、异常提取、来源移除和真实分合网账户。
- `46b63e62`：修正重启测试的独立正常 AE 启动夹具；最终两个 JVM 各 1/1 通过。恢复 `0.375 AE`、原电盘 `49999 FE` 和未确认提取上界 `17 FE`，没有重放提取。
- 上述产品代码完整 `build`：1492 项 JUnit，1488 通过、0 失败、4 跳过。4 项为未配置实包依赖的既有 EAEP 契约测试。Provider 回归 11/11 通过。
- 维护/拓扑修复：19 项账本和预算单测首轮通过；扩展 19 项 GameTest 首轮 18 通过、1 个同 tick 合网夹具失败。新放置 BE 下一 tick 才初始化，已改用现有真实节点即时建连来验证同 tick 合网；改正夹具后 19/19 通过；同 tick 合网经真实节点建连验证。

初次重启 verify 曾失败于测试夹具未提供普通 AE 启动能量，符合产品“不冷启动”约束。修正夹具后，以新世界重新运行完整 prepare→verify 已通过。失败日志保留，不计为产品成功结果。

最终产品代码检查点 `0030d5059dcc8af509ca5ec8f3e616b2945b8569`：完整构建 **1497 项，1493 通过、0 失败、4 既有跳过**；原无线 **35/35**、FE→AE **19/19**、Provider **11/11**、重启 prepare/verify **各 1/1**。全部使用官方发布依赖并正常保存退出。19 项包括超大有限请求、退款抛异常、访问与 FE 维护保留，以及同 tick 真实节点分合预算。随后独立正控在同一确切产品 SHA 与官方 jar 下 **3/3 通过**，两个 P2 均关闭。无扰动与拆空驱动器后维护都保持运行；访问场景前 31 次普通请求成功、第 32 次返回 0，维护后仍运行，总存储调用 32 次、总 FE 使用 2037 ≤ 65536。静态复核覆盖维护限定调用、revision、同 tick 预算归并去重和零 grant 不退款；此结论不等于默认启用或扩展实验保证。

本地实验 jar 已单独保存为 `build/verification/fe-to-ae/ae2lt-0030d505-experimental.jar`，SHA-256 `de46e018d490d7e351ec4584f0f67fb7fd157258a6884559337904f5718cea31`，确认不含 GameTest 类。它是默认关闭功能的本地产物，不是发布包；通用 `build/libs/ae2lt-2.1.2-beta3.jar` 路径已被该实验构建更新，不能拿文件名判断它属于旧验收切片。

原始证据、JUnit XML、隔离世界和产物散列位于本机 `build/verification/fe-to-ae/`；不提交原始日志。独立审查的 `5c446e33` 负控和 `0030d505` 正控日志均已复制至该目录的 `independent-review/`。所有 GameTest 确认实际注册数后正常退出，未运行性能基准。

## 依赖与复现

Java 21、Gradle 8.8。正式声明的 Thunderbolt Maven 坐标在本机启用仓库中仍无法直接解析，未修改产品依赖声明。本地测试注入校验过的官方 beta2 发布 jar：SHA-256 `9e45573895742144c1895a67ce08fe7a3967a50b413095edee68ccb3dbfba6a9`，不是之前的未发布性能候选。

```sh
./gradlew -I build/verification/local-released-dependency.init.gradle build --offline --no-daemon --no-configuration-cache --max-workers=2
./gradlew -I build/verification/local-released-dependency.init.gradle -I scripts/wireless-energy-test.init.gradle -I scripts/wireless-ae-fallback-test.init.gradle runGameTestServer --offline --no-daemon --no-configuration-cache --max-workers=2
# prepare 与 verify 用两个 JVM，保留中间同一个隔离世界
./gradlew -I build/verification/local-released-dependency.init.gradle -I scripts/wireless-energy-test.init.gradle -I scripts/wireless-ae-fallback-test.init.gradle -PwirelessAeRestartPhase=prepare runGameTestServer --offline --no-daemon --no-configuration-cache --max-workers=2
./gradlew -I build/verification/local-released-dependency.init.gradle -I scripts/wireless-energy-test.init.gradle -I scripts/wireless-ae-fallback-test.init.gradle -PwirelessAeRestartPhase=verify runGameTestServer --offline --no-daemon --no-configuration-cache --max-workers=2
```

## 未完成边界

维护修复后的最终完整回归和当前两项独立正控已完成，已知本轮审查 P2 均关闭。UI 消耗/链接管理、手动解除 historyIncomplete、孤立/异常账户人工恢复、非原生 FE 自动观察桥未实现。未做客户端手工验收、长期服务器 soak、任意断电/半写盘故障注入或全可选模组组合验证。默认关闭、不冷启动和只在本地功能分支保存的边界继续有效。


## 本地交接入口

仓库：`/Users/user/Documents/Codex/2026-10-12/task-6/wireless-energy`。远端归属保持 `AE2-Lightning-Tech-Reborn/AE2-Lightning-Tech-Reborn`；共同基线为 `5269dcabdd52acd95471ab373587d0ea4758ebcb`。

- 已验收、可供后续选择整合的手动/自动 FE 分支：`feature/wireless-energy-ledger-20261013`，固定 `106b052a33c450ce4c387b9fd8e28c02a27bc507`。
- 本轮默认关闭的 FE→AE 实验分支：`feature/wireless-running-ae-experimental-20261013`，产品代码固定 `0030d5059dcc8af509ca5ec8f3e616b2945b8569`，后续仅保存验证文档。
- 本地 bundle：`/Users/user/Documents/Codex/2026-10-12/task-6/wireless-energy-checkpoints-20261013.bundle`。包含两个分支从共同基线起的提交，接收仓库须已拥有上述共同基线；不是远端推送或合并。

```sh
git fetch /Users/user/Documents/Codex/2026-10-12/task-6/wireless-energy feature/wireless-energy-ledger-20261013:refs/remotes/wireless-local/accepted
git fetch /Users/user/Documents/Codex/2026-10-12/task-6/wireless-energy feature/wireless-running-ae-experimental-20261013:refs/remotes/wireless-local/experimental
# 或使用离线 bundle；先验证，再按需 fetch 指定分支
git bundle verify /Users/user/Documents/Codex/2026-10-12/task-6/wireless-energy-checkpoints-20261013.bundle
```

收到全局重置完成与收尾指令后没有新增功能或实验；仅完成已开始的维护安全修复、回归、当前独立正控与本地交付记录。
