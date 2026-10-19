# GTO 兼容性补充审计 · 2026-10-19

当前锁定的 GTO 环境中，初次补充审计没有发现新增的 LT 外部成员签名缺失、抽象方法缺失或 Mixin 目标加载失败，真实客户端配方转移断言通过。后续复查修复了组装室配方缓存失效问题，并完成 9 项专用服配方测试；见下方后续修复记录。

## 环境

沿用 [实机测试记录](GTO_REAL_TEST_2026-10-19.md) 的隔离实例：Minecraft 1.20.1、Java 21、Forge 47.4.20、GTO 0.6.0-dev15 快照 `3650c7d330ade1b744ade0bc303fe5111eac98fa`、GTOCore 26.10.5 内嵌 AE2-GTO 15.2610.8、TB 2.0.3-gto.1、LT 2.1.2-gto.1、EMI 1.1.24、JEI 15.59.0.212。实际模组列表还确认加载了 **AE2WTLib 15.3.3-forge**，不能把它列入本实例未安装的附属。

初次补充审计的 LT SHA-256 为 `be2c648c498fd00407c854aeabe42953dd8f859ac124ec471e3c2093179a1c93`。该次客户端使用正式 LT 和单独的 QA 启动模组。后续修复制品的哈希见下文。

## 新增检查

| 检查 | 结果 | 含义 |
| --- | --- | --- |
| 生产 JAR 外部字段/方法引用 | 5202 处引用、484 个外部类；已存在的目标类中缺失成员为 0 | 按完整 JVM 描述符解析，沿父类和接口查找，包含运行实例的嵌套 JAR 与 JDK 类 |
| 未解析的可选引用 | 37 处，目标类均未安装 | 来自 AdvancedAE、AppFlux 与 AE2AddonLib；不能记成 37 个已确认的运行错误 |
| 具体类的抽象方法扫描 | 1866 个具体类，未发现缺失实现 | 排除 Mixin、接口和抽象类，检查运行依赖的类层次签名 |
| 真实客户端目标加载 | 93 条启用的 Mixin/目标配对，73 个不同目标类，0 失败 | 读取 LT 的 4 份 Mixin 配置，遵循其插件条件，再通过实际 ModLauncher 类加载器加载目标 |
| 条件跳过 | 8 条 | 缺少可选模组或 `@Pseudo` 目标，不计作通过的集成 |
| JEI 转移行为 | 通过 | 模拟点击保持返回值且清除临时元数据；非原生配方页环境仍执行成功回调；无线补料注入符合 AE2WTLib 加载状态 |
| EMI 转移处理器 | 通过 | 实际变换后的类包含 LT 的转移入口与闭环模式恢复处理器 |
| 客户端回归 | 通过 | 进入世界、真实渲染、精确终端数量作用域与界面变换；客户端正常退出，进程退出码 0 |
| QA 构建 | 通过 | `gtoQaJar` 编译、打包及重混淆完成；`git diff --check` 无空白错误 |

37 处可选引用的入口也检查了缺席条件：AppFlux 桥在依赖缺席时返回默认值；AdvancedAE 配方目录、JEI 配方分类受加载状态控制，天枢高级样板按钮受 `AdvancedAECompat.canEncode()` 控制。没有安装这些附属来验证其存在时的行为。

客户端明确跳过的项目为：ExtendedAE Plus 超级矩阵的伪目标、AppFlux 能量分配、Mob Grinding Utils 传送带、AdvancedAE CPU 的访问器与逻辑、NeoECO CPU、Inventory Profiles Next 点击器、Mekanism 激光。它们仍不属于本次通过范围。

## 保留的问题与限制

- 外置 AE2-GTO 15.2610.10 与 GTOCore 26.10.5 的 `CreativeCellInventoryMixin` 字段类型不匹配仍是已知不支持的组合；继续使用原包内嵌 15.2610.8。
- 默认 EMI/JEMI 环境通过；不代表移除 EMI 后的原生 JEI 独立配置已经通过启动和完整功能测试。
- 本次客户端日志仍有第三方缺失可选目标、资源枚举、GuideME 模型、Ad Astra globe ID 及关闭窗口时 ModernUI 线程警告。没有将整个整合包判为无警告，也没有改动这些第三方模组。
- LT 配方扫描器仍提示 7 个配方的输入可由同池其他配方输入组合覆盖，涉及电磁轨道炮、两种炮模块、天枢放大单元、一级存储组件、收集器及模拟室。扫描条件会将其他配方输入放大 8192 倍后组合匹配，这不等于配方 ID 重复或单份输入完全相同。后续已逐项验证单份输入，并验证两种指定混合投料，均通过；不代表任意批量比例、雷电不足、堵塞输出及全部配方组合均已验证。
- 之前 QuickPlay 的启动时序失败、未安装附属、长期多人压力、旧存档迁移仍保留原实机记录中的验证边界。
- 静态扫描只检查上述依赖前缀的成员与类层次签名，不证明所有反射调用、访问权限或业务逻辑。目标类成功加载也不能证明所有 `require=0` 注入都命中；关键 JEI/EMI 处理器另有断言，其余功能以原实机记录的用例覆盖为准。

## 后续修复：组装室配方缓存

复现触发条件：在同一 `RecipeManager`、同一配方映射中替换配方，保持 ID、优先级、输入条目数和输入总量不变，仅修改输入材料或输出。原缓存指纹不包含配方对象身份，因而返回旧配方；另一个回归用例证明不同 ID 的哈希相同也可能漏掉替换。

`LightningAssemblyRecipeService` 现在保留不可变配方快照，以 ID 到实际配方对象的映射比较缓存是否过期，替换后重新排序。回归用例先在旧实现中得到 2 项失败，再在修复后通过。普通重载产生新映射的路径继续有效；本次缺陷复现专门针对原映射内替换，不把所有 `/reload` 宣称为原先都会出错。

| 验证 | 结果 |
| --- | --- |
| 完整 Gradle `check` | 1491 项，1486 通过、5 跳过，0 失败/错误 |
| 新增缓存回归 | 2/2 通过：同 ID 内容替换、不同 ID 哈希碰撞 |
| 正式包专用服 `recipes` | 9/9 通过，启动 CPU/STORAGE/PLANNER 检查也通过 |
| 7 个提示配方 | 单份输入均选择预期配方；雷击转化检查实际产物和输入消耗 |
| 模块混投 | 光束模块与核心模块同时满足输入时，选择更具体的光束模块配方 |
| 雷击混投 | 一份模拟室加一份收集器输入，实际雷击处理得到两种预期产物，无输入残留或其他产物 |
| 打包 | `jarJar`、`gtoQaJar` 及重混淆通过；正式包不含 `qa/` 测试类 |

测试使用正式 LT JAR，在本机隔离专用服的新世界 `qa-world-recipes-20261019` 运行，端口 `25587`；没有替换 GTO 的共享配方表。QA 模组新增 `-Dae2lt.gtoQaSuite=recipes`，源码为 `src/gtoQa/java/qa/GtoRecipeGameTests.java`。

修复后的正式包：`build/libs/ae2lt-forge-1.20.1-2.1.2-gto.1.jar`，SHA-256 为 `9e90bf5ba86c0dca0a5248a87ffc8032657176b7a86095972962687d31fb23e2`。旧实机报告中的大数终端、EMI 交互和联机结果对应修复前制品；本次正式制品验证了完整 `check` 与新增专用服用例。

证据目录中的 `recipe-cache-before.log`、`recipe-cache-before.xml` 保留旧实现失败；`recipe-fix-build.log`、`recipe-cache-after.xml` 和 `server-recipe-fix-console.log` 记录修复后通过，`recipe-fix-results.json` 保存统计、用例及新制品哈希。

## 初次补充审计的复现与证据

新增测试源码：`src/gtoQa/java/qa/GtoMixinAudit.java`、`GtoRecipeTransferAudit.java`。在隔离客户端部署完成重混淆的 `gto-compat-qa.jar`，配合正式 LT，设置 `-Dae2lt.gtoQaSuite=none -Dae2lt.gtoMixinAudit=true` 后进入测试世界；检查结束会自动退出。不要在用户正常存档运行 QA。

本机证据目录为 `D:\codex-builds\ae2lt-gto-realtest-20261019`：

- `linkage_audit.py`、`interface_audit.py`：本机静态审计脚本，输入为正式 JAR 和隔离客户端依赖。
- `linkage-audit.json`、`interface-audit.json`：静态审计明细。
- `compat-audit-build2.log`：最后一次 QA 构建成功日志。
- `client-mixin-audit-console.log`：本轮最后一次客户端日志，包含 `GTO_RECIPE_TRANSFER_AUDIT PASS`、`GTO_MIXIN_AUDIT PASS` 和 `GTO_CLIENT_QA PASS`。
- `client/mixin-audit-result.txt`：完整的已加载目标、跳过项目与失败列表。
- `compatibility-audit-results.json`：本轮结果与正式/QA 制品哈希。前次 `artifact-hashes-final.json` 中的 QA 哈希对应前次探针版本，应以本轮文件为准。
