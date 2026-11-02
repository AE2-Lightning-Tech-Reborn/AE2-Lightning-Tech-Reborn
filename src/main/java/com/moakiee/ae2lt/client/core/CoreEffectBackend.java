package com.moakiee.ae2lt.client.core;

import com.moakiee.ae2lt.client.core.veil.VeilCoreEffectShaders;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import net.minecraftforge.fml.ModList;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.slf4j.Logger;

final class CoreEffectBackend {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DefaultArtifactVersion MINIMUM_VEIL_VERSION =
            new DefaultArtifactVersion("1.0.0");
    private static final DefaultArtifactVersion MAXIMUM_VEIL_VERSION =
            new DefaultArtifactVersion("2.0.0");

    private static volatile boolean veilUsable = detectCompatibleVeil();

    private CoreEffectBackend() {
    }

    static boolean useVeil() {
        return veilUsable;
    }

    static void disableVeil(Throwable cause) {
        if (veilUsable) {
            veilUsable = false;
            LOGGER.warn("Veil core-effect backend failed; falling back to native shaders", cause);
        }
    }

    static boolean useShaderPackFallback() {
        return ShaderPackDetectorHolder.INSTANCE.isShaderPackInUse();
    }

    private static boolean detectCompatibleVeil() {
        var modList = ModList.get();
        if (modList == null || !modList.isLoaded("veil")) {
            return false;
        }

        var installedVersion = modList.getModContainerById("veil")
                .map(container -> container.getModInfo().getVersion())
                .orElse(null);
        if (installedVersion == null
                || installedVersion.compareTo(MINIMUM_VEIL_VERSION) < 0
                || installedVersion.compareTo(MAXIMUM_VEIL_VERSION) >= 0) {
            LOGGER.warn("Unsupported Veil version {} installed; AE2LT supports [1.0.0,2.0.0) "
                    + "and will use native core-effect shaders", installedVersion);
            return false;
        }

        try {
            boolean compatible = VeilCoreEffectShaders.isApiCompatible();
            if (!compatible) {
                LOGGER.warn("Veil is installed but its shader bridge API is incompatible; "
                        + "using native core-effect shaders");
            }
            return compatible;
        } catch (LinkageError | RuntimeException exception) {
            LOGGER.warn("Veil is installed but its shader bridge API is unavailable; "
                    + "using native core-effect shaders", exception);
            return false;
        }
    }

    private static ShaderPackDetector createShaderPackDetector() {
        var modList = ModList.get();
        if (modList == null || !modList.isLoaded("iris") && !modList.isLoaded("oculus")) {
            return () -> false;
        }

        for (String apiClassName : new String[] {
                "net.irisshaders.iris.api.v0.IrisApi",
                "net.coderbot.iris.api.v0.IrisApi"
        }) {
            try {
                Class<?> apiClass = Class.forName(apiClassName, false,
                        CoreEffectBackend.class.getClassLoader());
                return createReflectiveDetector(apiClass);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
                // Try the package used by the other Iris/Oculus generation.
            }
        }

        LOGGER.warn("Iris/Oculus is installed but its shader-pack API is unavailable; "
                + "using the shader-pack-safe core-effect renderer");
        return () -> true;
    }

    static ShaderPackDetector createReflectiveDetector(Class<?> apiClass) throws ReflectiveOperationException {
        Method factory = apiClass.getMethod("getInstance");
        Method query = apiClass.getMethod("isShaderPackInUse");
        if (!Modifier.isStatic(factory.getModifiers())
                || !apiClass.isAssignableFrom(factory.getReturnType())
                || Modifier.isStatic(query.getModifiers())
                || query.getReturnType() != boolean.class) {
            throw new NoSuchMethodException("Incompatible Iris/Oculus shader-pack API: " + apiClass.getName());
        }
        Object api = factory.invoke(null);
        if (!apiClass.isInstance(api)) {
            throw new IllegalStateException("Iris/Oculus returned no shader-pack API instance");
        }
        return new ReflectiveShaderPackDetector(api, query);
    }

    interface ShaderPackDetector {
        boolean isShaderPackInUse();
    }

    private static final class ShaderPackDetectorHolder {
        private static final ShaderPackDetector INSTANCE = createShaderPackDetector();

        private ShaderPackDetectorHolder() {
        }
    }

    private static final class ReflectiveShaderPackDetector implements ShaderPackDetector {
        private final Object api;
        private final Method isShaderPackInUse;
        private boolean failed;

        private ReflectiveShaderPackDetector(Object api, Method isShaderPackInUse) {
            this.api = api;
            this.isShaderPackInUse = isShaderPackInUse;
        }

        @Override
        public boolean isShaderPackInUse() {
            if (failed) {
                return true;
            }
            try {
                return Boolean.TRUE.equals(isShaderPackInUse.invoke(api));
            } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
                failed = true;
                LOGGER.warn("Unable to query the active Iris/Oculus shader pack; "
                        + "using the shader-pack-safe core-effect renderer", exception);
                return true;
            }
        }
    }
}
