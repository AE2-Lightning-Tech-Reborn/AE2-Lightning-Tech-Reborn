package qa;

import mezz.jei.gui.recipes.RecipesGui;

/** QA retains the real JEI backend while the public GTO browser remains JEMI. */
public final class GtoJeiNativeGui {
    private static RecipesGui nativeGui;
    private GtoJeiNativeGui() {}
    public static void capture(RecipesGui gui) { nativeGui = gui; }
    public static void startBeforeJemi(Object registration) {
        // JEMI supplies every public GUI service, so JEI never runs its default GUI factory.
        // Run that installed factory only for the layout probe, on the actual client thread.
        var mc = net.minecraft.client.Minecraft.getInstance();
        Runnable start = () -> {
            try {
                var api = Class.forName("mezz.jei.api.registration.IRuntimeRegistration");
                Class.forName("mezz.jei.gui.startup.JeiGuiStarter").getMethod("start", api)
                        .invoke(null, registration);
                require();
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot start the installed JEI layout backend for QA", failure);
            }
        };
        if (mc.isSameThread()) start.run();
        else mc.submit(start).join();
    }
    public static RecipesGui require() {
        if (nativeGui == null) throw new AssertionError("The actual JEI screen was not captured during startup");
        return nativeGui;
    }
}
