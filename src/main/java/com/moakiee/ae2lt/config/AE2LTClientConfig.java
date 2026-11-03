package com.moakiee.ae2lt.config;

import java.util.List;
import java.util.Locale;
import net.minecraftforge.common.ForgeConfigSpec;

public final class AE2LTClientConfig {
    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.EnumValue<TianshuUploadTrigger> TIANSHU_UPLOAD_TRIGGER;
    private static final ForgeConfigSpec.EnumValue<TianshuUploadMatchMode> TIANSHU_UPLOAD_MATCH_MODE;
    private static final ForgeConfigSpec.BooleanValue TIANSHU_INTERCEPT_DUPLICATE_ENCODING;
    private static final ForgeConfigSpec.BooleanValue TIANSHU_JEI_WIRELESS_SUPPLY;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> TIANSHU_UPLOAD_ALIASES;
    private static final ForgeConfigSpec.BooleanValue DISABLE_CORE_SHADER_RENDERING;
    private static final ForgeConfigSpec.BooleanValue RENDER_MULTIBLOCK_CORE_EFFECTS;

    static {
        var builder = new ForgeConfigSpec.Builder();
        builder.push("tianshuTerminal");
        TIANSHU_UPLOAD_TRIGGER = builder
                .comment("Modifier condition that starts pattern upload after encoding")
                .defineEnum("uploadTrigger", TianshuUploadTrigger.NO_SHIFT);
        TIANSHU_UPLOAD_MATCH_MODE = builder
                .comment("Automatic unique-target selection: OFF, SAVED_ALIAS, or ANY_MATCH (includes suggested aliases)")
                .defineEnum("uploadMatchMode", TianshuUploadMatchMode.OFF);
        TIANSHU_INTERCEPT_DUPLICATE_ENCODING = builder
                .comment("Cancel encoding when the same pattern already exists on the ME network")
                .define("interceptDuplicatePatternEncoding", true);
        TIANSHU_JEI_WIRELESS_SUPPLY = builder
                .comment("Let existing JEI recipe-transfer handlers refill the backpack from a carried wireless Tianshu terminal")
                .define("jeiWirelessSupply", false);
        TIANSHU_UPLOAD_ALIASES = builder
                .comment("Recipe type/category id to pattern-provider alias mappings (source=alias)")
                .defineListAllowEmpty("uploadAliases", List.of(),
                        value -> value instanceof String text
                                && text.length() <= 512
                                && text.indexOf('=') > 0);
        builder.pop();
        builder.push("compatibility");
        DISABLE_CORE_SHADER_RENDERING = builder
                .comment(
                        "Compatibility switch: disable custom core shaders provided by AE2 Lightning Tech Reborn.",
                        "Enable only when a graphics driver or another rendering mod is incompatible with them.")
                .define("disableCoreShaderRendering", false);
        builder.pop();
        builder.push("rendering");
        RENDER_MULTIBLOCK_CORE_EFFECTS = builder
                .comment("Render formed Tianshu and matter-warping core effects")
                .define("multiblockCoreEffects", true);
        builder.pop();
        SPEC = builder.build();
    }

    private AE2LTClientConfig() {
    }

    public static TianshuUploadTrigger uploadTrigger() {
        return TIANSHU_UPLOAD_TRIGGER.get();
    }

    public static void setUploadTrigger(TianshuUploadTrigger trigger) {
        if (trigger == null) return;
        TIANSHU_UPLOAD_TRIGGER.set(trigger);
        if (SPEC.isLoaded()) SPEC.save();
    }

    public static TianshuUploadMatchMode uploadMatchMode() { return TIANSHU_UPLOAD_MATCH_MODE.get(); }

    public static boolean setUploadMatchMode(TianshuUploadMatchMode mode) {
        if (mode == null || !SPEC.isLoaded()) return false;
        var previous = TIANSHU_UPLOAD_MATCH_MODE.get();
        try {
            TIANSHU_UPLOAD_MATCH_MODE.set(mode);
            SPEC.save();
            return true;
        } catch (RuntimeException failure) {
            TIANSHU_UPLOAD_MATCH_MODE.set(previous);
            return false;
        }
    }

    public static boolean interceptDuplicatePatternEncoding() {
        return TIANSHU_INTERCEPT_DUPLICATE_ENCODING.get();
    }

    public static boolean jeiWirelessSupply() { return TIANSHU_JEI_WIRELESS_SUPPLY.get(); }

    public static void setJeiWirelessSupply(boolean enabled) {
        TIANSHU_JEI_WIRELESS_SUPPLY.set(enabled);
        if (SPEC.isLoaded()) SPEC.save();
    }

    public static boolean renderMultiblockCoreEffects() {
        return RENDER_MULTIBLOCK_CORE_EFFECTS.get();
    }

    public static boolean useCoreShaderRendering() {
        return !DISABLE_CORE_SHADER_RENDERING.get();
    }

    public static void setInterceptDuplicatePatternEncoding(boolean enabled) {
        TIANSHU_INTERCEPT_DUPLICATE_ENCODING.set(enabled);
        if (SPEC.isLoaded()) SPEC.save();
    }

    public static synchronized String findUploadAlias(String sourceKey) {
        String normalized = normalizeAliasKey(sourceKey);
        if (normalized.isEmpty()) return null;
        for (var entry : TIANSHU_UPLOAD_ALIASES.get()) {
            int separator = entry.indexOf('=');
            if (separator <= 0) continue;
            if (normalizeAliasKey(entry.substring(0, separator)).equals(normalized)) {
                String alias = entry.substring(separator + 1).strip();
                return alias.isEmpty() ? null : alias;
            }
        }
        return null;
    }

    public static synchronized List<UploadAliasRecords.Entry> uploadAliases() {
        return UploadAliasRecords.snapshot(TIANSHU_UPLOAD_ALIASES.get());
    }

    public static synchronized boolean setUploadAlias(String sourceKey, String alias) {
        return replaceUploadAlias(null, sourceKey, alias);
    }

    public static synchronized boolean replaceUploadAlias(String original, String source, String alias) {
        if (!UploadAliasRecords.valid(source, alias)) return false;
        return saveUploadAliases(UploadAliasRecords.replace(TIANSHU_UPLOAD_ALIASES.get(), original, source, alias));
    }

    public static synchronized boolean removeUploadAlias(String source) {
        return saveUploadAliases(UploadAliasRecords.remove(TIANSHU_UPLOAD_ALIASES.get(), source));
    }

    /** Apply a validated clipboard batch once, preserving unrelated records and the upload mode. */
    public static synchronized boolean importUploadAliases(List<UploadAliasRecords.Entry> entries) {
        if (entries == null || entries.stream().anyMatch(e -> !UploadAliasRecords.valid(e.source(), e.alias())))
            return false;
        List<String> updated = List.copyOf(TIANSHU_UPLOAD_ALIASES.get());
        for (var entry : entries) updated = UploadAliasRecords.replace(updated, null, entry.source(), entry.alias());
        return saveUploadAliases(updated);
    }

    private static boolean saveUploadAliases(List<String> updated) {
        if (!SPEC.isLoaded()) return false;
        var previous = List.copyOf(TIANSHU_UPLOAD_ALIASES.get());
        try {
            TIANSHU_UPLOAD_ALIASES.set(updated);
            SPEC.save();
            return true;
        } catch (RuntimeException failure) {
            TIANSHU_UPLOAD_ALIASES.set(previous);
            return false;
        }
    }

    private static String normalizeAliasKey(String sourceKey) {
        return sourceKey == null ? "" : sourceKey.strip().toLowerCase(Locale.ROOT);
    }
}
