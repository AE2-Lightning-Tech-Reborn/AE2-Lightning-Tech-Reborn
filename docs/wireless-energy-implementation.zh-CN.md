# 无线能源手动端点、自动准入与验收记录

## 2026-10-15：设备无线配置子菜单

ME 网络设备页选中具体设备后，可以点击“无线配置”打开该设备原本的配置界面。这里的无线配置是远程操作原界面；优先级、配置槽、升级槽和设备自身的次级菜单仍走原有菜单与数据包。没有原生可打开菜单的设备不启用此按钮。

- 复用 AE2 `ISubMenuHost` 与 `MenuOpener` 契约，覆盖方块和面板设备；无线控制器/接收器使用已有频率界面。原生界面工具栏增加返回设备列表的按钮。列表返回时恢复搜索词、类型筛选、排序、滚动位置和选中设备。
- 支持当前 ME 网格内的远处、跨维度设备，要求目标区块已经在服务端加载，不替设备加载区块。客户端不需要已加载目标区块：只接收有上限的服务端方块实体更新快照，用临时、未装入客户端世界的宿主构建原生界面；真实修改仍落在服务端原设备上。
- 每次操作核对原控制器、打开时的频率、玩家身份与权限、控制器距离、目标节点所在网格、方块实体身份及面板接入面。撤权、离开控制器、设备拆除/替换或脱网后，旧菜单失效；客户端序列化的定位信息不能在服务端生成远程操作权限。
- 跨维度频率选择从菜单绑定的真实设备解析目标，避免误改玩家所在维度同坐标的设备。

验证入口：`scripts/wireless-energy-test.init.gradle` 包含远程菜单 GameTest；客户端在其后应用 `scripts/remote-device-client-test.init.gradle`，任务 `runRemoteDeviceClient`，独立存档 `RemoteDeviceQA`，可用 `-PremoteDeviceClientDirectory=...` 隔离运行目录。验证使用下节相同的 Minecraft、AE2 和依赖版本。

本次验证：

- 完整 `build` 成功；JUnit **1502** 项，**1498** 通过、4 项既有跳过。产品 jar 不包含 GameTest 或客户端探针。
- 无线能源、管理与远程配置 GameTest **48/48**：新增六项验证涵盖原生配置修改和返回状态、撤权后立即拒绝原生配置包、旧行不能打开同坐标替代设备、伪造定位信息与离开控制器、面板精确接入面，以及跨维度频率修改不会触及同坐标诱饵设备。跨维度夹具等待两侧原生节点自然就绪后连接。
- 真实客户端通过：打开客户端视距外的原生接口，进入优先级子菜单并将服务端优先级改为 321；打开下界同坐标接口并改为 654，确认主世界仍为 321；面板接口、LT 供应器和无线控制器子菜单均能打开、返回。返回列表恢复原搜索词、类型和选中设备。
- 完整构建日志为 `build/verification/remote-device-build.log`；GameTest 日志为 `build/verification/remote-device-gametest-final4.log`；客户端日志为 `build/verification/remote-device-client-2.log`，原始截图在 `build/remote-device-client-2/`。日志、世界和截图留在本地忽略目录。

## 2026-10-15：ME 设备管理与供电管理

当前实现位于 Reborn 的 `feature/wireless-running-ae-experimental-20261013`，从无线控制器的频率首页分别进入“ME 网络设备”和“供电管理”，也可以在两个页面间切换。

- **ME 网络设备**：查询控制器当前实际 ME 网格的方块与面板设备，按物品类型汇总数量，点击类型展开具体设备。支持名称、设备类型/ID、维度、坐标和多词组合搜索；本地化物品名与常用维度名在客户端转换为搜索标识，结果仍由服务端生成。单项可复制完整维度坐标，或关闭界面后高亮 15 秒；面板标记对应面，跨维度只提示位置，不传送或加载区块。
- **供电管理**：显示此 ME 网络的 LT 输出、原版感应卡输出、Applied Flux 方块/面板访问点主动输出与被动抽取，以及当前频率的无线端点。被动抽取接口没有目标地址，因此明确显示来源位置与“目标未知”。没有发生收据的已安装输出设备仍以空闲状态列出。
- **五档速率**：`1t / 1s / 5s / 30s / 1min` 对应 `1 / 20 / 100 / 600 / 1200` 个完整服务端 tick，统一显示 `FE/t`。窗口内实际交付量除以完整窗口长度，空闲 tick 和尚未积满窗口的部分算 0；当前未结束 tick 不参与，避免菜单执行顺序影响 1t 数值。平均值按游戏 tick 计算，不是墙钟秒。供电量排序跟随所选窗口，另有名称/ID、类型、维度、距离排序；合计遵循当前筛选条件。
- **AE2 风格**：直接使用 AE2 的生成式底板、调色板、物品标签、搜索框、按钮、滚动条和世界轮廓渲染。EAE/Flux 仅作定位交互与供电列表组织参考，不引入独立皮肤或运行依赖。
- **性能与边界**：节点索引随 AE2 add/remove 回调维护，没有每 tick 全网枚举。设备快照最多每 20 tick 更新，页面每 20 客户端 tick 刷新，服务端每菜单每 2 tick 最多响应一次查询；每包最多 5 行。能源收据按实际来源节点保留，拆分/合并跟随节点，不复制历史；查询只使用已加载 chunk。历史只在内存中，重启从零开始，过期路径释放，最多 8192 条；满额时显式提示记录不全。它不是 FE 账本，也不改变库存、退款、保留量或 FE 输入规则。
- **操作权限**：菜单绑定真实控制器和打开时的频率，检查距离、方块身份和频率使用权限。解绑仅适用于当前返回页中、当前权限允许的无线端点 UUID；同一 tick 的查询和编辑额度分离。供电页中的原版设备只提供数据查看，ME 设备页的远程配置见上节。新一代端点不会被旧 ID 删除，关闭菜单、换频和撤权不能继续操作。

复现入口仍为 `scripts/wireless-energy-test.init.gradle`；新增 `-PwirelessEnergyGameTestDirectory=...` 可隔离世界。客户端在其后应用 `scripts/wireless-energy-client-test.init.gradle`，使用独立 `WirelessEnergyQA` 存档与 `runWirelessEnergyClient`。旧切片结果保留在下文，不追溯为本次结果。

本次验证（Minecraft 1.21.1 / NeoForge 21.1.220 / AE2 19.2.17 / 仓库声明的 Applied Flux 7429565 / 官方 Thunderbolt 2.0.3-beta2）：

- 完整 `build` 成功；JUnit 共 **1502** 项，**1498** 通过、4 项既有 EAEP 实包测试跳过。五个窗口的测试覆盖完整 tick、空闲、环形回绕、长时间间隔和大额收据。
- 无线能源与管理 GameTest **42/42**：包含原 36 项回归，以及原版感应卡、方块访问点、面板访问点、被动抽取不统计模拟、无线输出不重复统计、真实节点拆分/合并归属六项验证。供应器能源输出/回流过滤 **11/11** 通过。
- 真实集成客户端完整通过：控制器入口、按类型聚合、中文名称搜索、展开与复制坐标、世界内遮挡高亮、窗口切换与供电排序、感应卡/访问点筛选、原生滚动条、精确解绑、两页切换及返回。已查看游戏原始截图，确认目标轮廓和两页布局。
- 日志位于忽略目录 `build/verification/network-management-*.log`，最终截图位于 `build/network-management-client-final/`。产品 jar 已确认不含 GameTest 或客户端探针；不将日志、测试世界和本地依赖加入提交。本轮未做长期负载基准。

仍未实现供电曲线、孤立/异常账本恢复、解除自动准入的 `historyIncomplete`、非原生 FE 自动观察桥。FE→AE 运行补能仍默认关闭，不支持冷启动。

## 2026-10-15：基础三行 FE 页（历史快照 cdf11c36）

本节保留第一版管理页的验收记录；该三行页面及其协议现已被上文两页界面替代。功能分支为 `feature/wireless-running-ae-experimental-20261013`，目标仓库为 `AE2-Lightning-Tech-Reborn/AE2-Lightning-Tech-Reborn`。

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


本文件记录保持在 `106b052a` 的已验收切片。FE→AE 的另行授权后续工作见 [实验分支说明](wireless-running-ae-experimental.zh-CN.md)，不将后续测试结果追溯计入本切片。

目标：Minecraft 1.21.1 / NeoForge。独立功能分支 `feature/wireless-energy-ledger-20261013` 基线为核对后的远端 main `5269dcabdd52acd95471ab373587d0ea4758ebcb`。用户主工作区本地 main `1bda5091` 及未提交改动未带入、未修改。机制依据 docs 分支 `0e20b7caece0ddc79d0f49603ca1847aec85cdf8` 的 `无线过载控制器能源/设计方案.md`。仅在本地提交，无推送、PR、合并或发布。

## 用户澄清与本切片边界

2026-10-13：用户确认原机制设计基本不变，待讨论主要是 **UI 查看消耗、管理链接**。不因 UI 未定搁置机制，也不在此分支定稿最终 UI。手动连接不做准入探测；自动 FE 准入已单独实现，每物理实例最多 100 FE，等待目标实际机器 ticker 执行后才判定。自动机制提交 `abdcde6efbca571dab833c68fd6cc1e07173c142`，容量边界修复提交 `5ff00345958c2184fabf34d10a690dfcf33bfa4b`。未接入 FE→AE，未来也只支持运行网络，不支持冷启动。

## 安全归属

- 已提取未送出的 FE 进入世界 SavedData 账本；AE 节点保存账户 UUID。网格分合通过 AE2 saveNodeData/addNode 移交 UUID，绝不把整网余额复制到每个控制器。
- 余量属于提取节点的托管账户。拆分时随该节点归属，不按网络数量均分；合网不会合并/复制账户余额。与真实 FE 库存始终分开。
- 退款拒绝保留余额，下一次合法输送优先消费已有余额。节点正常卸载/重启恢复账户；永久移除仍保留世界账本记录，不能让同坐标新放置方块自动继承。
- 对异常接收回调无法证明对方是否已收电，金额隔离为 uncertain，禁止自动重发。管理员后续恢复界面需展示该状态，不能默默退款。
- 正常世界保存/退出是恢复边界。Minecraft 的多个存档文件并非事务数据库，不声称应对任意断电/半写盘下的跨模组恰好一次交付。

## 端点与调度

服务端记录频率、维度/坐标、输入面、绑定人、实例 UUID、方块和 BE 类型；不写第三方机器 NBT，不创建 AE 节点。通过 LevelChunk 实际移除 BE 时撤销旧实例，区别卸载和同类型替换。每次有限轮询重新取能力，不持有跨卸载失效的能力。手动绑定仅查看能力是否支持输入，不调用模拟或真实接收；正常运行才发送 FE。

共享网格输出预算、目标预算和全局访问预算均有界。数值为可配置工程初值，非最终平衡承诺；默认每网每 tick 65536 FE、每目标 4096 FE、每网 32 次访问、全局 128 次访问，每频率 256 端点，全球 8192 端点。留在 ME 库存的 10000 FE 仅约束此出口，不是 FE→AE 代理或对其他模组出口的保电承诺。预算在回调前扣除，拒收也占此次尝试额度。发生分合网的当个 tick 暂停外供，避免通过拓扑变更获得新一份预算。

调度以“尚未获得网格预算的访问年龄”为优先级，预算拒绝不推进年龄；每 tick 访问过的端点在本轮结束才重新入队，防止重复发放。17、64、128、129、257 个端点的独立算法检查以及实际 128 端点场景已通过。端点与控制器轮询使用 `getChunkNow` 及已知 BE 的能力查询，不通过读取位置续上 UNKNOWN 区块票据。

## 自动 FE 准入

`WirelessEnergyAdmissions` 使用独立 SavedData 保存每个坐标的物理实例、绑定人、频率、输入面、状态、首次提供量与实际接收量。玩家放置时选择一个原生 FE 输入面，每个实例只有一次机会；不轮流试所有面，不补足部分接收，不因重复事件或多个控制器重复探测。历史记录最多 8192 条，达到上限保守拒绝新增；只在实际移除/替换 BE 时清除对应实例，普通卸载不清除。若满额时无法保存新的手动实例排除记录，则持久化 `historyIncomplete` 并停止所有后续新增自动准入；释放名额和重启都不会自动清除此标记。已有供能和手动绑定仍可用。这是有意的保守上限策略，未来管理入口必须先解决缺失实例身份才能解除，不能简单自动重置。

1. QUEUED 先等待确实成功调用了该 BE 的 `BlockEntityTicker.tick`。仅已加载但不 ticking 的区块不会收到测试电量。
2. 读取原生 FE 存量 E0；负数、超容量和整型饱和读数等不可观察情况直接拒绝。测试最多 100 FE，仍受共享网格/目标预算、保留库存、权限与防回流约束。
3. 在真实接收回调前保存 WAITING 和提供量，能源先进入账本隔离；回调明确返回后结算实际接收，拒退余量仍归提取账户。
4. 至少再观察到同一实例的一次真实机器 ticker，且在服务端本轮 BE tick 阶段之后读取 E1。只有 E1 > E0 才建立持续供能端点。未增加、瞬间销毁、不明读数、能力替换、权限失效或 100 tick 超时均拒绝。
5. 卸载、观察中断或重启把未完成状态视为 UNVERIFIED，不继续输入、不恢复比较、不自动重试。正常保存/退出的边界与账本一致，不承诺任意断电下的跨文件事务。

手动绑定不读取 E0/E1，不模拟或实际接收测试能源，并保存 MANUAL 实例记录；解绑后重复放置事件仍不会再探测。真正拆掉重放才是新的物理实例。手动连接已验证的普通机器可绕过不适用的自动观察规则。假玩家放置不进入自动准入事件路径。非 FE 后端没有被假定为可观察；`TargetAccess.storedEnergyFe` 缺省返回未知，原生 FE 适配器提供有效读数。

当前判定仅证明观察窗口内总存量增加，不能在第三方机器内部区分其他并行能源来源，也不猜测延迟更长的耗电语义。每 tick 自动队列最多处理 32 项；没有客户端批量扫描或持续探测。

## FE→AE 后续接入边界

本切片没有实现 Applied Flux 库存补 AE，不把现有保留 FE 配置称为 AE 保电。已检查 AE2 19.2.17 的 `EnergyService`：`onServerEndTick` 通过 `extractAEPower(..., PowerMultiplier.CONFIG)` 完成本轮闲置消耗，内部先乘倍率、提取后再除倍率。因此未来“同一次扣能补缺口”应在该调用返回前接入短缺补偿，限制为调用前已经运行的网络，并处理只转换一次的倍率、重入、分合网归属和小数 AE 余量。

现有整数 FE 账本不等于小数 AE 账本；在上述规则和回归明确前，不添加跨模组扣能 Mixin，也不以每 tick 提前充电冒充同调用兜底。禁止冷启动的设计约束保持不变。

## UI 待讨论（机制接口保持可替换）

1. 消耗展示：区分实际送达 FE、尝试预算、账本待退 FE、隔离 FE；显示瞬时速率还是窗口平均/累计、放在哪个页面。`inspect` 返回独立端点状态、最近访问 tick 和该次实际交付量，不把预算当消耗。
2. 链接管理：设备列表如何呈现 AE 和 FE 两项连接、绑定面、离线/权限失效/故障状态；单条/批量解除按钮与筛选。底层 `setBound(..., desiredState)` 为幂等接口，未来 UI 不需要模拟卡片 toggle。
3. 移除控制器后的托管账户如何供有权限玩家查看和认领；不默认为同坐标新方块继承，也不做清零。异常回执需人工核实，不承诺一键安全恢复。

## 验证记录

2026-10-13，`5ff00345` 的完整构建和 35 项测试、`abdcde6e` 的双 JVM 重启及 Provider 11 项回归，均使用官方 Thunderbolt `2.0.3-beta2` 发布 jar 的结果。测试源码在 `src/test` 和 `src/jdb`；GameTest 及夹具不打入产品 jar。

| 验证 | 结果 | 覆盖与限制 |
| --- | --- | --- |
| Gradle `build` | 通过 | 编译、打包、全量 JUnit；1484 项中 1480 通过、0 失败、4 跳过。4 项是未配置 `ae2lt.eaepTestJar` 的既有 EAEP 实包契约测试。 |
| `EnergyEscrowTest` | 6/6 通过 | 包含于 JUnit 总数：拒退/部分接收、异常回执隔离、溢出、NBT、固定种子 10000 次守恒、共享预算。 |
| 手动与账本 GameTest | 18/18 通过 | 不探测绑定、重复/伪造绑定、权限撤销恢复、真实 FE、替换撤销、能力失效、来源脱网、拒退/回调重入/异常、节点分合账户。新增倍率余数及 17/64/128 调度回归。 |
| 自动准入 GameTest | 17/17 通过 | 真实 ticker、立即/延迟销毁、部分接收不补足、未知/饱和、能力替换、卸载、权限、同类型重放、已加载但不 ticking、手动覆盖/解绑防重试、拒退余量、WAITING 存档快照、三轮完整磁盘卸载/重载。容量满额后手动实例排除、释放容量与存档往返。与上行合计 35/35。 |
| 重启 GameTest | 两个独立 JVM 各 1/1 通过 | 同一世界 prepare 正常保存退出，再 verify；恢复 7331 可用、17 隔离和真实控制器 991 FE 账户 UUID。自动准入仅输入 100 FE，退出中断为 UNVERIFIED，下一进程保留该记录和机器存量，不重新输入、不建立端点。原始 WAITING 的加载另由快照测试覆盖。 |
| Provider 能源输出/回流过滤 GameTest | 11/11 通过 | 既有邻接/无线输出、移除停止、FE 缓冲和物品/流体过滤回归。 |
| `git diff --check` | 通过 | 无空白错误。 |

所有 GameTest 核对实际注册数和 `All N required tests passed` 后正常退出保存；未开展性能基准。独立审查在确切 `6468e92c951f83a64160b839616bae80603f9139` 上通过了原 18 项、生产方法预算/守恒探针和曾失败的 128 端点真实反例（尾端获得 12288 FE），确认关闭两个 P2。该次独立结论使用候选依赖，不自动扩展为自动准入切片的审查结论。

自动容量反例随后在官方发布 jar 下完成独立对照：`abdcde6e` 发送 100 FE、调用接收 1 次，测试按预期失败；`5ff00345` 完全相同用例发送 0 FE、调用 0 次，1/1 通过。使用独立目录、原生产手动绑定/解绑、另一实例移除和重复放置路径；没有修改本功能工作区。随后同一 `5ff00345` 在独立目录完整 35/35 GameTest 通过，包含容量标志存档往返和三轮真实磁盘卸载；容量 P2 关闭，未发现新的阻塞问题。

### 已修正的实质失败

- 旧代码拒退后清缓存会丢失 FE。现在所有提取先进入持久账户，退款失败保留；异常回执隔离而不重发。
- 独立审查发现倍率 2、账本仅余 1 FE 时反复优先访问余量，导致插盘有电却永久停供。`a278076a` 修正只在够支付一次倍率时走该早返回路径，余量仍保留。
- 第一调度修复虽通过 17 目标但仍在 128 目标整轮遍历时使尾部饥饿；独立 GameTest 明确失败。`6468e92c` 改为预算拒绝保留访问年龄，独立原反例及相关规模均通过。
- 自动准入初版在历史满 8192 条时无法保存手动实例记录，随后释放容量可能让重复事件触发探测。独立审查指出后添加持久 `historyIncomplete` 闸门；此时保守停用新自动准入，避免丢失排除记录。
- 完整磁盘卸载测试最初无法卸载，查明普通位置查询续上临时区块票据。改为非加载查询后，三轮真实磁盘保存→区块管理器卸载→重载通过；不再仅以卸载回调测试代替真实卸载。
- 早期 GameTest 命名空间错误注册 0 项，不能计通过。早期精简 GameTest runtime 用于全量 JUnit 造成可选集成发现失败；现全量 build 使用完整 runtime。旧世界重复跑测试会残留测试端点；常规 GameTest 使用新的隔离世界，重启配对则必须复用同一个世界。

### 发布依赖与复现

Java 21、Gradle 8.8。未注入脚本的离线构建失败于未缓存 `com.moakiee.thunderbolt:thunderbolt-reborn:2.0.3-beta2`；在线尝试也无法从本机当前启用仓库解析该坐标（认证 GitHub Packages 仓库未启用）。这项环境问题没有被隐藏，也未更改产品依赖声明。

已从 [Thunderbolt 官方 beta2 发布](https://github.com/AE2-Lightning-Tech-Reborn/Thunderbolt-Core-Reborn/releases/tag/2.0.3-beta2) 取得发布资产 625134905，大小 5152057 字节。SHA-256 `9e45573895742144c1895a67ce08fe7a3967a50b413095edee68ccb3dbfba6a9` 与发布 API 的 digest 相同。存于 `build/local-dependencies/released/thunderbolt-2.0.3-beta2.jar`，通过本地 `local-released-dependency.init.gradle` 注入。上表全部最终自测使用该官方发布包，不依赖尚未合并的 Thunderbolt 优化。

先前候选副本 SHA-256 `ec766f0ce0505b8c49c3dd31cff908f99db74624b35c988b788b5b8634da62e4` 仅属于早期验证。独立逐字节比对表明其 504 个产品类对应结构候选 `94e431fca91d85fa6510c16150a9cc0d22d09437`（alpha 迁移 `5e3c0bcecb65cc9ea30491c13fd812f7f5590fa0`），不是未优化基线、不是后续质量修正，也不是上述官方发布资产；manifest 无 commit 字段，不能由类相同推断唯一打包 HEAD。

本机 `build/verification/` 保留各日志、JUnit XML、注入脚本、发布资产元数据与产物散列，原始日志不提交。本地验证 jar 为 `build/libs/ae2lt-2.1.2-beta3.jar`，不是发布行为。

```sh
# init 脚本仅移除原 Thunderbolt 坐标并注入上述校验过的本地官方发布 jar。
./gradlew -I build/verification/local-released-dependency.init.gradle build --offline --no-daemon --no-configuration-cache --max-workers=2
# 常规测试使用新建的隔离测试世界，避免旧测试端点干扰预算。
./gradlew -I build/verification/local-released-dependency.init.gradle -I scripts/wireless-energy-test.init.gradle runGameTestServer --offline --no-daemon --no-configuration-cache --max-workers=2
# prepare / verify 顺序运行，并保留二者之间的同一个隔离世界。
./gradlew -I build/verification/local-released-dependency.init.gradle -I scripts/wireless-energy-test.init.gradle -PwirelessRestartPhase=prepare runGameTestServer --offline --no-daemon --no-configuration-cache --max-workers=2
./gradlew -I build/verification/local-released-dependency.init.gradle -I scripts/wireless-energy-test.init.gradle -PwirelessRestartPhase=verify runGameTestServer --offline --no-daemon --no-configuration-cache --max-workers=2
./gradlew -I scripts/provider-energy-output-test.init.gradle -I build/verification/local-released-dependency.init.gradle runGameTestServer --offline --no-daemon --no-configuration-cache --max-workers=2
```

### 未运行与待完成

- 未运行客户端手工交互验收、长时服务器压力测试或断电/半写盘故障注入。三轮真实卸载重载不等于长时 soak。
- 精简 GameTest runtime 没有 ExtendedAE；既有 `ae2lt:assembler/overload_processor` 的 `extendedae:config` recipe codec 警告仍存在，不声称全可选模组组合无警告。
- FE→AE 运行网络补能、非原生 FE 观察桥、UI 链接/消耗展示、孤立与异常账户人工恢复入口均未实现；边界见上文。
- 原依赖坐标的认证仓库解析仍需在最终发布环境核验。该问题与官方发布 jar 的兼容性验证分开报告。
