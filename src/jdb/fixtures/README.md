# 配方同步与终端绑定回归

Minecraft 1.21.1 / Java 21：

```sh
./gradlew runRecipeSyncGameTestServer -Pae2ltRecipeSyncGameTestsOnly=true
```

此任务只编译本组 GameTest，并将 `overload-recipe-sync.js` 复制到独立测试目录。
使用开发环境中的 KubeJS 加载配方，再通过原生 `ClientboundUpdateRecipesPacket` 编解码，
检查大数量输入、产物、组件与 JSON 往返。另有非法产物拒绝及天枢无线终端绑定、重绑测试。
测试代码与脚本均不进入发布 JAR。

这是实际配方网络包的回归测试；不会启动图形客户端完成一次 TCP 登录。
