# 天枢万象模式标签图标崩溃

记录日期：2026-11-07。

用户报告：打开天枢无线样板编码终端，渲染模式标签的样板图标时客户端崩溃；环境包含 `useless_mod 20.2.5.0` 和 `extendedae_plus`。异常为 `IllegalArgumentException: Missing or unsupported omniversal pattern data`，涉及 `OmniversalPatternDetails` 和被 Mixin 增强的 `EncodedPatternItem.getOutput()`。Embeddium 的模组污染提示不是该异常的直接原因。

源码定位：`UselessModCompat.icon()` 原先创建裸 `useless_mod:omniversal_pattern`，没有附带编码数据，随后传给 `TianshuPatternEncodingTermScreen` 的 `TabButton`。AE2 的 `GuiGraphicsHooks.onRenderGuiItem()` 在按住 Shift 且存在世界时，会调用编码样板的 `getOutput()`。第三方增强的输出读取若直接进入万象样板解码，便会因缺少数据而抛出上述异常。因此，这条路径不需要玩家持有损坏的样板即可触发；缺少数据的样板由标签图标代码自身创建。用户未提供完整崩溃日志，其他 Mixin 的精确调用次序尚未运行时复核。

修复：模式标签改用 `useless_mod:omniversal_mold_hub`（万象模具仓）作为图标。该物品是普通 `BlockItem`，不进入编码样板的 Shift 输出替换路径；继续使用现有标签和可选模组注册表边界。实际万象样板的识别、编码和配方数据保持原有职责。

验证边界：核对实际编译依赖 JAR 中模具仓的注册字段类型及模型，以及最新上游源码中的物品注册和 AE2 的标签渲染路径。`compileJava jar` 构建成功（仅现有弃用 API 警告）；尚未在用户包含 `extendedae_plus` 的整合包中复现或验证。
