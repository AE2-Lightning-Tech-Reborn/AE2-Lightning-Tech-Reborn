package com.moakiee.ae2lt.client.ldlib;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

import com.lowdragmc.lowdraglib2.client.shader.LDProgramDefineManager;
import com.lowdragmc.lowdraglib2.client.shader.LDShaderHolder;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/** Registers LDLib shaders with the client resource reload lifecycle. */
public final class LdlibShaders {
    private LdlibShaders() {
    }

    public static void register(RegisterShadersEvent event, ResourceLocation location, VertexFormat format,
            String uniformName, Consumer<Uniform> updateUniform, Consumer<ShaderInstance> onLoaded)
            throws IOException {
        var holder = createHolder(event.getResourceProvider(), location, format);
        var uniform = holder.baseInstance.getUniform(uniformName);
        if (uniform != null) {
            ((DefaultUniformUpdater) holder.baseInstance)
                    .ae2lt$setDefaultUniformUpdater(() -> updateUniform.accept(uniform));
        }
        event.registerShader(holder.baseInstance, onLoaded);
    }

    /** Per-instance callback supplied by the client ShaderInstance mixin. */
    public interface DefaultUniformUpdater {
        void ae2lt$setDefaultUniformUpdater(Runnable updater);
    }

    private static LDShaderHolder createHolder(ResourceProvider resources, ResourceLocation location,
            VertexFormat format) throws IOException {
        var previousDefines = List.copyOf(LDProgramDefineManager.getProgramDefines());
        try {
            var holder = LDShaderHolder.create(resources, location, format);
            if (holder == null) {
                throw new IOException("Missing shader " + location);
            }
            return holder;
        } catch (IOException | RuntimeException | Error exception) {
            throw exception;
        } catch (Throwable exception) {
            throw new IOException("Unable to create shader " + location, exception);
        } finally {
            // LDLib can leave its temporary shader define set on a failed load.
            LDProgramDefineManager.clearProgramDefines();
            previousDefines.forEach(LDProgramDefineManager::addProgramDefine);
        }
    }
}
