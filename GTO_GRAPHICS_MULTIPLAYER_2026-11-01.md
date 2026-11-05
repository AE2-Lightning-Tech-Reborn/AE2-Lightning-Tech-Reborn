# Forge 1.20.1 GTO 图形与多人验证

本轮计划 20 组，已执行 **20 组**，目前 **16／20 通过**，整体验收尚未通过。
图形结果来自真实客户端，多人使用普通 TCP 连接，不用预检、编译或服务端启动代替行为通过。

## 本次修复

- 修正 `JeiCompatibilityClientProbe` 的真实 EMI 分类 API 调用；现在完整 QA 编译包含该客户端探针。
- 在 `GtoEaepMixinExtension` 中将 EAEP 编码按钮的旧局部变量注入改到两版 GTO AE2 唯一的 `ActionButton` 构造表达式。保留 EAEP 的按钮替换、强制编码包、正常编码和上传回调；异常布局明确失败，不降低注入要求。
- EAEP `1.6.3` 在每帧末尾重写文本标题，会覆盖 Dev23 的 Core 算法名称和精确字节数。新增 `GtoEaepCraftConfirmSupport`，只包装这一处标题写入，保留普通数值格式、强制开始和书签按钮；普通／精确／预览／恢复路径独立验证。
- 新增真实两版 AE2 和 EAEP 字节码回归，以及标题组合行为回归；`build.gradle` 支持显式指定 Dev23 测试 JAR。确认页夹具保留原行为断言并补充失败诊断。
- 同步 Useless 转换类在 Mixin `TargetClassContext` 中单独保存的泛型签名，防止合并末尾恢复旧 AE2 JEI 接口；保留严格的最终类审计。万象夹具改用 Forge 的 SRG 字段反射，保留原槽位与数量断言。
- QA 的大数库存／IPN 探针复用现有 `ClientRepoAccess` 双版本桥；能源界面夹具按实际映射表访问鼠标、界面尺寸和聊天字段。万象夹具保持创造飞行及接入点旁站位，避免玩家掉到基岩层后无线链路失效。
- 频率卡夹具保持原 1,600 tick 截止、四组原生点击及全部断言，把固定空等间隔从 25 改为 10 tick。FTB Library 全局侧栏按屏幕范围检查，Inventory Tweaks 注入的排序控件验证为完全隐藏或位于屏幕内；LT 控件仍保留紧凑框架边界检查。
- 截图复查发现网络管理背景缺失纹理。发行包 473 张 PNG 中，四张无线界面资源的 PNG 头损坏；已从固定普通 `1.20.1` 提交 `2df9c5b5d4c1f8efb6567b4bc01f4b1edf9c59f7` 按原始字节恢复，Git blob 与上游一致，不重新绘制或更改布局。补跑受影响页面并检查渲染截图；发行包增加全部 PNG 的校验与解码。

相对上一候选 `565b36c223ed…`，生产类差异限于 EAEP 兼容扩展及 Useless 的两个转换类，
增加标题辅助类，其余 **2,362 个生产类逐字节一致**。
正式包与测试副本的 **2,366 个生产类逐字节一致**。

## 固定环境与制品

Minecraft `1.20.1`，Forge `47.4.20`，Microsoft OpenJDK `21.0.10`，LT `2.1.3-gto.1`。

| 环境 | GTOCore／GTM／AE2 | EMI | AppFlux |
| --- | --- | --- | --- |
| Dev23 | `26.10.10`／`26.10.70`／`15.2610.31` | `1.1.25` | `1.20-1.3.7-forge` |
| Dev25 | `26.10.18`／`26.10.80`／`15.2610.40` | `1.1.1027` | 缺席，沿用已验收组合 |

两版均保留 Core `2.0.4-gto.3`、NeoECO `20.5.2-gto.1`、EAEP `1.6.3`、
FluxNetworks `7.2.1.15`、JEI `15.59.0.212`、AE2WTlib `15.3.3`。
万象专项额外安装实际 Useless `20.2.4.7-gto.1`。不以关闭 EMI 或移除 EAEP 绕开兼容问题。

| 制品 | SHA-256 |
| --- | --- |
| LT 正式包 | `aa3a89638a1babdf40258e77377a5239a8197fffe5e2584ff9b6cc39c641582e` |
| LT 附加夹具包 | `b6397085a544717f9b1147fd433bbbc71e6a7f735f7969e70ec8b7481756920f` |
| 独立 QA | `a3bc873cd9800cc03bf8753e29403202cde7b21c1403f7e71527f83a4c956c82` |
| Core `.3` | `1ebff68ab9a2c7e041b8ca8338acb1a00aa9f5eacfa6604b4f55f2cd04be85a9` |

多人四组使用的正式包为 `221394f5b77c699e8fc280c5abcb434501eeec75f52276e74181f484f3371806`，
独立 QA 为 `6f3dc3e348bdbbd46d28932215692b1e425cc49d0d20a174bd5e1923293ed472`。
其后生产变更仅为 Useless 的两个转换类；多人环境未安装 Useless，该扩展未注册。
下表保留这些实际运行哈希，不声称新正式包重复运行了多人组。
单人首轮正式包为 `be5e0c3db8b3226b85dac8e97c933347070dabdbdaf6afa7488395aee32c5a38`；
PNG 修复后的最终包与它的 2,366 个生产类完全一致，仅修复四张资源及构建清单。
资源修复后补跑 AppFlux 两种组合、频率卡和无线管理，其他已完成轮次保留旧哈希，不改记为新包运行。
单人 QA 因万象夹具反射和站位修正产生多个副本哈希，下表逐项列出实际前缀；
这些副本各自冻结并核验全部 2,366 个生产类与最终正式包一致，不替换旧运行记录的 QA 哈希。

## 构建与回归

`test check build gtoQaLtJar gtoQaJar` 通过，未筛掉客户端探针。
完整 JUnit **1919 通过、14 跳过、0 失败／错误**，共 1933 项。
14 项跳过分别为默认未启用的实际 API 审计 12 项、可选 NeoECO 制品检查 1 项，以及 GTO 替换的旧 CPU 映射项 1 项。
真实 EAEP／双版 AE2／Useless／Core 定向检查属于完整 JUnit 的子集，不重复累计。

包校验通过：1,438 个 JSON、473 张 PNG、ZIP 与嵌套包、Java 类版本、版本依赖、许可证与署名、
Mixin／refmap、生产类一致性及 QA 排除均已核对。正式包不嵌入 Core 或 NeoECO 类。

## 实机矩阵

两版各启动两个独立图形 JVM 和一个专服，使用不同离线 UUID，经 `127.0.0.1:25587`
普通 `ConnectScreen` TCP 登录；未启用 loopback 绕行。双人同步检查实际按钮点击、
两侧 GuiSync、重新打开后状态持久，并要求各版准确 8 个唯一玩家／阶段／状态回执。
连续重连要求每个玩家实际登录 8 次且每次完成渲染检查。
单人专项使用独立的新存档；一次只运行一组图形进程。

| 专项 | 本轮实际结果 |
| --- | --- |
| dev23 multiplayer | **通过**，157.4 秒；实际登录 1＋1 次，唯一同步回执 8／8 |
| dev23 reconnect | **通过**，243.4 秒；实际登录 8＋8 次 |
| dev25 multiplayer | **通过**，151.4 秒；实际登录 1＋1 次，唯一同步回执 8／8 |
| dev25 reconnect | **通过**，241.3 秒；实际登录 8＋8 次 |
| dev23 upload-alias | **通过**，141.2 秒；新截图 4 张；正式包 `be5e0c3db8b3`／QA `c6aec6a07742` |
| dev23 jei-service-emi | **失败**，87.1 秒；新截图 0 张；正式包 `be5e0c3db8b3`／QA `c6aec6a07742` |
| dev23 omniversal-processing | **通过**，129.2 秒；新截图 2 张；正式包 `be5e0c3db8b3`／QA `d75c0fe32432` |
| dev23 stock | **通过**，99.2 秒；新截图 2 张；正式包 `be5e0c3db8b3`／QA `a4e854889d2a` |
| dev23 provider | **通过**，96.1 秒；新截图 2 张；正式包 `be5e0c3db8b3`／QA `a4e854889d2a` |
| dev23 remote-device | **失败**，159.3 秒；新截图 20 张；正式包 `be5e0c3db8b3`／QA `a4e854889d2a` |
| dev23 appflux-present | **通过**，108.2 秒；新截图 14 张；正式包 `aa3a89638a1b`／QA `b6397085a544` |
| dev23 frequency-card | **通过**，105.3 秒；新截图 15 张；正式包 `aa3a89638a1b`／QA `b6397085a544` |
| dev23 wireless-energy-menu | **通过**，195.3 秒；新截图 33 张；正式包 `aa3a89638a1b`／QA `b6397085a544` |
| dev25 upload-alias | **通过**，141.2 秒；新截图 4 张；正式包 `be5e0c3db8b3`／QA `c6aec6a07742` |
| dev25 jei-service-emi | **失败**，87.1 秒；新截图 0 张；正式包 `be5e0c3db8b3`／QA `c6aec6a07742` |
| dev25 omniversal-processing | **通过**，129.2 秒；新截图 2 张；正式包 `be5e0c3db8b3`／QA `d75c0fe32432` |
| dev25 stock | **通过**，99.1 秒；新截图 2 张；正式包 `be5e0c3db8b3`／QA `d75c0fe32432` |
| dev25 provider | **通过**，96.2 秒；新截图 2 张；正式包 `be5e0c3db8b3`／QA `c6aec6a07742` |
| dev25 remote-device | **失败**，159.3 秒；新截图 20 张；正式包 `be5e0c3db8b3`／QA `d75c0fe32432` |
| dev25 appflux-absent | **通过**，108.3 秒；新截图 14 张；正式包 `aa3a89638a1b`／QA `b6397085a544` |

上表所列轮次共归档 **160 张新截图**；历史失败轮次截图另存，不纳入此数。
确认页检查包含原生算法名称、`2^100+37` 精确字节数、
模拟预览、回到普通计划后的状态恢复和 LT 报告；这些 UI 计划由夹具提供，
不宣称本轮重新执行服务端 exact 规划器。

## 失败保留与边界

- 首轮 Dev25 在 EAEP `eap$wrapEncodeButton` 的 `ModifyVariable STORE` 注入失败，尚未登录，0 回执／截图。修复后以新标签完整复测，原失败证据保留。
- 仅修复按钮后的 Dev23 已完成双人同步，但确认页算法名称断言失败；定位到 EAEP 的末尾标题覆盖。本篇矩阵只计最终标题修复候选，不把中间候选的通过迁移到新哈希。
- 首次真实 EAEP JUnit 的两项数量表类型转换失败来自测试启动遗漏已有 `ae2lt.testLegacyCounterEntries` 开关；补齐与实际整合包一致的测试条件后原断言通过，没有新增或重复修改取料生产语义。
- 首轮 Dev25 万象的匿名 JEI 转换器在 Mixin 合并后恢复旧泛型签名，最终审计明确失败；另在中键编辑阶段遇到夹具 `hoveredSlot` 的生产映射反射失败。原记录保留，修复后使用新标签与新存档复验。
- 随后的万象无线组在转移前失败：服务端保留沙子×3／PROCESSING，客户端仍为空槽／CRAFTING；截图显示未飞行的夹具玩家已经掉到基岩层。修正站位而不改变生产范围检查，原有线六项完成摘要仍只属于原轮次。
- Dev25 库存和远程设备首次分别停在夹具旧仓库描述符和开发环境鼠标方法名；AppFlux 缺席组首次在 phase 20 的 `topPos` 反射失败。频率卡首次于 phase 32 触及 1,600 tick 上限；无线管理两次在 phase 3 把 FTB 全局 `(0,0)` 侧栏误作 LT 面板控件。各失败均保留，不计作通过。
- 频率卡 20 tick 轮询的第二轮仍在最后 phase 37 触及同一上限，最后管理页截图未完成；没有删除该项，而是进一步减少固定空等。无线管理第三轮识别出 `InvTweaksButtonSort` 使用约 `-1.4e9` 的坐标隐藏；夹具区别全局工具条与 LT 面板，不改产品布局来迁就外部控件。
- JEI 服务专项走真实 EMI 浏览界面和 QA 驱动的 JEI 按钮／服务。它没有执行原始 JEMI `showTypes`；此前 JEI `minecraft:furnace` 与 EMI `minecraft:smelting` 分类 UID 差异的失败单独保留，不能称为原始 JEI API 或 JEI-only 界面通过。
- 本轮两版 JEI 服务专项各在 **5／20** 条 PASS 后停止：三个实际 EMI 分类页面已打开，但 JEI 运行时没有注册 `RecipeTypes.SMELTING`（UID `minecraft:furnace`）。其余 15 项转移／元数据／服务端槽位断言未完成，没有该组完成截图。
- 两版远程设备组均到达 **phase 49**，此前 20 张原生菜单／跨维度往返截图已归档；最后要求原生 JEI `RecipesGui`，实际 GTO 使用 JEMI／EMI，故该组仍判失败，JEI 布局高度／缓存／收缩断言未执行。不把原生菜单子流程成功计为整组通过。
- 首次无线管理完整行为收据通过后，截图复查仍发现黑／品红背景，不能据此声称视觉验收通过。四张 PNG 恢复后以新标签和新存档补测；原带缺失纹理的截图和行为收据作为历史记录保留。
- Dev25 额外 AppFlux `1.3.7` 的 final `hashCode` 外部 ABI 冲突仍按原记录保留；本轮不扩大可选模组版本声明。
- 旧版服务端 51／51、真实 FX 和三个独立 JVM 的恢复结果仍归属[原 Core `.3` 配套验收](GTO_CORE_GTO3_ACCEPTANCE_2026-10-31.md)的制品；异步节点保存见[该专项记录](GTO_ASYNC_NODE_SERIALIZATION_2026-11-01.md)。本轮不把 UI 渲染或内存夹具计为重新执行完整服务端及磁盘恢复。

完整依赖清单、冻结 JAR、JUnit XML、失败与成功日志、逐组收据和 Minecraft 渲染截图
保存在 `D:/CodexTaskData/ae2lt-gto-graphics-multiplayer-20261101`，不加入源码提交。
未修改其他项目或 `AGENTS.md`；PR 继续保留草稿状态，不自动合并或发布。

## 本轮改动文件（22 个）

- [GTO_GRAPHICS_MULTIPLAYER_2026-11-01.md](GTO_GRAPHICS_MULTIPLAYER_2026-11-01.md)
- [build.gradle](build.gradle)
- [GtoCraftConfirmClientQA.java](src/gtoQa/java/qa/GtoCraftConfirmClientQA.java)
- [OmniversalProcessingClientProbe.java](src/jdb/java/com/moakiee/ae2lt/client/tianshu/OmniversalProcessingClientProbe.java)
- [BigTerminalClientProbe.java](src/jdb/java/com/moakiee/ae2lt/debug/BigTerminalClientProbe.java)
- [JeiCompatibilityClientProbe.java](src/jdb/java/com/moakiee/ae2lt/debug/JeiCompatibilityClientProbe.java)
- [OmniversalTerminalClientProbe.java](src/jdb/java/com/moakiee/ae2lt/debug/OmniversalTerminalClientProbe.java)
- [PigmeeIpnClientProbe.java](src/jdb/java/com/moakiee/ae2lt/debug/PigmeeIpnClientProbe.java)
- [FrequencyCardClientProbe.java](src/jdb/java/com/moakiee/ae2lt/logic/energy/FrequencyCardClientProbe.java)
- [NoAppFluxClientProbe.java](src/jdb/java/com/moakiee/ae2lt/logic/energy/NoAppFluxClientProbe.java)
- [RemoteDeviceClientProbe.java](src/jdb/java/com/moakiee/ae2lt/logic/energy/RemoteDeviceClientProbe.java)
- [WirelessEnergyMenuClientProbe.java](src/jdb/java/com/moakiee/ae2lt/logic/energy/WirelessEnergyMenuClientProbe.java)
- [GtoEaepCraftConfirmSupport.java](src/main/java/com/moakiee/ae2lt/compat/gto/GtoEaepCraftConfirmSupport.java)
- [GtoEaepMixinExtension.java](src/main/java/com/moakiee/ae2lt/compat/gto/GtoEaepMixinExtension.java)
- [GtoUselessJeiMixinExtension.java](src/main/java/com/moakiee/ae2lt/compat/gto/GtoUselessJeiMixinExtension.java)
- [GtoUselessJeiTransform.java](src/main/java/com/moakiee/ae2lt/compat/gto/GtoUselessJeiTransform.java)
- [wireless_network_management.png](src/main/resources/assets/ae2lt/textures/gui/wireless_network_management.png)
- [wireless_overloaded_form.png](src/main/resources/assets/ae2lt/textures/gui/wireless_overloaded_form.png)
- [wireless_overloaded_list.png](src/main/resources/assets/ae2lt/textures/gui/wireless_overloaded_list.png)
- [wireless_overloaded_selection.png](src/main/resources/assets/ae2lt/textures/gui/wireless_overloaded_selection.png)
- [GtoEaepClientMixinExtensionTest.java](src/test/java/com/moakiee/ae2lt/compat/gto/GtoEaepClientMixinExtensionTest.java)
- [GtoUselessJeiTransformTest.java](src/test/java/com/moakiee/ae2lt/compat/gto/GtoUselessJeiTransformTest.java)
