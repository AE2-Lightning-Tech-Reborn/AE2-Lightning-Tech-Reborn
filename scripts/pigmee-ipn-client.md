# 猪咪合成站与 IPN 划动操作回归

## 原因与修复范围

AE2 库存列表中的 `RepoSlot` 是客户端虚拟条目，它的 `Slot.index` 不能作为服务端菜单槽号。
IPN 2.2.5 的划动回调却直接将该值传给 `ContainerClicker.shiftClick` / `qClick`，
绕过 AE2 屏幕按条目 serial 分派的逻辑，误移动或丢弃真实菜单第 0 格中的物品。

`IpnContainerClickerMixin` 只在猪咪合成站屏幕打开、且 IPN 记录的原始操作对象为
`RepoSlot` 时取消这两种额外操作。普通 AE2 Shift 点击仍负责提取库存条目。
检查对象而非数字槽号，可保留真实快捷栏、背包、合成格及其他屏幕的原有行为。
`@Pseudo` 和客户端 mixin 注册允许未安装 IPN 的客户端及专用服务端继续加载。

此修复需要更新客户端 LT；服务端收到的普通第 0 格点击无法区分误操作与玩家合法操作。

## 集成探针

使用 Java 21、Minecraft 1.21.1 / NeoForge，准备独立开发存档
`build/pigmee-ipn-client/saves/PigmeeIpnTest`。**只能使用可丢弃的测试世界**：
探针会清空测试玩家背包并改写 `(0, 99, 0)` 附近的方块。

```sh
./gradlew --init-script scripts/pigmee-ipn-client.init.gradle \
  -PpigmeeIpnMode=fixed \
  -Pae2ltJdbProbeMods="/path/IPN.jar:/path/libIPN.jar:/path/KotlinForForge.jar:/path/MouseTweaks.jar" \
  runClient
```

IPN 版本使用 2.2.5，libIPN 6.6.3，KotlinForForge 5.12.0，Mouse Tweaks 2.26.1。
Thunderbolt 运行时使用与 LT alpha 对应的构建产物。
结果写到游戏目录 `pigmee-ipn-fixed.txt`，测试完成后客户端自动退出。

探针调用实际 IPN 的私有划动回调，以及实际 AE2 屏幕 `slotClicked`，经由真实
客户端/集成服务端数据包检查库存同步。这是回调和数据包级集成测试，不模拟物理鼠标轨迹。

- 虚拟格子与真实快捷栏都为索引 0 时，IPN 不再误存快捷栏中的 7 个钻石。
- 正常 AE2 Shift 点击仍能提取 64 个铁锭，钻石保留在快捷栏。
- 开启划动合成输出格选项后，重复 Shift / Ctrl+Q 虚拟格操作仍不误存或误丢钻石。
- 对真实快捷栏格使用 IPN 划动，仍可正常存入这 7 个钻石。
- `-PpigmeeIpnMode=absent` 且不传额外模组路径，验证未安装 IPN 时的启动、屏幕和正常取物。

修复前对照：在原 alpha 代码上仅加入探针和启动脚本，使用 `baseline` 模式。
该模式要求 IPN 操作铁锭虚拟条目后，快捷栏钻石实际进入箱子，才能报告复现成功。
探针位于 `jdb` source set，不进入发布 JAR。
