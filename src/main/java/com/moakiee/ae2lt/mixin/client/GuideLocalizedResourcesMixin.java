package com.moakiee.ae2lt.mixin.client;

import java.util.Map;
import java.util.HashMap;
import java.util.function.Predicate;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** AE2 12 predates localized guide pages; resolve LT translations before indexing. */
@Mixin(targets = "appeng.client.guidebook.Guide$ReloadListener", remap = false)
public abstract class GuideLocalizedResourcesMixin {
    @WrapOperation(method = "prepare(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)Ljava/util/Map;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/resources/ResourceManager;listResources(Ljava/lang/String;Ljava/util/function/Predicate;)Ljava/util/Map;", remap = true))
    private Map<ResourceLocation, Resource> ae2lt$localize(ResourceManager manager, String folder,
            Predicate<ResourceLocation> filter, Operation<Map<ResourceLocation, Resource>> original) {
        var resources = original.call(manager, folder, filter);
        var result = new HashMap<>(resources);
        String prefix = folder + "/_";
        String selected = prefix + Minecraft.getInstance().options.languageCode + "/";
        for (var entry : resources.entrySet()) {
            var id = entry.getKey();
            if (!(id.getNamespace().equals("ae2lt") || id.getNamespace().equals("ae2ltpp"))
                    || !id.getPath().startsWith(prefix)) continue;
            result.remove(id);
            if (id.getPath().startsWith(selected)) {
                result.put(new ResourceLocation(id.getNamespace(), folder + "/" + id.getPath().substring(selected.length())), entry.getValue());
            }
        }
        return result;
    }
}
