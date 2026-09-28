# 过载工厂复用反应仓配方

过载工厂保留 LT 本体配方和专门设计的强化配方。已存在于 AdvancedAE 反应仓中的加工配方，改为读取游戏实际加载的 `advanced_ae:reaction` 配方。适配按配方类型工作，也包含其他模组或数据包注册到反应仓的配方。

## 执行与显示

- 保留原配方 ID、物品 Ingredient（含标签、替代项和组件要求）、数量、流体 Ingredient、产物组件及单次 FE。
- 每次原配方操作额外消耗 1 个高压闪电。一次原方产出 64 个物品仍消耗 1 个；执行 16 次则消耗 16 个。执行速度和并行 FE 缩放继续使用工厂规则。
- 执行视图只存在于工厂目录，不注册进 `RecipeManager`，不再同步或展示一份 LT 配方。存档锁定原配方 ID，恢复时从当前目录重新解析。
- JEI 将过载工厂加入原反应仓分类的加工机器列表；工厂进度区域可打开本体配方和反应仓配方。EMI 同样复用原分类。工厂提示说明额外闪电消耗，原反应仓的配方页面保持原意。
- 配方管理器不同，或配方新增、替换、删除时，执行目录重新构建。被数据包移除的原方没有静态副本兜底；原模组的条件加载结果同样生效。
- 目前只适配 AdvancedAE 的标准反应仓配方类。超过 9 种物品输入、无有效产物、数量越界或能耗小于工厂最低值 5 FE 等无法表达的配方会跳过并记录原因。

## 静态配方迁移

移除以下 22 个 `ae2lt:overload_processing/` 配方；对应的原方存在时才可加工。未安装 AdvancedAE 时，不再保留这些反应仓加工方法。

| 原 LT 路径 | 原配方 ID |
| --- | --- |
| `aae_quantum_alloy` | `advanced_ae:quantum_alloy` |
| `aae_quantum_alloy_plate` | `advanced_ae:quantum_alloy_plate` |
| `aae_quantum_infusion` | `advanced_ae:quantum_infusion` |
| `aae_shattered_singularity` | `advanced_ae:shatteredsingularity` |
| `ae2_certus_quartz_crystal` | `advanced_ae:quartzcrystal` |
| `ae2_charged_certus_quartz_crystal` | `advanced_ae:certuscharger` |
| `ae2_fluix_crystal_from_dust` | `advanced_ae:fluixcrystalfromdust` |
| `ae2_fluix_crystal_from_redstone` | `advanced_ae:fluixcrystals` |
| `ae2_singularity` | `advanced_ae:singularity` |
| `appflux_charged_redstone` | `advanced_ae:chargedredstone` |
| `appflux_redstone_crystal` | `advanced_ae:redstonecrystal` |
| `eae_entro_crystal` | `advanced_ae:entrocrystal` |
| `eae_entro_ingot` | `advanced_ae:entroingot` |
| `mega_sky_bronze_ingot` | `advanced_ae:skybronze` |
| `mega_sky_steel_ingot` | `advanced_ae:skysteel` |
| `appgen_ember_crystal` | `appgen:reaction/ember_crystal` |
| `appgen_ember_crystal_duplicate` | `appgen:reaction/ember_crystal_duplicate` |
| `appgen_charged_ember_crystal` | `appgen:reaction/charged_ember_crystal` |
| `omni_charged_ender_ingot` | `ae2omnicells:reaction_chamber/charged_ender_ingot` |
| `omni_ender_ingot` | `ae2omnicells:reaction_chamber/ender_ingot` |
| `neoeco_energized_crystal` | `neoecoae:reaction_chamber/energized_crystal` |
| `neoeco_energized_fluix_crystal` | `neoecoae:reaction_chamber/energized_fluix_crystal` |

原 LT 副本中的少量材料标签与原方不同，迁移后一律以实际加载的原方为准。其余 30 条静态配方包括 12 条 LT 产物配方和 18 条独立加工/强化配方；不能仅凭产物所属模组判断它们是重复配方。

旧存档若正在执行已删除的 LT 副本，旧配方锁定将失效，机器重新选择原方。物品、流体和闪电原本在完成时才扣除；已投入的加工 FE 不迁移到新的配方锁定。

## 缺失处理器配方的自动推导

已有工厂配方优先。对于没有显式工厂配方的产物，只读取源配方 ID 的路径包含 `processor` 的 AE2 三槽 `PRESS` 压印配方；仅模组命名空间包含该词不算，其他三合一加工不会自动混入。再沿只有一个不消耗模板的 `INSCRIBE` 步骤向前解析原料。模板槽在上或下均可，支持连续多次压印和多个材料选择；中间压印步骤不要求 ID 包含 `processor`，也不限制模组名单。

原料若有标准工作台 2×2 / 3×3 压块配方，并有数量一致的解压配方，就使用对应块。槽位使用标签或具体物品都可以，只要实际材料满足每个槽位。没有可确认块配方的材料保留原物品及其 Ingredient；会消耗模板的压印步骤不会被跳过。

默认每批相当于执行最终压印配方 36 次，消耗 400000 FE 和 1 个高压闪电。例如 AppGen 起源处理器可推导为 9 个余烬块、4 个红石块、4 个硅块，产出 36 个处理器。若上游单次产出数量使材料不能整除，则增加到可整除的批量，并同比增加 FE 和闪电，不进行向下取整。递归有循环/深度和规模限制，超过可表达范围会停止展开或跳过并记录原因。

### KubeJS 的最终决定权

生成在 `RecipeManager.apply` 的 `HEAD` 完成：LT Mixin 优先级为 1088，避开默认 1000，并排在当前 KubeJS 2101 的 1100 之前。使用脚本验证初次加载和 `/reload` 的顺序。无需 KJS plugin，也不增加 KJS 编译依赖。

包装配方以 `ae2lt:derived/inscriber/<源命名空间>/<源路径>/<路线摘要>` 为 ID、`ae2lt:overload_processing` 为类型，在 KJS 执行前加入待加载 JSON。KJS 可以按 ID、类型或产物删除/替换这些配方，也可以修改材料和能耗。之后工厂和 JEI/EMI 只读取最终配方管理器，不会运行时补回被脚本删除的包装配方或已有手写配方。包装配方通过原生配方同步发送客户端，不在客户端再次推导。

生成器使用 NeoForge 当前这轮加载的条件上下文和待绑定标签，不提前改写全局标签。无法安全解析待绑定标签的自定义 Ingredient 保留原输入，不依据旧标签展开。KJS 在后续脚本中新加的压印配方不会再次触发推导，整合包作者可以直接添加所需工厂配方。

反应仓仍采用原分类和原配方引用；上述预先生成只针对原料已经展开的处理器批量配方。

## 验证

`AdvancedAeReactionAdapterTest` 覆盖源 ID、成分匹配对象、数量、产物组件、流体替代项、流体产物、并行闪电、同 ID 替换、删除和缓存隔离。

`runOverloadFactoryGameTestServer -Pae2ltOverloadFactoryTestsOnly=true` 在独立世界验证实际加载的反应仓配方、流体标签、工厂选方、并行、锁定存档和配方替换/删除。可通过既有 `ae2ltJdbProbeMods` 参数加载 AppGen，验证其原配方与原网络序列化。

Reborn `alpha` 验证通过 1294 项单元测试、包含 AppGen 和 KJS 的 11 项 GameTest，以及构建。GameTest 让真实工厂完成两次量子灌注，验证物品、水、FE 和 2 个高压闪电的扣除，以及 2000 mB 流体产出；还验证处理器批量加工和不含 `processor` 的三合一配方不会被包装。

客户端探针为 `runOverloadFactoryClient -Pae2ltOverloadFactoryTestsOnly=true`，在独立的 `run-overload-factory-client/saves/OverloadFactoryQA` 测试世界执行。附加 `-Pae2ltJeiOnlyForTest=true` 可单独验证 JEI；macOS 图形测试使用 `--no-daemon` 启动。探针核对原分类的机器入口、原配方数量和工厂配方数量，截图后退出。启用处理器测试资源和 KJS 测试脚本时，还检查客户端收到的包装配方为脚本修改后的 123456 FE，并打开该配方核对显示。

JEI 和 EMI 客户端验证均通过：当前隔离测试组合显示 19 条原反应仓配方和 22 条最终工厂配方，包含测试包装配方，没有复制反应仓条目。截图 `factory-inscriber-jei.png` 和 `factory-inscriber-emi.png` 位于 `run-overload-factory-client/`；界面显示脚本修改后的约 123.5k FE、1 个高压闪电，以及三种块材料各 4 个、输出 36 个。

处理器验证另加 `-Pae2ltInscriberFixture=true`，仅向开发测试资源中加入合成的三合一源配方，不进入发布 JAR。针对 KJS 的测试先将 `src/overloadTest/kubejs/inscriber_wrappers.js` 复制到隔离游戏目录的 `kubejs/server_scripts/`，再加 `-Pae2ltInscriberScriptTest=true`。测试会修改包装配方 FE、删除另一条包装配方、删除已有处理器配方，并验证真实工厂加工及 `/reload` 后的最终结果。`-Pae2ltNoKjsForTest=true` 可单独验证未安装 KJS 的加载路径。

Reborn 复验使用 Java 21、NeoForge 21.1.220 和 Thunderbolt Core Reborn 2.0.0（Minecraft 1.21.1）。本机 Maven 缓存含不匹配的 Thunderbolt 构建，测试通过临时 Gradle init 文件固定已核对的 1.21.1 JAR；未改变项目依赖声明。
