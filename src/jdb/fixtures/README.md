# 26.1.2 过载工厂配方同步回归

使用 Java 25，将以下官方模组放在一个独立目录：

- KubeJS `26.1.2-8.0.6`
- Rhino `2101.2.8-build.91`
- Better Advanced Tooltips `2601.1.0-build.9`

KubeJS 8.0.6 在服务端初始化创意标签时也会引用 BATIcons，因此本次测试需要第三个模组。

```sh
./gradlew runRecipeSyncGameTestServer -Pae2ltRecipeSyncGameTest \
  -Pae2ltKubeJsTestMods=/path/to/test-mods
```

任务会将 `overload-recipe-sync.js` 复制到独立的 `run-recipe-sync-gametest` 目录。
覆盖实际 KJS 配方加载、NeoForge `RecipeContentPayload` 编解码、JSON 往返、非法产物拒绝、
组件保留，以及 `64 × 2 = 128` 并行产物进入真实工厂物品栏。
测试代码、脚本和测试用依赖均不打入发布 JAR。

此回归验证实际配方同步包；不会启动图形客户端完成一次 TCP 登录。
