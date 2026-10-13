# 猪咪工作站 P0 玩家代理

独立功能分支上的实验实现。设计基线为 docs 分支 `0e20b7ca` 的「猪咪远程工作站/实现设计.md」；代码基线为 main `5269dcab`，Minecraft 1.21.1 / NeoForge 21.1.220 / AE2 19.2.17 / Thunderbolt 2.0.3-beta2。

## 当前边界

仅在服务端显式设置 `-Dae2lt.pigmeeP0=true` 后启用。没有 HTTP、MCP、凭证、AI 或远程网络输入。尚未接管装饰 Fumo；没有消耗控制器，也没有确定死亡控制器返还、离线作业、超维能力费用等玩法。P0 身体使用原版玩家尺寸和生存模式，正常拥有背包、护甲、生命、饥饿和药水效果。

管理员命令（权限等级 2）：

- `/pigmee_p0 create`：由在线真人管理员创建一个独立生存身体，位置在执行者 Z + 2。仅用于空旷测试场地。
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

待运行。已完成主代码编译；首轮行为测试尚未执行断言，测试结构资源错误已修正。不得把当前原型称为已通过 P0。

## 后续阶段与限制

- P0 运行门槛通过后再进入 Fumo 激活交易和类型映射（P1），继而进入人工第一人称遥控与操作（P2/P3）。
- 当前原生玩家表会影响睡眠比例、刷怪距离和维度空闲判定；原生模拟距离由服务器控制。未把这些副作用定为正式玩法，故默认关闭。
- 未验收真人客户端渲染、玩家信息包晚加入顺序、客户端容器同步、Curios、墓碑、领地或特定装备效果。仅保留原生调用和拒绝，不声称第三方兼容。
- 不保证任意高速度进入尚未生成区块、坐骑、跨维度、恢复失效维度或损坏存档的自动修复。P0 无这些操作入口。
- 统计/进度仍使用该独立 profile 的原生服务端对象，但不把它们宣称为工作站支持的持久进度功能。
- 死亡沿用原生玩家规则并禁止重复身体/库存恢复；猪咪本体、控制器掉落和正式死亡坐标广播留给后续阶段。异常关服通过隔离阻止不确定库存恢复，不宣称可以对其他模组的世界副作用做事务回滚。
