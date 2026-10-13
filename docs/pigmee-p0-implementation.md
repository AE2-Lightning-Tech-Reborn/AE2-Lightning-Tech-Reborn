# 猪咪工作站 P0 玩家代理

独立功能分支上的实验实现。设计基线为 docs 分支 `0e20b7ca` 的「猪咪远程工作站/实现设计.md」；代码基线为 main `5269dcab`，Minecraft 1.21.1 / NeoForge 21.1.220 / AE2 19.2.17 / Thunderbolt 2.0.3-beta2。

## 当前边界

仅在服务端显式设置 `-Dae2lt.pigmeeP0=true` 后启用。没有 HTTP、MCP、凭证、AI 或远程网络输入。已增加管理员 Fumo 转换探针；普通放置、佩戴、空手旋转不变。没有接入生存玩家的控制器使用入口，也没有消耗控制器，也没有确定死亡控制器返还、离线作业、超维能力费用等玩法。P0 直接创建的身体使用原版玩家尺寸和生存模式；P1 原型按普通/超维→生存、创造→创造固定类型，正常拥有背包、护甲、生命、饥饿和药水效果。

管理员命令（权限等级 2）：

- `/pigmee_p0 create`：由在线真人管理员创建一个独立生存身体，位置在执行者 Z + 2。仅用于空旷测试场地。
- `/pigmee_p0 activate_fumo <x> <y> <z>`：管理员近距离转换放置的普通、超维或创造猪咪；此探针不收控制器费用，不作为正式生存玩法。
- `/pigmee_p0 list`：显示持久身份、所有者、状态与诊断。
- `/pigmee_p0 stop <uuid>` / `start <uuid>`：保存停驻 / 按原身份恢复。重复 start 返回同一个活动身体。
- `/pigmee_p0 walk <uuid> <0..200>`：沿身体当前朝向给予有限 tick 的前进输入；停驻和重启清除输入。

每个测试存档最多创建八个身份。没有删除、复活、隔离库存发还或搬走回收入口。死亡和隔离记录禁止再次激活；恢复处置属于后续独立管理员工具，不自动补发库存。

## 原生链路与保存

`PigmeeServerPlayer` 直接继承 `ServerPlayer`，不继承将 tick、die 和伤害置空的 NeoForge `FakePlayer`。每只使用随机独立 UUID 和明确标识为 Pigmee 的 profile；所有者另存。`isFakePlayer()` 返回 true，命令权限固定为 0。不调用真人 `PlayerList.placeNewPlayer`、登录认证、退出或 playerdata 保存流程。

`ServerLevel.addNewPlayer` 接入实体跟踪、世界玩家表和 `DistanceManager` 原生玩家票据。世界是唯一 tick 所有者：`tick()` 调用玩家专用更新和通常由网络 listener 驱动的 `doTick()`，随后更新原生加载中心。无 socket 的 listener 丢弃发给代理的包，不注册到服务器连接列表，不累积 Connection 的发送队列。摔落检查显式接到原生 `doCheckFallDamage`。移除身体走原生实体跟踪退出，逐身体释放加载来源。

世界 `data/ae2lt_pigmee_p0.dat` 是唯一身体状态存档。活动对象是可变库存权威；SavedData 在世界保存时调用完整 `saveWithoutId`，停驻先关闭菜单、快照，然后卸载。不会每 tick 序列化全库存。`Player.shouldBeSaved() == false` 避免另存一份区块实体，管理器不使用真人 `playerdata` 恢复。

正常停止时保存活动/停驻/死亡状态，后续启用 P0 的服务端按活动身份恢复一次。启动/激活前先写入并 flush 独立 `.running` 崩溃标记。完整关服保存结束后，读回 NBT 与最终快照一致才删除标记。标记残留、未知版本、重复身份、损坏记录均拒绝自动恢复可花费库存；原始未知/重复数据保留。异常进程结束不自动重放先前输入或补发资源。这个保守机制不是 P1 Fumo 与控制器跨文件交易协议。

## 验证入口

构建前必须与 Thunderbolt 性能验证任务协调，`/tmp/thunderbolt-reborn-benchmark.lock` 存在时禁止启动 Gradle、GameTest 或游戏。协调者要求静默即暂停，即使尚未出现锁。

Java 21，使用仓库 Gradle wrapper：

```sh
./gradlew --no-daemon --max-workers=2 --no-configuration-cache \
  -I scripts/pigmee-p0-test.init.gradle runGameTestServer
```

如果 Thunderbolt 依赖存放在独立本地 Maven 仓库，额外设置 `-PpigmeeLocalMaven=/path/to/repository`。初始化脚本只裁剪本次开发测试的 jdb 和可选运行依赖，不更改正式依赖版本。测试世界位于 `build/pigmee-p0-gametest`。

`PigmeeP0GameTests` 在无任何真人登录的真实 NeoForge 世界中创建远离测试场地的两个身体，验证：自然前进跨区块、PlayerTickEvent 与实体 tick 一次性、原生票据跟随、独立库存/装备/生命/饥饿/效果、原生受伤/死亡掉落、重复死亡回调、停驻重建、两来源重叠释放以及最终区块实际卸载。另验证未知 schema 和重复身份保留、异常关服快照隔离。

`PigmeeRestartProbe` 使用两个独立专用服进程和同一磁盘存档验证，不把内存 NBT round-trip 称作重启。测试目录 `build/pigmee-p0-restart`，须提前放置 `eula.txt` 与仅绑定 localhost 的 `server.properties`，推荐 flat 世界、view-distance=2、simulation-distance=2、spawn-protection=0。然后分别运行：

```sh
./gradlew --no-daemon --max-workers=2 --no-configuration-cache \
  -I scripts/pigmee-p0-test.init.gradle -PpigmeeRestartPhase=prepare runServer
./gradlew --no-daemon --max-workers=2 --no-configuration-cache \
  -I scripts/pigmee-p0-test.init.gradle -PpigmeeRestartPhase=verify runServer
```

每个进程完成后自行正常停止。通过以日志中明确的 `PIGMEE_*_PASS` 标记及对应断言为准；Gradle 退出成功本身不能证明探针通过，`PIGMEE_RESTART_FAIL` 必须视为失败。

## 运行验收记录

已通过以下真实运行（本地 Java 21，NeoForge 21.1.220；无 Curios 等可选运行模组）：

- `compileJava`、`compileJdbJava` 成功。
- 独立 GameTest 世界 `pigmee-p0-gametest-run5`：1 个必需端到端测试通过；日志含 `PIGMEE_P0_PASS`，断言覆盖上节全部项目。首轮 440 tick 验收后进程正常退出。随后在 P1 变更后的 `pigmee-p0-final-run6` 中强化并通过 640 tick 回归：无真人登录持续 400 tick、跨三个区块、实际摔落伤害、原生死亡和最终卸载均通过。
- 专用服进程一（prepare）：两只代理活动 100 tick 后正常停服，写入同一存档。
- 专用服进程二（verify）：同一存档唯一恢复两只原 UUID 身体，库存 13 个绿宝石 / 23 个红石分别保留，铁胸甲、11 点生命、9 点饥饿保留，两个身体继续正常 tick；重复 start 没有第二实体，未生成代理 `playerdata/*.dat`。停用后正常退出。日志含 `PIGMEE_RESTART_VERIFY_PASS`。
- 关服后 `.running` 标记消失，表示最终磁盘快照读回匹配。异常快照、重复身份和未知 schema 已在真实运行中的 NBT 恢复测试覆盖；另在三类型专用服存档中实际执行了 `Runtime.halt(86)` 硬停注入；下一 JVM 未恢复任何身体，三份旧库存全部隔离，逐一激活均被拒绝。日志含 `PIGMEE_CRASH_RECOVERY_PASS`。硬停轮次的退出 86 / Gradle FAILED 是预期故障注入，不列为普通测试成功。

首两次启动因测试结构命名/资源缺失失败；随后修正了 GameTest 自身强载测试台的断言范围，并为每次生命周期运行使用新目录，避免旧活动记录影响计数。保留失败日志，不把失败轮次计为通过。重复 GameTest 时用 `-PpigmeeGameTestDirectory=<新的目录名>`，测试要求空存档。

P1 服务端转换原型还通过：

- `PigmeeActivationGameTests`：1 个端到端测试通过，精确限定三种 Fumo；原生生存/创造模式、类型恢复、重复转换拒绝、含水保留、原旋转行为、领地事件取消、距离/空间限制、加入世界被取消后的方块与自定义名称/旋转状态恢复、源 Fumo 重现时隔离均通过。日志含 `PIGMEE_P1_ACTIVATION_PASS`。
- 新的三类型专用服 prepare / verify 两进程均通过：三 UUID 各恢复一只身体，普通与超维仍为生存、创造仍为创造，三份库存独立，均无 OP 权限，也没有真人 playerdata 副本。日志含 `PIGMEE_RESTART_VERIFY_PASS: three variants`。

P1 数据使用 schema 2，兼容读取 P0 schema 1（普通类型）。转换来源、方块实体快照和持久身份同存；世界拒绝加入时只在原位置仍是本次替代状态时恢复源块，不覆盖事件处理器的新改动。重启或再次激活发现源 Fumo 重现则隔离，不同时启用两份承载者。

单独运行 P1 测试：加 `-PpigmeeTestNamespace=ae2lt_pigmee_p1 -PpigmeeGameTestDirectory=<新目录>`。独立重启目录可通过 `-PpigmeeRestartDirectory=<新目录>` 指定。硬停测试只用于已经 prepare / verify 的一次性三代理测试世界：依次设置 `-PpigmeeRestartPhase=crash`（预期 86）及 `verify_crash`，测试源集未打入发布 jar。

以上证明 P0 核心技术可行，不等于完整远程工作站、客户端视图或生产服玩法验收。

## 后续阶段与限制

- P0 核心门槛通过，已推进 P1 服务端转换与类型基础；完整 P1 尚未通过。下一步先补猪咪外观/类型同步与绑定物品，并定稿控制器成本及转换交易策略，再进入人工第一人称遥控与操作（P2/P3）。
- 当前原生玩家表会影响睡眠比例、刷怪距离和维度空闲判定；原生模拟距离由服务器控制。未把这些副作用定为正式玩法，故默认关闭。
- 未验收真人客户端渲染、玩家信息包晚加入顺序、客户端容器同步、Curios、墓碑、领地或特定装备效果。仅保留原生调用和拒绝，不声称第三方兼容。
- 不保证任意高速度进入尚未生成区块、坐骑、跨维度、恢复失效维度或损坏存档的自动修复。P0 无这些操作入口。
- 统计/进度仍使用该独立 profile 的原生服务端对象，但不把它们宣称为工作站支持的持久进度功能。
- 死亡沿用原生玩家规则并禁止重复身体/库存恢复；猪咪本体、控制器掉落和正式死亡坐标广播留给后续阶段。异常关服通过隔离阻止不确定库存恢复，不宣称可以对其他模组的世界副作用做事务回滚。
