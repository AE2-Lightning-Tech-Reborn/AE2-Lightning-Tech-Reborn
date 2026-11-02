# 最新 GTL 双包直接实跑回归（2026-11-02）

**本轮实跑范围通过；整包总状态仍为 `NOT_FULLY_ACCEPTED`。** 按用户“最新构建直接测试”的要求执行，没有再发起计划确认或八步审批。本轮没有修改生产源码、AGENTS.md 或其他项目；只新增本报告、更新历史验收报告，并在本工作区 `build/gtl-dual-direct-20261102/` 保存隔离验证文件。既有未提交的 PatternMigrationGameTests.java 前后 SHA256 相同，两个既有未跟踪目录保留。

## 实际受测双包

AE2LT 源快照为 `3f7b3fea5a643dbb30fb65e65a7a073dcbdd2bf3`，生产代码提交 `7a5ccc2a51072284fc0648946167ab92dee108ac`；二者间只有已提交验收文档差异。Core 由所属成员提供，源提交 `5ca4da079659b4cc89532cfcb0eef1402b882491`。版本号相同的历史包按哈希区分。本轮运行全程使用以下原始完整重混淆包：

| 制品 | 字节数 | SHA256 |
| --- | ---: | --- |
| AE2LT 2.1.3-beta1 | 7618385 | `94c0fcad3e075bb060d9c14ee0bdb991f8f981b5aea91e298faa25d870fe0c5f` |
| Core 2.0.4 | 5470188 | `1bd40221c927b9fc5b26dd1e08fc1ba3f3feb5f7c701e9d23e208793dde4868c` |

AE2LT 冻结发行包通过 Java 17、UTF-8 元数据、JSON/TOML、Mixin/refmap、嵌套依赖、许可证/署名、开发夹具排除及既有 NeoECO 排除边界审计。未恢复被排除的适配、注册、依赖、配方或测试夹具；默认整合包中的历史组件按原清单保留。隔离快照重建与冻结包的 41 个原始条目差异全部核清：构建时间、资源换行及一个 javac 临时局部变量名；ASM 去除调试属性后的执行类一致，资源归一化后内容一致，证据见 artifact-equivalence.json。

## 本轮实际结果

运行环境为 Minecraft 1.20.1、Java 17.0.20、Forge 47.4.16、AE2 15.4.10、GTLCore 1.2.3.2-fix1、GTCEu 1.4.4。开发编译用 Forge 47.1.47/official 1.20.1 映射；运行验证单独使用上述生产环境。默认 Radium 0.12.4 未关闭 NBT 规则，配置 SHA256 为 `b87ee52fdc722ce1497e7ff55fdbd0660fc9df40c8f2f8167465cab55a388f0d`。

| 检查 | 本轮结果 |
| --- | --- |
| AE2LT 完整 JUnit | 1957 通过，失败/错误/跳过均为 0；真实可选 API 门控已开启 |
| 批处理模型 | 6/6；不与完整 JUnit 重复合并计数 |
| compileJava / check / build | PASS；原始 Core→映射包 SHA256 可追踪 |
| Core 依赖解析 | 编译、测试运行时、普通运行时、jdb 夹具编译四套类路径各只有最新 Core；映射 SHA256 `770a2ec55327902b69a03102b75c2f1cbbfa8b1f80b32bd7ed0c92ea8aed23b6` |
| 默认专服冷启 / 同世界重启 | PASS / PASS；PID 11520 → 35368；旧 JVM 退出后再启动，保存退出码均为 0 |
| 默认客户端冷启 / 同世界重启 | PASS_CHECKPOINT / PASS；PID 23408 → 99612；保存退出码均为 0 |
| 大数存储、身份与 NBT | 上述四个 JVM 均通过；四键 2147483652/333/444/555、普通/NBT/两份真实可序列化 Forge capability 载体隔离、模拟只读及实际提取/回存守恒 |
| 真实 ElementalCraft 场景 | 11/11，PID 44412，正常保存退出 0 |
| 原四组长请求 | 4/4，只读规划与摘要；正常保存退出 0 |
| 目标异常 | 六次最终成功 JVM 的 `Server thread required`、LDLib `asyncThreadLogic error`、相关 ICCE/链接或 Mixin apply failed 未出现 |

客户端使用固定离线验证身份及原始 192 文件世界种子副本，实际进入世界后执行原生/V2 有料合成、取消材料守恒、GTL long 菜单/摘要及空闲稳定性探针，并在新 JVM 读取同一保存世界继续验证。存储辅助模组给纸附加真实 Forge 能量 capability；它验证实际 Forge 身份与序列化契约，不代表验收某台真实 GTL 机器。历史探针 cellId 字段仍以字符串 getter 读取 UUID 而为空，恢复判据为实际单元及四个键的完整数量。

## 真实 ElementalCraft 与定向行为

兼容场景在默认专服基础上安装真实 ElementalCraft 6.0.1、DPAnvil 5.0.1，使用原整包 GTMAdvancedHatch 内嵌 Patchouli 84.1，不额外升级依赖。其 JAR/依赖全哈希、来源及默认场景差异均留证。没有安装人工 ItemStack 接口 super 替身 Mixin。最终导出 ItemStack 的两处 invokespecial 均保持 IForgeItemStack 接口 owner；实际附魔单值/映射调用、原生箱子能力及 EJECT 在线/离线/面限制/防递归/注销断言通过。

| 原用例（成功回调） | 结果 |
| --- | --- |
| asyncnodesnapshotrestoresexistingfeaeanduncertainbalances | PASS |
| asyncsavepreservessameticksplitmergeenergyandvisitbudgets | PASS |
| exclusivev2surviveswithoutatianshucpu | PASS |
| exclusivevanillasurviveswithoutenumeratingcpus | PASS |
| feasiblealternativereassignmentremainsavailable | PASS |
| interfacesuperenchantmentsandnativecapabilitiesremainvalid | PASS |
| jointdemandabovelongdoesnotwrap | PASS |
| offlinepoliciespreserveitemandfluidrejection | PASS |
| onlineprojectionandrecursivefallbackkeepownership | PASS |
| ordinaryaenodeserializesoffthreadwithoutcreatingenergystate | PASS |
| sharedalternativeswaitforstockwithoutconsumingit | PASS |

三个异步保存用例保留原类字节和原断言；为避免生产模组 JPMS 同包冲突，将其作为辅助包二进制资源，通过原模块 private lookup 定义后执行，未补丁生产包。首轮同包模块失败及第二轮原版测试命令准备失败（均 0 项回调）保留于 real-ec-r1/r2，不计入通过；R2 保存现场后只停止了已核验归属的 PID 86000。第三轮补充 pack.mcmeta，独立入口检查模板、捕获准备异常，并实际取得上述 11 个成功回调。夹具许可证与两项目署名材料均保留。

## 四组长请求

沿用原探针 SHA256 `280d56ee38a258abe1b4009a1d4cd7b0fc3a7a5ff35e56041ffa396915ff3d3b`、真实 1:1 石头→石按钮配方、数量/摘要/Crafting Tree 断言与原超时。原生和 V2 分别请求 2147483652、9223372036854775807，四组实际 GTL 样板执行次数均等于请求，库存不变。

V2 极值保持 simulation=true、previewOnly=true、incomplete=false，stored/used/crafting/finalOutput 均为 Long.MAX_VALUE，missing=0；精确成本 27670116110564327437，long 投影 Long.MAX_VALUE。未提交极值执行请求。

## 未关闭事项与证据

历史导出 >50 ms 比例 2/1200 = **0.1667% > 0.1%** 仍为 FAIL，本轮未重跑完整性能验收。有限供能逐项能量对账、真实 GTL 催化剂/膨胀样板/EAEP 批量设备执行取消、客户端返回按钮、旧 Core 同整包性能基线及 Useless 新公开 API/真实机器仍未验收；不据本轮结果宣称整包通过或提速比例。

Core 所属成员交接的 JUnit 1728 通过/2 可选跳过、EJECT 3/3、GTL 14/14 和构建/发行检查另存 core-owner-evidence；它们是提供方证据，不冒称本成员本轮重跑。原八步流程的两次正式只读审查与调度闭环未执行，本报告仅结清用户最新要求的直接实跑范围，不把旧矩阵所有关口填成 PASS。

独立专服仍记录 Rhino 对 GTItems 扫描声明方法时访问客户端类的非致命诊断。本轮具体为 ItemPropertyFunction 的 NoClassDefFoundError；旧成功运行在同一调用点报告 ClientLevel 的 invalid-dist RuntimeException，异常文本并不相同。该整包扫描诊断及新旧对照在 result.json 单列，不把它归为 Core/AE2LT API 断言失败，也不声称整包日志干净。

本地证据根目录：`build/gtl-dual-direct-20261102/`。入口为 result.json、test-summary.json、adaptiveBatchStress-summary.json、core-classpath-audit.json、artifact-equivalence.json、audit-frozen/、cold-r1.json、restart-r1.json、qa-runs/real-ec-r3/、native-io-runs/long-r1/、client-launch/runs/cold-r1/ 与 restart-r1/、四份 dedicated-storage-*.json。运行前后 JAR/配置哈希、进程标识、保存退出、源清单及原有文件保全均可追踪；失败 attempt 原样保留。

完整双包及各自 SHA256、pair.json、result.json 和 evidence-index.json 在 [delivery](../build/gtl-dual-direct-20261102/delivery/)；交付字节与最终受测包逐一相同。本轮开发环境日志和辅助文件留在忽略的 build 目录，不加入 Git 提交；报告整理为本地汇总提交，未推送。
