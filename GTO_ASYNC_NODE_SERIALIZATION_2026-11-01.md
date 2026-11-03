# Forge 1.20.1 异步 AE 节点保存修复的 GTO 移植

本轮指定范围通过：能源 JUnit 76／76、双版新增 GameTest 各 3／3、
Dev23 既有无线回归 19／19，以及构建和正式包核验。旧实现的两组各 3 项失败
作为负向对照单独保留，不计入修复版通过数。

## 来源与范围

来源为普通 `1.20.1` 的提交
[`2df9c5b5d4c1f8efb6567b4bc01f4b1edf9c59f7`](https://github.com/AE2-Lightning-Tech-Reborn/AE2-Lightning-Tech-Reborn/commit/2df9c5b5d4c1f8efb6567b4bc01f4b1edf9c59f7)，
即此前已移植基线 `59abcdba32cf0947cb25eb5afd3ad943d1f58e14` 之后的一个提交。
保留 LT `2.1.3-gto.1`、Core `2.0.4-gto.3` 及既有 GTO／NeoECO 适配；
不引入 `main`／`alpha` 的红石控制、GUI 配色或酿造功能，不使用 GTL。

本次改动共六个文件：`WirelessEnergyGridService.java`、上游新增的
`WirelessEnergyNodeSerializationGameTests.java`、
`scripts/wireless-management-test.init.gradle`、`gradle/gto-qa.gradle`、
`src/gtoQa/java/qa/GtoCompatQA.java` 和本文。
前三个文件的 Git 规范化内容逐一与上游固定提交一致；另两处为 GTO 独立 QA 的
编译包含项及 `upstream-node-serialization` 测试入口。

## 行为

- 节点 claim 表继续以对象身份为键，增加同步包装，允许保存线程读取现有 UUID。
- 异步 `saveNodeData` 只导出现有 claim；不创建 claim、不访问能源账本，
  不分配／登记／导出当前 tick 的迁移预算。
- 主线程保存继续登记并传递预算别名，保留拆分、重复合并、FE 与访问次数上限。
- `WirelessEnergyLedger.get` 的主线程校验保留；账本、余额及存档格式未改变。

## 固定制品与环境

| 制品 | SHA-256 |
| --- | --- |
| LT 正式包 | `565b36c223ed4bb4652fc9587ae462a789f23cbcb6b619cc06fa924ef11b93c0` |
| LT 附加夹具包 | `7a41ec6389c30043f4a7dda1c050556162e69f5b928f334d260c55fee7b7ee39` |
| 独立 QA | `e09e2f448cb935d907ee0b0e067ef332d47348b66a66eab978c3d86488b5989a` |
| Core `2.0.4-gto.3` | `1ebff68ab9a2c7e041b8ca8338acb1a00aa9f5eacfa6604b4f55f2cd04be85a9` |

实际环境为 Minecraft `1.20.1`、Forge `47.4.20`、Microsoft OpenJDK `21.0.10`。
Dev23 使用 GTOCore `26.10.10`／AE2 `15.2610.31`；Dev25 使用
GTOCore `26.10.18`／AE2 `15.2610.40`。两版均保留真实 GTM、LDLib、
NeoECO `20.5.2-gto.1` 及 EAEP `1.6.3`。Dev23 安装 AppFlux
`1.20-1.3.7-forge`；Dev25 保持已验收的无 AppFlux 组合。

相对上一轮冻结正式包，2,365 个生产类中只有
`WirelessEnergyGridService.class` 改变。负向对照只将本次候选的这一个类替换为
上一轮冻结字节码，其他类、夹具和依赖保持一致；该对照包仅用于重现，不作为发行包。

## 验证

| 检查 | 本轮结果 |
| --- | --- |
| 能源 JUnit，`com.moakiee.ae2lt.logic.energy.*` | **76 通过，0 失败、0 错误、0 跳过** |
| `check build gtoQaLtJar gtoQaJar` | 通过；`test` 仅运行上述筛选范围，不计为全量 JUnit |
| 正式包与附加夹具包核验 | 通过：全部 2,365 个生产类逐字节一致，正式包不含新夹具；1,438 个 JSON、ZIP／嵌套包、Java 21、依赖、许可证及 Mixin／refmap 均通过 |
| Dev23 旧实现负向对照 | 3 项均复现 `Server thread required`，`failed=3 startupFailures=0`；351.1 秒，不计为通过 |
| Dev25 旧实现负向对照 | 3 项均复现相同线程校验错误，`failed=3 startupFailures=0`；350.9 秒，不计为通过 |
| Dev23 修复版序列化 GameTest | **3／3 通过**，`failed=0 startupFailures=0`；136.5 秒 |
| Dev25 修复版序列化 GameTest | **3／3 通过**，同样无失败／启动错误；134.2 秒 |
| Dev23 既有无线 GameTest | **19／19 通过**，`failed=0 startupFailures=0`；63.9 秒 |

上游三项 GameTest 保留原断言及工作线程 5 秒上限，分别验证：

1. 普通节点异步保存稳定且不创建能源归属／迁移预算，并验证账本主线程限制仍生效。
2. 已有 claim 的 FE、AE、隔离余额与待确认提款在异步快照及节点重建后保持。
3. 64 次异步保存不改动已有迁移别名，主线程拆分／合并仍共享本 tick 的 FE 和访问预算，下一 tick 正常补充。

构建首次停在 ForgeGradle 的 HTTPS 环境连通探测，尚未编译；保留该轮诊断后，
以本次命令的 `--offline -Dnet.minecraftforge.gradle.check.certs=false` 使用缓存依赖构建成功，
没有更改仓库地址、全局配置或依赖校验策略。服务器 QA 沿用此前临时 init，
只排除无关且仍有 EMI 编译 ABI 问题的 `JeiCompatibilityClientProbe`。

本轮不重跑完整图形／多人矩阵；新增恢复检查为内存 NBT 快照及节点重建，
不记为跨 JVM 磁盘恢复。此前完整服务端及重启结果仍归属
[Core `.3` 配套验收](GTO_CORE_GTO3_ACCEPTANCE_2026-10-31.md)的旧哈希。

完整本轮构建日志、JUnit XML、制品、每项实机结果及对照记录保存在独立任务目录
`D:/CodexTaskData/ae2lt-gto-async-node-20261101`，不加入提交。
