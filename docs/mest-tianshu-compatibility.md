# ME 拼接终端的天枢面板

适用 Minecraft 1.21.1 NeoForge；兼容基线为 MEST 0.0.3.0（其要求 NeoForge 21.1.238+、AE2WTLib 19.5.0+）。MEST 是可选依赖，不打包进 AE2LT。

在拼接终端的模块开关中启用以下三个面板，再按 MEST 原有方式拖动、拼接和保存布局。新面板默认隐藏，避免覆盖已有布局。

- **天枢工作台**：合成、铁砧、锻造、切石和存储元件配置，沿用天枢终端的原生操作。
- **天枢样板编码**：原生编码页、加工配置、闭环配置与上传。原版 MEST 编码器和天枢编码器分别保存草稿及编码结果；MEST 自动补入本地的空白样板也可供天枢编码使用，每次只消耗实际需要的一张。
- **天枢库存维持**：显示当前规则和库存，点击规则编辑；点击顶部进入规则与保留库存设置。在 ME 物品列表上 Shift + 中键创建或编辑维持规则。仍需网络中可用的天枢。

先点击所需工作台或样板面板，再使用配方查看器的填充按钮。JEI 和 EMI 的 MEST 编码路径会填入当前天枢样板面板；JEI 也支持填充天枢铁砧、锻造和切石配方。详细设置沿用原生弹窗，返回后恢复原拼接布局和容器。

实现只打开一个 MEST 容器。天枢菜单作为内部控制器，槽位使用固定映射，客户端动作使用独立命名空间；游标、无线连接、电量和 ME 库存同步由外部容器统一管理。工作台实物保存在终端物品中，库存维持仍由原生天枢服务保存和校验。关闭容器、失效宿主及过期选择版本不能继续修改规则。

开发验证：

```sh
./gradlew -I scripts/mest-test.init.gradle \
  -Pneo_version=21.1.238 -Pae2wtlib_version=19.5.1 \
  -Pae2ltEnableMestDevRuntime=true runGameTestServer

./gradlew -I scripts/tianshu-workstation-test.init.gradle runGameTestServer
```

`scripts/mest-client.init.gradle` 为隔离客户端探针配置。先在 `build/mest-client/saves/MestQA` 准备测试世界并完成首次启动设置，再运行同组版本参数下的 `runClient`。探针仅在启用 `ae2lt.mestClientProbe` 的开发运行中加载，会在该测试世界建立 ME 网络、打开面板、验证真实数据包和弹窗返回，并将结果与截图写入运行目录。

已验证：4 项 MEST GameTest（含共享空白样板扣除、失败退款、上传、规则保存、过期操作拒绝、实物持久化与结果槽保护）；未加载 MEST 时的 12 项原有天枢工作台 GameTest；JEI 单独启用及 JEI/EMI 共存的客户端面板、配方填充、编码、弹窗返回和锻造结果点击。项目 `build` 通过，单元测试 1475 项通过、4 项跳过。客户端探针可用 `-Pae2ltMestViewer=jei`、`emi` 或默认 `both` 选择配方查看器组合。
