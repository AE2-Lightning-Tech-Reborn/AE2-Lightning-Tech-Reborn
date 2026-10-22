# Forge 1.20.1 GTL 再次全面验收（2026-10-22）

针对本地汇总提交 `968ebe41` 导出独立源码副本重新构建和执行；初始化时与工作区源码规范换行后完全一致。环境为 Minecraft 1.20.1、Forge 47.1.47、AE2 15.4.10、Java 17。测试世界、构建和证据全部位于 D 盘。保留 GTL，未引入 main/alpha 新功能，未恢复 NeoECO，未修改其他项目源码或 AGENTS.md。

本轮修复了一个提交完整性问题：`*.txt` 忽略规则漏掉现有的 `src/main/resources/META-INF/recipesearch-attribution.txt`，导致从汇总提交独立构建的 JAR 缺少该署名。现已补入文件并增加精确的忽略例外。修正了两个 EAEP 测试只判断模组存在、没有判断超级矩阵 API 是否存在的前提错误。合成与催化相关的可选 GameTest 提前成功返回现在输出未执行标记，旧报告的 IO 和 crafting 实际覆盖数字已更正。

## 本轮实际执行

- 完整 JUnit：1672 通过，0 失败、0 错误、0 跳过，含实际 EAEP JAR 的四项契约。
- 512 目标自适应批处理压力模型：6 通过。
- 发布脚本单元：12 通过；基准运行器单元：1 通过；发布 Maven 原始/映射坐标检查通过。
- 从独立源码完整编译、重映射、打包通过；补入署名后再次打包并重新审计通过。

| 服务端分组 | 引擎通过 | 条件未满足而未执行 | 实际执行 |
| --- | ---: | ---: | ---: |
| minimum | 51 | 0 | 51 |
| io | 74 | 4 | 70 |
| exact | 16 | 0 | 16 |
| machines | 26 | 2 | 24 |
| backport | 128 | 0 | 128 |
| crafting-eaep | 50 | 8 | 42 |
| extras | 33 | 0 | 33 |
| migration-eaep | 13 | 1 | 12 |
| pigmee-storage | 10 | 0 | 10 |
| kubejs-recipes | 1 | 0 | 1 |

minimum 关闭了开发配置可关闭的可选运行依赖，保留必需依赖和常驻的 Glodium 库。IO 的四项未执行为未启用的专项性能场景；本轮不提供这些场景的性能结论。机器组的两项未执行针对 `mekanism_extras:infinite_pressurised_reacting_factory`。crafting-eaep 安装了真实 EAEP 1.5.5，验证该版本缺少超级矩阵 API 时不注册批处理适配器、创建适配器返回空，保留普通供应器路径；这不是超级矩阵批处理实测。未执行项包括 AppGen、未启用的 KubeJS 夹具，以及缺少配方的 Oritech、Just Dire Things、AE2CS 催化场景。可选配方边界的其他断言仍执行。

KubeJS 使用隔离脚本世界单独补测：修改派生配方能耗、删除派生配方、删除手动处理器配方，并在真实资源重载后再次断言。修复了脚本夹具中 Rhino 将数值选入字符串重载的问题；删除断言现在检查本模组的原生配方目录，并确认 AdvancedAE 自己的同产物反应配方仍保留。migration-eaep 编译并执行实际样板迁移夹具（此前 exact 分组只执行精确合成夹具），EAEP 超级矩阵物理库存聚合因该版本没有对应 API 而明确未执行；pigmee-storage 使用实际 Farmer's Delight 1.3.4。分组包含部分相同基础用例，不能将表格简单相加当作唯一用例数。

实际 Forge 客户端安装 Tweakerge 0.1.5 与 MaFgLib，检查两次真实自由相机启停及水平/垂直移动、本体保护、自主移动、同 UUID 代理及 RemotePlayer，探针 `Tweakerge=true` 且 PASS。JEI 三版本和供应器 UI 的客户端结果沿用上一轮同一生产源码的已通过证据，本轮没有将其重计为新执行。

## 已发现的可选组合不兼容

尝试安装 Mekanism Extras 1.5.0（官方开发构件，SHA-256 已与 GitHub 发布校验值核对）和 MoreMachine 1.2.1 时，两个模组均在构造阶段失败：`NoSuchMethodException: MekanismExtras.<init>()` / `Mekmm.<init>()`。它们使用带 `FMLJavaModLoadingContext` 参数的构造方法，而固定的 Forge 47.1.47 尝试无参构造。该组合未进入测试阶段，明确记录失败；没有修改第三方构件或更换本项目的加载器来掩盖问题。随后不安装这两个可选模组，重新执行机器组并通过其余用例。无限反应工厂两项仍未获得通过结论。

EAEP 首次补测的旧夹具要求缺失的矩阵 API 注册适配器而失败。检查实际 JAR 与核心公开接口后修正测试前提，重跑确认普通供应器降级；保留首次失败证据。当前 EAEP 1.5.5 不包含超级矩阵 API，相应批处理、矩阵物理库存聚合没有获得通过结论。

KubeJS 补测最初遇到离线传递依赖解析失败，改用已缓存的映射构件；新夹具的模板命名空间配置曾造成零项测试执行，门禁拒绝该次运行。脚本数值与配方范围断言修正后，实际执行一项完整资源重载回归并通过，保留全部早期失败日志。

AppGen 查到的公开发行版本为 NeoForge，未找到可用于当前环境的 Forge 1.20.1 构件；其实际处理器派生场景保持未验证。Oritech、Just Dire Things、AE2CS 的可选配方实际处理也未验证。自动 GameTest、客户端探针及压力模型不替代任意整合包或长期生产存档的运行验证。

## 发布包与源码完整性

发布包包含 2273 个 Java 17 class、1450 个可解析 JSON；refmap 非空、JarJar 元数据及许可证和全部声明的署名齐全，无重复 ZIP 条目、开发夹具或日志；源码和产物未检测到 NeoECO。与上一轮产物对照，除修复的署名文件外，资源差异为换行格式，两个 class 差异为 javac 临时变量调试名称，另有构建时间戳变化。

- AE2LT：`ae2lt-forge-1.20.1-gtl-2.1.2-beta2.jar`，SHA-256 `e7fe1c3160225c2761ba51b7fb9accb7203278b7180916ffbe74caf96c9c28ec`。
- 配套无 NeoECO 核心：`thunderbolt-forge-1.20.1-2.0.3-beta3.jar`，SHA-256 `b4b8e379aeb1ea1971062b09daa0ff677bebbbc02c33c4fccbfe606ec395caff`。

完整证据、运行参数、JUnit XML、初始失败记录、客户端结果及构件位于本地 `D:\CodexBuilds\gtl-1201-expanded-validation-20261022`。测试使用导出源码和隔离构建初始化脚本；依赖固定为既有的无 NeoECO 核心编译构件。开发工作区的 HEAD 和真实 index 保持原状；最终变更整理到本地 `1.20.1_GTL` 的单个汇总提交，未推送。
