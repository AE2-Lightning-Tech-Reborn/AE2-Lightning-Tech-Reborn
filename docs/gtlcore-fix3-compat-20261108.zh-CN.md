# GTLCore 1.2.3.3-fix3 兼容修复与验证

本轮在 Forge 1.20.1 GTL 工作区修复了超大数计划与 AE2CraftingTree 共存时的预览发包空指针，并提供 GTLCore 配方字段改名的 KubeJS 迁移工具。最终冻结包的 JUnit 1971/1971、正式专服 GameTest 100/100 和真实客户端操作回归均通过。验证副本仍缺部分扩展模组，保留 4 条配方脚本错误，因此**完整整合包尚未验收通过，也不能据此宣布原 #63 全部修复**。

日期为本地 2026-11-08（最终运行记录为 2026-11-09 UTC）。源码基线为本地 `1.20.1_GTL` 的 `f377402ed56dc10e305fd400f8470e6ccfc011fb`。本轮只在本项目内修改；未恢复 stash、未修改 AGENTS.md、未恢复 NeoECO、未改其他项目，未推送或发布。

## 实际修复

### 超大数预览的 Crafting Tree 数据缺失

`BigCraftingPlan.summary()` 自行建立原生摘要并附加精确报告，却没有填充 AE2CraftingTree 1.1.1 在序列化时要求的 `RecipeHelper`。此前把这一故障只归于夹具的判断证据不足。本轮用真实产品摘要工厂确认 `jobs == null` 空指针；在其他 JAR 不变、只替换 AE2LT JAR 的前后对照中，`summary-before-r1` 失败、`summary-after-r1` 通过。

新增可选反射桥 `CraftingTreeSummaryCompat`，按实际依赖的 `ICraftingPlanSummary.getJob()/setJob()`、`RecipeHelper` 和嵌套 `Recipe` 构造接口填充目标产物及配方输入、输出、次数。`ClassValue` 缓存按摘要类型保存访问器；未安装该模组时不调用其 API，已有 payload 不被覆盖。树协议中的 long 数量仅为显示投影，执行及精确缺料继续使用原 BigInteger 数据。

`BigForgePacketGameTests` 改为调用真实 `BigCraftingPlan.summary()`，保留 `10^100` 精确报告发包往返及缓冲区无剩余字节检查。客户端还验证了包含真实配方的树数据，不只验证空计划。

### GTLCore 配方字段改名

已核对的官方 fix3 JAR 使用 `BLOCK_CONVERSION_RECIPES`，验证副本的完整 KubeJS 仍引用旧字段 `BLOCK_CONVERSIONRECIPES`。新增 `scripts/patch-gtlcore-kubejs.py`：默认只预览；显式 `--apply` 时先备份，再原子替换字段 token；保留其余文件字节，不停用脚本、不改 JAR。工具只接受下文列出的官方 fix3 SHA256，并检查实际 class 中的字段。

使用 Python 3.9 或以上版本，在选定的整合包目录上操作：

```powershell
python scripts/patch-gtlcore-kubejs.py --instance "<整合包目录>"
python scripts/patch-gtlcore-kubejs.py --instance "<整合包目录>" --apply
```

备份位于该实例 `ae2lt-compat-backups/gtlcore-1.2.3.3-fix3/` 下；可加 `--report <新文件路径>` 保存 JSON。预览、应用和再次应用均已验证：首次只替换 1 处，再次应用为 0 改动。客户端及专服恢复完整 KubeJS 后，startup 日志均为 0 ERROR；剩余 server recipe 错误见下文。

### 闪电回归的定位修正

并行回归中的人工闪电被 SkyblockBuilder 5.1.33 的 `EntityJoinLevelEvent` 处理器吸引到另一个测试的避雷针：材料位于 `(129.5, 102, 321.5)`，闪电加入世界时已位于 `(65.5, 103, 321.5)`。事件观察和处理器反编译共同支持这一原因。此前“断言检查过早”的解释在本轮被更正。

`LightningCompatibilityGameTests` 在自己的材料下放置避雷针，并断言闪电加入世界后仍在材料位置；成功结束时恢复原方块。最终保留原 `runAfterDelay(2)`、timeout 20、恰好 1 个产物、全部输入消耗的断言。中间尝试等待的版本已撤回。最终整套 100 项在这些原有约束下通过。

## 冻结环境与制品

| 项目 | 本轮实际版本或身份 |
| --- | --- |
| Minecraft / Forge | 1.20.1 / 正式 Forge 47.4.16 |
| Java | Microsoft 21.0.7+6 |
| AE2 / MAE2 / AE2CraftingTree | 15.4.10 / 1.6.1 / 1.1.1 |
| GTLCore | 官方 1.2.3.3-fix3；固定源码提交 `f79d4c93e8381cd09b840dbdf372524fa912ea61` |
| Thunderbolt Core | 冻结 2.0.4 / `5ca4da079659b4cc89532cfcb0eef1402b882491` |
| Radium | 0.12.4+git.26c9d8e；默认 lithium.properties |
| GTL 引擎 / 库存锁 | GRAPH / DYNAMIC |
| 运行副本 | 专服 84 个 JAR、客户端 86 个 JAR，含隔离 QA/观察夹具；无 NeoECO |

最终包位于 `build/gtlcore-fix3-compat-20261108/release-final-r3/ae2lt-forge-1.20.1-gtl-2.1.3-beta1.jar`；同目录保存 `.sha256` 和 `artifact.json`。客户端与专服测试时使用这一相同包。

| 文件或配置 | SHA256 |
| --- | --- |
| 最终 AE2LT JAR | `cc773e1d444e6f08e43b8d79bb0cccb2e137a44e75d0306ae33c949edf029bd6` |
| GTLCore fix3 JAR | `6dae8e3f6b9b495b8c7c368f8a6e019d3dec182b06d14ff159d5e18f61b97a51` |
| 冻结 Thunderbolt Core JAR | `1bd40221c927b9fc5b26dd1e08fc1ba3f3feb5f7c701e9d23e208793dde4868c` |
| Radium JAR | `b42584e2672d6b5329959ec2b0f342395b19389fed0ee4d86ea71072f42f77a0` |
| 默认 lithium.properties 配置 | `b87ee52fdc722ce1497e7ff55fdbd0660fc9df40c8f2f8167465cab55a388f0d` |

## 已执行验证

以下产物路径均相对于 `build/gtlcore-fix3-compat-20261108/`；原始日志、启动参数、XML 及验证运行工具只保存在本地 build 中，不加入源码提交。

| 验证 | 结果 | 证据 |
| --- | --- | --- |
| 完整 JUnit 与构建 | 1971 通过，0 失败、0 跳过 | `unit-summary.json`、`unit-xml/`、`full-unit-build-r1-result.json` |
| 最终发行构建 | PASS，退出码 0 | `final-build-r3-result.json`、同名日志 |
| 完整正式专服 GameTest | 100/100，正常保存退出 0；LDLib 异步错误和链接错误计数均为 0 | `qa-runs/full-final-r3/{process.json,report.json,console.log}` |
| 最终包真实客户端 | PASS，正常保存退出 0 | `client-launch/runs/exact-client-final-r3/{process.json,probe-result.json,java-console.log}` |
| KubeJS 迁移 | 预览 1 处、应用 1 处、重复应用 0 处 | `scripts-{preview,applied,idempotent}.json` |

完整 JUnit 命令由 `run-gradle.py` 调用仓库 wrapper，使用 `pinned-core.init.gradle`、`validation.init.gradle`、`full-unit-api.init.gradle` 三个已有 init，任务为 `test build -x adaptiveBatchStress -x check`。最终构建使用前两个 init，任务为 `build -x test -x adaptiveBatchStress -x check`。确切 argv、离线缓存及 JVM 参数保存在两个 `*-result.json`。未执行的 `adaptiveBatchStress` 和 `check` 不计为通过。完整 JUnit 后仅修改了 GameTest 类；`final-r3-production-class-comparison.json` 确认最终包与完整 JUnit 对应包的非 GameTest 生产 class 字节一致。

最终专服命令为：

```powershell
python build/gtlcore-fix3-compat-20261108/run-qa.py full-final-r3 cold server-final fixture-original cases
```

客户端的 Java 命令、参数文件、Forge 启动元数据位于该次运行的 `process.json`、`java-arguments.txt`、`launch-plan.json`。使用固定本地离线测试身份；真实终端鼠标操作经过原生改量、确认、开始、状态和取消控件及数据包，没有直接调用服务端提交或取消代替 GUI。

客户端覆盖缺料预览与收藏控件、改量后成功重试、运行状态、GUI 取消及材料守恒、取消后状态清空、取消后再次下单，以及真实多维结构下 `10^30` 请求的精确预览。最终服务端与客户端都报告请求 `1000000000000000000000000000000`、缺少原木 `249999999999999999999999999867`；反序列化得到真实 `RecipeHelper`，含 1 条配方。缺料时开始按钮不可执行。

该轮初始为 139 原木，最终为 133 原木和玩家持有的 24 木板，网络木板为 0、活动 CPU 为 0，总账仍为 556 木板当量。大数预览前后原木、网络木板、玩家木板和活动 CPU 一致。汇总器以本轮 `afterRound3` 快照核对预览只读性，没有套用其他轮次的取消时机常数。

## 保留的失败及未验收项

修复前摘要失败、闪电诊断各轮、复用世界的失败日志全部保留在 `qa-runs/`。`full-cases-final-r1` 在闪电以外还出现 `closedloopuploadundo`、`matricespersistwithoutmemorycardduplication`、`multiplayeruploadundo` 三项失败；干净世界相应用例通过，不能用此替代正式旧存档验证。另一次观察器编译与客户端并行时 JVM native memory 不足，改为串行后构建成功；这是验证环境失败，未计为产品缺陷。

验证副本未装 `gtladditions`、`gtl_enhancedcore` 等扩展，完整 server recipe 脚本仍有 4 条 ERROR，客户端与专服各保留一份：

- `配方添加-模组整合.js:649` 引用缺失的 `gtl_enhancedcore:neutron_control_factory`。
- `配方添加-私货.js:180`、`:181` 引用缺失的 GTLAdditions 缓存/代理物品。
- `配方添加-私货.js:195` 引用该扩展提供的 `nightmare_crafting` 配方类型。

这些脚本没有被停用，也没有通过删配方使日志变绿。本轮未扩装或修改这些外部项目。未完成远程多人、长时间压力、超 long 订单真正执行、任意机器组合及完整原整合包重建。原 #63 的全部包身份、有效配置、订单及原失败分支仍未被完整重建；本轮确认的是上述具体兼容修复及所列场景。

汇总与身份核对见 `result.json`、`evidence-sha256.json`；闪电证据见 `lightning-root-cause.json`、`skyblockbuilder-lightning-handler-javap.txt`。`preflight.json` 与收尾核对保留 AGENTS、stash、其他受控文件和源存档的身份。源码提交仅含 4 个 Java 文件、迁移脚本和本报告，不含 JAR、缓存、存档或运行日志。
