# 上游过载 IO 修复移植 · 2026-10-20

来源：[上游 main 的 7c76557f](https://github.com/AE2-Lightning-Tech-Reborn/AE2-Lightning-Tech-Reborn/commit/7c76557f302a74c5f089dcc95de321bafe200253)，修复过载 IO 重读空槽数据后坍缩矩阵加成残留。

当前 Forge/GTO 分支的 `OverloadedIOPortBlockEntity.loadTag(CompoundTag)` 也存在此问题：AE2 对空库存省略存档内容，读取时只覆盖存在的槽位。同一方块实体先加载有矩阵/过滤器的快照，再加载空槽快照时，旧物品和过滤规则可能残留。

移植后的加载过程先清空过滤器、矩阵库存，再按 Forge 1.20.1 的 API 读取 NBT，并重建过滤规则。生产改动与上游一致，保留本项目的无注册表参数签名。当前配方及配方断言已使用过载奇点，无需再次修改材料。

## 回归测试

移植上游的两项服务器 GameTest：

- 四种真实菜单操作取出矩阵：普通点击、Shift 点击、数字键交换、丢弃。检查服务器库存、服务器吞吐上限及菜单同步值全部恢复基础值。
- 同一实体先读取强化库存，再读取空库存：检查矩阵数量、单次转移上限、批次上限和过滤器槽位不残留旧状态。

测试适配为 Forge `FakePlayerFactory`、1.20.1 的 NBT 方法和 `List.get(0)`。完整过载 IO 测试组共 40 项，通过 `-Dae2lt.gtoQaSuite=io` 运行。使用本隔离 GTO 实例已验证可用的原版空结构 `minecraft:bastion/mobs/empty`，无需依赖未能解析的上游结构模板。

原有真实元件过滤测试也需要适配 GTO 的时序：GTOCore 的 `BasicCellInventoryMixin` 将两个 `getAvailableStacks` 重载接入 `CellDataStorage.cache`，AE2-GTO 的 `AvailableStacksCache` 在同一 tick 内复用列表。转移成功后立即枚举仍可能看到旧数量，过滤 EMPTY 弹出会延至下一次正常 IO 周期。这在移植前后均可复现。测试先通过模拟提取确认实时石头为 0、泥土为 128，再等待基础 IO 周期 5 tick，检查输入已弹出、输出只保留泥土、目标仍恰好收到 1000 石头且未收到泥土。没有改变生产转移行为或跳过此测试。

构建方式沿用 [GTO 兼容说明](GTO_COMPATIBILITY.md)，额外启用 `-PgtoQa` 并运行 `gtoQaJar gtoQaLtJar`。隔离专用服使用 instrumented LT 包代替正式包，并放入独立 QA 模组；instrumented 包保留正式生产类字节，仅附加测试类和资源。正式游戏使用 `build/libs` 主 JAR。

验证夹具时发现 QA 启动异常后还可能打印 `PASS total=0`。已让启动异常增加失败计数并终止该组的后续报告，失败不会再被空测试集合误报为通过。模板加载失败的尝试保留日志，不计入通过次数。

## 构建与证据

完整 `check`、`jarJar` 和 QA 重混淆通过。JUnit 共 1495 项，1490 通过、5 跳过，0 失败/错误。本次新增的回归属于服务器 GameTest，不计入该 JUnit 数量。

最终隔离完整 GTO 专用服报告 `GTO_COMPAT_QA PASS total=40 failed=0 startupFailures=0`，CPU、STORAGE、PLANNER 三项兼容探针全部 PASS，服务端正常停止，JVM 和监督脚本退出码均为 0。空槽重载回归由移植前 FAIL 变为 PASS。四种菜单操作通过服务器 FakePlayer 执行，本轮没有新增人工图形客户端操作测试。

正式制品：`build/libs/ae2lt-forge-1.20.1-2.1.2-gto.1.jar`，SHA-256：`45cc01e0637695eee6034965642900a65f3c15e5845b25d4ac5600d41b1ff5ac`。与移植前制品相比，唯一发生字节变化的生产类为 `OverloadedIOPortBlockEntity.class`；最终 instrumented 测试包的全部生产类与正式包逐字节一致，正式包不含本次 QA 测试类。

本机证据目录：`D:\codex-builds\ae2lt-gto-realtest-20261019`。

- `io-port-after-build.log`：修复后完整构建与单元测试。
- `io-port-unit-results.json`：JUnit 统计。
- `io-port-vanilla-template-build.log`：最终 IO 测试夹具编译、打包与重混淆。
- `server-io-port-before-console.log`、`server-io-port-before-repro-fixed-console.log`：未执行用例的模板加载失败记录；前者还记录了已修正的空组误报。
- `server-io-port-before-final-console.log`：移植前生产包执行 40 项，38 通过；空槽重载回归明确失败，另一个失败为上述尚未适配的同 tick 元件弹出断言。
- `server-io-port-after-console.log`：移植后生产类执行 40 项，39 通过；空槽重载和四种菜单操作通过，仅剩上述时序断言。
- `server-io-port-diagnostic2-console.log`、`gto-cell-mixin-bytecode.txt`、`gto-cell-bytecode.txt`：真实元件缓存时序的诊断证据。
- `io-port-final-build.log`：最终测试夹具构建与重混淆。
- `server-io-port-final-console.log`：最终 40/40 及三个兼容探针通过。
- `io-port-port-results.json`：逐项结果、制品哈希、生产类对比及已有文件修改保护检查。

本轮保留了开始任务前已有的 `BigMixedProviderGameTests`、`GlobalBatchAdaptersGameTests` 两个文件修改，且按 SHA-256 核对内容未变。没有重置用户工作区，也没有改动同级 TB 项目或用户正式存档。
