# GTO 无线区块生命周期 QA

本轮将不可访问与真实磁盘卸载分开验证。`upstream-wireless` 仍为 19 项，
`upstream-admission-slow` 仍为 3 项；快项保持 120 tick，慢项保持总计
1600 tick 和三个完整磁盘循环。r9 已编译并实际运行：快组 19／19、慢组
3／3 通过；以前轮次的失败保留。

## 实际时序依据

对 Dev23 实际 GTO `26.10.10` 的公开可读字节码进行离线核验：
`ServerChunkCacheMixin` 在正常 ticking 且 `serverTick % 80 != 0` 时设置
skip；`ChunkMapMixin` 在 `processUnloads` 调用前取消，跳过整个卸载处理。
因此 pending-unload 转移、保存 future 的挂接和 unloadQueue 消费均受节流。

标准 Forge `ChunkMap.scheduleUnload` 将 `getChunkToSave()` future 的回调排入
unloadQueue，消费时还会检查 future 是否被替换。已完成的 future 和足够预算
可以在一次处理内完成；未完成、晚入队或 future 更新可能需要后续处理，不能
规定必需恰好两次。`UNKNOWN` 票据超时为 1 tick；300 是 `PORTAL` 票据期限，
没有找到通用的固定 300 tick 卸载等待规则。

Forge 的真实 `ChunkEvent.Unload` 在 `setLoaded(false)` 后发布，但早于
`ServerLevel.unload` 的 BE 清理。失效断言应在后续 test tick 联合观察，
不能在事件处理器中要求 capability 已经失效。

r8 首次不可访问发生于 test tick 37 / server tick 53；结束时为 test tick 121 /
server tick 137，`oldRemoved=false`、`capPresent=true`、失效监听与 Unload
事件均为 0、旧对象实际投递为 0。它没有观察到真实卸载，不能据此认定生产
清理错误。生产缓存没有在本轮修改。

## 断言迁移

| 契约 | 快项：`chunkInaccessibilityPreservesBindingAndResumesSupply` | 慢项：`repeatedDiskChunkUnloadReloadPreservesBindingWithoutProbe` |
|---|---|---|
| 预算与数量 | 原 120 tick；19 项中的一项，更名后仍选中 | 原 1600 tick；3 项中的一项，三个完整循环 |
| 不可访问时暂停 | 在 `getChunkNow == null` 后注入 50,000 FE，要求 `lastVisitedTick` 晚于注入时间且状态为 `UNLOADED`；区块仍不可访问 | 每轮首次不可访问时冻结旧机器的累计 FE 与接收调用数，之后不得增加 |
| 绑定归属 | 暂停、重新访问后保留原 UUID | 真实卸载、磁盘恢复及恢复供电后均保留原 UUID，不重放自动探针 |
| 真正卸载 | 只保留诊断观测，不以不可访问代替卸载完成 | 每轮必须实际收到 Unload 事件，随后旧 BE removed、旧 capability 不再 present，且失效监听已触发 |
| 对象身份 | 使用当前实际 BE/capability；未完成真实卸载时允许合法复用 | 每轮要求不同 BE、不同且有效的 capability 和储能对象，保存的累计回执与 FE 均恢复 |
| 旧对象投递 | 不可访问期间为零；恢复后若对象已更换，旧对象继续为零 | 从首次不可访问起冻结旧投递，真正卸载及新对象恢复供电后仍不增加 |
| FE 守恒与恢复 | 暂停时 ME + escrow = 50,000；重新访问并实际供电后 ME + escrow + 当前机器 = 50,000 | 每轮显式 credit/reset 后记录基线；供电、卸载、磁盘恢复各阶段核对 ME + escrow + 单个当前机器；为恢复供电显式 drain 时从基线扣除，然后再次核对 |

慢项以事件和失效条件推进，替换固定的 400 tick 等待；没有调用手动卸载、
`clearAllBlockEntities` 或 capability invalidation 来制造通过条件。初始 FE
注入和恢复供电前的排空是夹具操作，均有独立账面基线；新旧对象的存量不会
同时计入同一次余额。

快项的 19／19 不再代表真实卸载检查通过，必须同时报告慢项的实际执行结果。
`GtoCompatQA` 在无过滤器的两组启动时核对 19／3 数量、对应方法名及
120／1600 tick；任务目录的 suite JSON 与覆盖说明同步更名，避免静默漏跑。

## r9 实际结果与证据范围

Dev23、Core `2.0.4-gto.2` 下，`upstream-wireless` 用时 96.8 秒，19／19
通过；`upstream-admission-slow` 用时 124.9 秒，3／3 通过。实际附加夹具包
SHA-256 为 `8495d395b838679dd8d85d803ad3d2b226102d2f8f9ee965f19ddc426564df24`，
正式包为 `358ba472560aa2110a25e9592f880072a85d0a900bd378e6733ad747abbd8f7d`。

冻结字节码及 lambda 绑定复核确认：入口要求三个循环，唯一成功出口位于
`completed == 3` 断言之后；各循环必须经过真实 Unload、旧能力失效、新对象
与恢复供电、守恒断言。没有发现提前成功路径，生产 GameTest 断言不依赖
Java `-ea` 开关。

该轮 INFO 生命周期诊断未出现在原始输出中，运行期过滤原因尚未证实。
因此可以保留实际断言通过结果，但无法补出各轮卸载与能力失效的具体 tick。
日志缺失不作为修改生产缓存、扩大预算或重复整组的依据。
