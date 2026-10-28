# Dev25 图形客户端兼容与恢复补测（2026-10-27）

本轮接续 [gto.10 扩展修复](GTO_EXTENDED_FIXES_2026-10-27.md)，基于 `e5731cac70eec6630f6dcf179bff1c3cc58af882`，更新 LT 为 `2.1.2-gto.11`。继续保留 NeoECO 兼容。本记录区分最终制品、同版本的早期候选和测试附件。

## 环境与实际制品

Minecraft 1.20.1、Java 21.0.10、Forge 47.4.20。Dev25 使用 AE2-GTO `15.2610.40`、GTOCore `26.10.18`、EMI `1.1.1027`；Dev23 回归使用 AE2-GTO `15.2610.31`、GTOCore `26.10.10`、EMI `1.1.25`。两组均使用 Core `2.0.3-gto.6` 和可选 NeoECO `20.5.2-gto.1`。

| 制品 | SHA256 |
| --- | --- |
| LT `2.1.2-gto.11` 最终正式包 | `67b1827b5424390c45299be6454d65f7b65936513dfcf59e3cd13fee845f013c` |
| 最终独立 QA 附件 | `8999eba8d39c8bdf79bcf8ceb091203b76141ceeeef1b1f5e9b79a0c8e9f7a23` |
| 最终 LT 附加夹具副本 | `bffd9eeeb6492f76c6e93ec30775c7a144c21343b1b5afa540ca24abcdf8c24e` |
| Core `2.0.3-gto.6` 原包 | `85ae8b28afacd951d19af79a1ad8708d01f29e834e17d7e0448772ed4bbddd1f` |
| NeoECO `20.5.2-gto.1` 原包 | `c535ea3256a1ab5b0ad6025a28b5ead90e295b3fe7c2a1198246c1369a4d7f8b` |
| Dev25 EMI `1.1.1027` | `3fcbcc12909c29ee289a94b4f32e59f8fa399b45f9d0125d0526745d7b649a40` |

分发文件为 `build/libs/ae2lt-forge-1.20.1-2.1.2-gto.11.jar`，不分发 QA 附件或夹具副本。正式包的 2,165 个生产类与夹具副本逐字节相同；ZIP CRC、重复条目、1,427 个 JSON、Java 类版本、依赖范围、Mixin／refmap、嵌套归档、许可证和 QA 排除检查均通过。

### 官方客户端来源

下载了 [Dev25 官方客户端 ZIP](https://github.com/GregTech-Odyssey/GregTech-Odyssey/releases/tag/0.6.0-dev-25)，文件 `GregTech.Odyssey-0.6.0-dev25.zip` 的 SHA256 为 `48739c7d3cc706bb89965e5e102b01e729330bff80b39838a08c9d7e60a90661`，与发布摘要一致。release target 为 `f04e3482eb9d675ca7a933985e18ce11392721e7`。客户端 manifest 的 153 个索引制品（含 28 个客户端专属制品）均逐项核对 SHA1，另采用发布 overrides 中的 datasynclib `26.10.11`、EMI `1.1.1027` 和 GTOCore `26.10.18`。

该 target 源码里的四个 pw 索引比客户端发布 manifest 旧。本轮以正式发布 manifest 和官方服务端内对应的新索引为准：

| 组件 | 源码 target 索引 | 发布 manifest |
| --- | --- | --- |
| AE2-GTO | 15.2610.28 | 15.2610.40 |
| Ars Nouveau | 4.12.19 | 4.12.23 |
| Botania | 457.2610.17 | 457.2610.24 |
| GTM | 26.10.70 | 26.10.80 |

在官方组合上附加配套 Core、LT、NeoECO 和独立 QA。没有用旧依赖补齐 Dev25。

## 修复

- **确认页加载失败**：AE2-GTO `.40` 将标题从 `setTextContent` 移到 `CraftingScreenTitle`，Core gto.6 的第二个旧调用点匹配为 0，两个图形 JVM 均在主菜单前失败。LT 仅在客户端、AE2 `.40`、Core gto.6 的精确组合下，经 GTO 已带的 MixinSquared 调整 Core 两个文本回调的调用点：算法名／精确字节数指向新标题控件，预览状态指向剩余文本调用。回调方法体、require 数量及功能保持；真实调用数量或旧注解契约改变时明确失败。不修改 Core 原 JAR 或其他项目。
- **Dev25 JEMI 生命周期**：官方 EMI 为 `1.1.1027`，同样存在直接解引用可空 JEI runtime 的悬停回调。将该精确版本加入已有快照保护，保留 Dev23 `1.1.25`；卸载期间返回空交互，恢复后沿用正常查询。
- **LT 报告页原生按钮布局**：截图复核发现 `.40` 新增的“跟随开始”按钮因缺少样式位置而落在屏幕左上角。保留原生控件及动作，增加独立一行；只有实际存在该控件时扩展页脚、相应计算可见行数。旧版不增加空白行。
- **间歇性登录读取停滞**：重连失败时，客户端处于 LOGIN、连接活动、`autoRead=true`、待发送缓冲为 0，但连续多次采样均为 `interestOps=0,readPending=false`，最终触发默认 30 秒读取超时。同版本 Netty 4.1.82 的真实 TCP 反例稳定复现了延迟的旧 `clearReadPending` 覆盖读取恢复。现于协议切换完成后在 event loop 排队恢复一次读取，确保位于旧清理任务之后；执行时再次核对连接活动及当前 `autoRead`，后续主动暂停优先。沿用 GTOCore + Krypton 门控，保留已有 LOGIN 刷新和 PLAY 发送合并，不提高超时、不轮询强制读取、不移除 Krypton。

`ThunderboltMixinBoundaryTest` 原先通过源码子串禁止链接 Core Mixin，将兼容目标的字符串选择器也误判为类型依赖。现改为 ASM 检查所有已编译生产类的真实类型引用，并分别验证直接 accessor 类型被拒绝、纯字符串选择器被允许。实际 `.40` 二进制契约测试还验证旧预览注入为零匹配、适配后两个回调各匹配一次，且回调指令体不变；未知布局拒绝适配。

## 验证

最终候选执行 `test build gtoQaJar gtoQaLtJar -PgtoQa` 成功，启用了真实 EAEP 1.6.3、新 NeoECO 和 Dev25 AE2 夹具。JUnit **1,608 项：1,607 通过、1 跳过、0 失败、0 错误**。新增真实 TCP 回归检查旧任务顺序能复现丢失读取、修复后实际字节送达、后续主动暂停仍生效，以及空／关闭连接。跳过项要求普通映射原生 CPU；真实 GTO CPU 已由前轮专服场景验证。

实际包夹具参数如下，路径替换为已核对哈希的本地原件：

```sh
./gradlew test build gtoQaJar gtoQaLtJar -PgtoQa \
  -Peaep_test_jar=/fixtures/extendedae_plus-1.6.3.jar \
  -Pneoeco_fixed_release=/fixtures/neoecoae-20.5.2-gto.1.jar \
  -Pgto_dev25_ae2_test_jar=/fixtures/appliedenergistics2-forge-1.20.1-15.2610.40.jar
```

图形测试使用两个独立图形 JVM、不同离线身份及普通 ConnectScreen，经回环 TCP 连接独立专服，`gtoQaLoopbackConnect=false`。正式 LT 主包未插桩。原生／LT 确认页探针在真实世界里构造客户端摘要用于 UI 渲染；这部分不代表从服务端提交或执行这些合成订单。

| 最终包场景 | 结果与证据 |
| --- | --- |
| Dev25 官方客户端双人 | 关闭诊断后通过。两个客户端实际点击高级界面，服务端收到八次独立 ACK，两端收到关闭／开启的 GuiSync，新容器重开保留状态。真实 EMI 加载完成后，检查 JEMI runtime 置空／恢复时的悬停查询。专服及两个客户端均退出 0。证据：`dev25-network-ui-final-pair.json`。 |
| Dev25 原生及 LT 确认页 | 通过。原生标题保留 Core 算法名，`2^100+37` 字节按精确值格式化；可执行摘要不误标预览、模拟摘要显示预览、普通摘要清除先前精确值及预览。路由 LT 报告后检查跟随按钮仍存在且位于卡片内、没有与 CPU 选择器重叠；实际截图复核通过。 |
| Dev23 旧组合图形回归 | 关闭诊断后通过。同组双人实际菜单、JEMI 生命周期和原生／LT 确认页检查均通过；旧版本没有跟随按钮，也没有多出的页脚行。专服及两客户端均退出 0。证据：`dev23-network-ui-final-pair.json`，截图复核通过。 |
| 最终包 Dev25 双人反复登录 | 关闭诊断后通过。两个独立图形 JVM 各完成 8 次普通 TCP 登录、世界渲染、精确终端格式检查和屏幕转换；每次退出前等待真实 EMI 完成加载。服务端核对两个身份各 8 次登录，三进程退出 0。证据：`dev25-reconnect-fixed-pair.json`。 |
| 最终包 Dev23 双人反复登录 | 关闭诊断后通过，同组两个客户端各 8 次登录，服务端逐项核对，三进程退出 0。证据：`dev23-reconnect-fixed-pair.json`。与 Dev25 合计 32 次成功登录。 |
| 最终包保存后强制终止恢复 | 通过。真实任务已派发输入、剩余 3／等待 1 时执行 `save-all flush`，收到保存确认后直接终止本次 Java PID；准备进程退出 1，日志无正常停服。第二 JVM 恢复同一任务 `6f399c03-6b46-438e-a9de-313ff4b38d67`，消耗 3、交付 3；第三 JVM 确认完成任务没有复活，磁盘产物 3、原料 0、处理箱为空。恢复及复核进程均退出 0。证据：`dev25-final-forced-restart.json`。 |

`final-validation-summary.json` 汇总了上述 15 个独立进程实际加载的制品记录；LT、Core、NeoECO 和 QA 哈希均与本记录表首一致。`network-fix-v7-release-artifact-validation.json` 为最终包结构检查，`dev25-network-fix-junit.json` 和封存 XML 为最终 JUnit 统计。

主要生产文件为 `GtoCoreClientMixinExtension.java`、`GtoConnectionReads.java`、`GtoLoginPacketFlushMixin.java`、`RecipeViewerMixinPlugin.java`、`AE2LtCraftConfirmScreen.java` 和 `ae2lt_craft_confirm.json`；同时更新实际包夹具参数、QA、回归测试、版本及兼容文档。未改动 `AGENTS.md` 或其他项目。

## 历史失败与验证边界

保留原先 `.10` 的官方 Dev25 客户端注入失败。早期 `.11` 正式候选 SHA256 为 `fe0a7f97c0b5bd9b6162fd5f37391ce6a9842b8fa17f48ef0375bed1558dcbe4`：双人联机、确认页程序断言及保存确认后的强制终止恢复通过，但截图暴露上述跟随按钮位置问题；不能将早期候选的哈希当作最终发行包。

确认页夹具最初把“仅预览”标记放在可执行摘要上。Core 的实际契约要求模拟摘要才显示预览，因此保留可执行场景检查其不显示，并另加模拟场景检查其显示，随后检查普通计划清除精确值及预览状态。没有提高原有超时／预算、改变订单数量或取消第三方功能来通过测试。

未修复登录读取的布局候选 SHA256 为 `ed9aa0236323fe857bcaff6ff5f42dfed6aaf3581eca9ca96995f98250eab15e`。其首次无诊断重连失败（服务端只有一名玩家成功登录 5 次）；开启完整 DEBUG 的 16 次登录通过，但低干扰采样再次失败，两名玩家均在第 8 次前出现上述读取订阅丢失。因此 DEBUG 通过不能抵消原失败。证据分别为 `dev25-reconnect-eight-pair.json`、`dev25-reconnect-diagnostic-pair.json`、`dev25-reconnect-quiet-diagnostic-pair.json`；独立 Netty 反例为 `netty-read-interest-race-proof.json`。

前轮 gto.10 的 Dev25 精确数量 19 项、真实 F9／FX 网络、可选 EAEP／Useless／IF／Mekanism 机器与压力测试，仍按 [扩展修复记录](GTO_EXTENDED_FIXES_2026-10-27.md) 中各自的制品和结果追溯。本轮变化涉及确认页、Mixin／浏览器插件及上述网络读取恢复；合成、供应器、数量和持久化生产类未变。这些历史组不计为 gto.11 重新执行。

本轮覆盖本地离线身份及指定菜单，未覆盖公网正版认证、长时间联机、全部第三方功能或写盘中途断电。重复八次登录属于有界重连回归，不代表长期稳定性测试。强制终止恢复限定收到 `save-all flush` 保存确认之后，不代表任意崩溃点的数据一致性。

完整环境证据封存于本机独立目录 `ae2lt-gto-continuation-20261027`，包括各次实际加载哈希、官方发布清单、失败日志、JUnit XML、截图和运行结果。环境日志及实例不加入源码仓库。
