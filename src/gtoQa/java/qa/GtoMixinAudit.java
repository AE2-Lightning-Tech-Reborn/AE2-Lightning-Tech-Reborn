package qa;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import com.google.gson.JsonParser;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;

/** Loads every enabled target on the client, without initializing target static state. */
public final class GtoMixinAudit {
    private GtoMixinAudit() { }

    public static void run() throws Exception {
        ClassLoader loader = GtoMixinAudit.class.getClassLoader();
        var targets = new LinkedHashSet<String>();
        var skipped = new ArrayList<String>();
        var failures = new ArrayList<String>();
        int enabledMixins = 0;
        for (String config : List.of("ae2lt.mixins.json", "ae2lt.recipeviewer.mixins.json",
                "ae2lt.ae2wtlib.mixins.json", "ae2lt.mekanism.mixins.json")) {
            var json = JsonParser.parseString(new String(read(loader, config), java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject();
            String pkg = json.get("package").getAsString();
            var plugin = (IMixinConfigPlugin) Class.forName(json.get("plugin").getAsString())
                    .getConstructor().newInstance();
            plugin.onLoad(pkg);
            for (String section : List.of("mixins", "client")) {
                if (!json.has(section)) continue;
                for (var entry : json.getAsJsonArray(section)) {
                    String mixin = pkg + "." + entry.getAsString();
                    var node = new ClassNode();
                    new ClassReader(read(loader, mixin.replace('.', '/') + ".class"))
                            .accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    var annotations = new ArrayList<AnnotationNode>();
                    if (node.visibleAnnotations != null) annotations.addAll(node.visibleAnnotations);
                    if (node.invisibleAnnotations != null) annotations.addAll(node.invisibleAnnotations);
                    boolean pseudo = annotations.stream().anyMatch(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Pseudo;"));
                    for (var annotation : annotations) {
                        if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) continue;
                        for (int i = 0; i < annotation.values.size(); i += 2) {
                            String key = (String) annotation.values.get(i);
                            if (!key.equals("value") && !key.equals("targets")) continue;
                            for (Object target : (List<?>) annotation.values.get(i + 1)) {
                                String name = target instanceof Type type ? type.getClassName() : (String) target;
                                if (!plugin.shouldApplyMixin(name, mixin)) {
                                    skipped.add(mixin + " (optional mod absent)");
                                    continue;
                                }
                                if (pseudo && loader.getResource(name.replace('.', '/') + ".class") == null) {
                                    skipped.add(mixin + " (pseudo target absent)");
                                    continue;
                                }
                                enabledMixins++;
                                try {
                                    Class.forName(name, false, loader);
                                    targets.add(name);
                                } catch (Throwable failure) {
                                    failures.add(mixin + " -> " + name + ": " + failure);
                                    failure.printStackTrace();
                                }
                            }
                        }
                    }
                }
            }
        }
        try {
            GtoRecipeTransferAudit.run();
        } catch (Throwable failure) {
            failures.add("Recipe transfer assertions: " + failure);
            failure.printStackTrace();
        }
        String report = (failures.isEmpty() ? "PASS" : "FAIL") + ": enabledMixins=" + enabledMixins
                + " uniqueTargets=" + targets.size() + " skipped=" + skipped.size() + "\n"
                + "Loaded targets:\n" + String.join("\n", targets) + "\nSkipped:\n" + String.join("\n", skipped)
                + "\nFailures:\n" + String.join("\n", failures) + "\n";
        Files.writeString(Path.of("mixin-audit-result.txt"), report);
        System.out.println("GTO_MIXIN_AUDIT " + report);
        if (!failures.isEmpty()) throw new AssertionError("Enabled mixin target loading failed: " + failures);
    }

    private static byte[] read(ClassLoader loader, String path) throws Exception {
        try (InputStream input = loader.getResourceAsStream(path)) {
            if (input == null) throw new IllegalArgumentException("Missing resource " + path);
            return input.readAllBytes();
        }
    }
}
