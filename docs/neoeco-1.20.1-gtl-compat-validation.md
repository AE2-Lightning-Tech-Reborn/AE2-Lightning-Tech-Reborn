# NeoECO 1.20.1 GTL 适配与验证

日期：2026-10-26。本次按用户最新要求加入 NeoECO 适配；此前排除 ECO 的记录保留为历史结果。

基线：`1.20.1_GTL`，`550b1af1c14d37ca0f1f431cdc33311d31b26965`。
参考：[NeoECOAEExtension / 1.20.1](https://github.com/DancingSnow0517/NeoECOAEExtension/tree/1.20.1)，
`d1e306c7fbf557fc883f8c804b0d10c22b057f89`（20.5.1）。

## 运行要求

| 组合 | 本轮环境 |
| --- | --- |
| Minecraft / Java | 1.20.1 / Eclipse Adoptium 17.0.20 |
| 不安装 ECO | Forge 47.1.47，保留项目原基线 |
| 安装 ECO 20.5.1 | Forge 47.4.10；这是 ECO 正式包要求的最低 Forge |
| AE2 / GuideME | 15.4.10 / 20.1.15 |
| Thunderbolt | Forge 1.20.1 2.0.3-beta3，沿用已验证的独立核心包 |
| LDLib | Modrinth 制品 `FX8CaaJ8` |

ECO 为可选依赖，声明版本范围 `[20.5.1,21)`，实际验证版本为 20.5.1。
普通构建不下载或内嵌 ECO，不提高项目默认 Forge 版本。安装 ECO 时应同时满足其 Forge 要求。

## 实现

- 天枢批量执行器接入 ECO 的公开 `prepareAllocated` 与任务 UUID 派发接口。
  提取材料前先限制到 ECO 获准容量，CPU 负责输入、能耗与产物账本，避免重复扣费。
  复制输入模板并保存借用任务视图的 UUID；明确拒收可降级单份派发，提交状态不确定时停止派发并保留所有权隔离。
- 保留 Useless 批量适配器及其缓存策略；ECO 缺席时不链接其可选类型。
- 矩阵样板迁移与 ECO 共用每网协调租约，检查整个关联网络的工作线程及待交付产物。
  从样板总线物理槽位迁移，保留编码样板 NBT，批量更新使用 ECO 的 begin/end 接口；未成型总线拒绝迁移。
- 为公开合成记账接口增加 Forge 1.20.1 的存档重载，ECO 可直接使用公开接口保存、恢复待交付账本。
  不恢复旧的 ECO CPU 私有实现 Mixin。
- 加入 5 个晶体催化配方、3 个超限加工配方，均受 `forge:mod_loaded/neoecoae` 条件控制。
- 保留上游署名与 GPLv3 来源说明；没有复制 ECO 实现、资源或打包其依赖 JAR。

## 本轮结果

| 验证 | 结果 | 证据文件名 |
| --- | --- | --- |
| 全量 JUnit（含 EAEP 实际 JAR 条件用例） | 1,688 项，0 失败、0 错误、0 跳过 | `ae-test-final-full-and-release.log`、JUnit XML |
| ECO 存在，Forge 47.4.10 GameTest | 9 项通过 | `ae-runGameTestServer-eco-admission-and-migration.log` |
| ECO 缺席，Forge 47.1.47 GameTest | 2 项通过 | `ae-runGameTestServer-noeco-baseline.log` |
| `assemble`、`reobfJar`、`reobfJarJar` | 通过 | `ae-test-final-full-and-release.log` |
| 正式发行包 + 官方 ECO 原包首次启动、保存退出 | 通过，进程退出码 0 | `production-cold-start.log` |
| 同一世界重新启动、读取磁盘存档标记、保存退出 | 通过，标记恢复，退出码 0 | `production-restart.log` |
| 移除 AE2LT 的 ECO 对照启动 | 通过，重现下述上游错误 | `production-eco-only-control.log` |
| 发行包内容审计 | 8 个条件配方、可选依赖范围正确；无 ECO 嵌套包、旧 CPU Mixin 或本次新增测试夹具 | `package-audit.json` |

9 项 ECO GameTest 包含：4 项真实 ECO facade + 实际变换后的 BatchExecutor 协议测试，
3 项协调器、物理总线库存与公开账本接口选择测试，以及 2 项配方矩阵和催化生产测试。
催化生产实测得到 16 个充能水晶，催化剂保留，流体、FE 与闪电按配方扣除。
派发测试的 provider 是协议夹具，不能等同于实际成型 ECO 多方块的全流程生产测试。

GameTest 使用开发映射的 ECO 副本及映射后的嵌套 Registrate；这些副本仅用于测试。
正式包验证则使用官方未修改的 ECO 20.5.1 JAR、正式 AE2LT 与 Thunderbolt JAR，未安装测试探针。
原始日志、结构化结果、隔离构建及世界留在本轮外部验证目录，未加入 Git。

## 失败追踪与已知限制

新增账本存档测试首次失败于普通 JUnit 未初始化 AE2 key type registry；
接口代理尝试又因 AE2 强制转换到真实 ForgeRegistry 而失败。
最终夹具采用私有 RegistryManager 建立真实注册表，测试结束恢复原 supplier，未修改产品逻辑或放宽断言。
定向 7 项及随后全量 1,688 项均通过；两次失败记录保留。

开发启动初期还遇到 Forge 参数被 PowerShell 拆分、嵌套 Registrate 未映射及截断的 Minecraft 元数据。
这些问题均发生在行为测试前；修正隔离测试环境后运行上述测试，没有修改官方 ECO JAR或全局缓存。

正式专服启动仍记录非致命错误：LDLib 扫描 ECO 的 `PatternItemSlot` 注解时触发
`LocalPlayer` 的服务端类加载检查。移除 AE2LT 的独立对照也复现相同错误并正常启动，
确认该错误不依赖本次 AE2LT 适配。未修改其他项目，也未在本项目加入屏蔽异常的补丁。

本轮未执行图形客户端、完整 GTL 整合包、实际成型 ECO 多方块的端到端任务与长期负载测试。
正式包重启验证的是世界磁盘保存与加载；待交付账本的往返保存由独立行为测试覆盖。

## 制品

- 文件：`ae2lt-forge-1.20.1-gtl-2.1.2-beta2.jar`
- 大小：7,143,603 字节
- SHA-256：`0296996eeead66d4560ff87e2948a86ac1862ac077be54641d022f9903220a62`
- 官方 ECO SHA-256：`a44268d5895dcb4ac99f327ed744fe3a7f94dc224a85e57bc27ac13dc8ed8d3b`
- 配套 Thunderbolt SHA-256：`b4b8e379aeb1ea1971062b09daa0ff677bebbbc02c33c4fccbfe606ec395caff`

## 测试入口

JUnit：`gradlew test`；发行包：`gradlew assemble`。

GameTest 筛选脚本为 `scripts/neoeco-compat-test.init.gradle`。
ECO 存在矩阵需提供已正确映射的 ECO 20.5.1（包括嵌套 Registrate）和 MixinExtras 0.5.3，
并设置 `-Pforge_version=47.4.10`；PowerShell 中应将整个 `-P` 参数加引号。
缺席矩阵使用 `-PneoecoTestPresent=false`，不加载 ECO，沿用默认 Forge。
该筛选脚本本身不会下载第三方依赖。
