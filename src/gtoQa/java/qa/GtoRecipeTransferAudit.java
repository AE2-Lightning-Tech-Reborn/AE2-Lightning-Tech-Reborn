package qa;

import com.moakiee.ae2lt.client.compat.JeiRecipeTransferMetadata;
import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraftforge.fml.ModList;

/** Exercises real transformed recipe-viewer classes in the isolated client. */
public final class GtoRecipeTransferAudit {
    private GtoRecipeTransferAudit() { }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void run() throws Exception {
        if (ModList.get().isLoaded("emi")) {
            Class<?> handler = Class.forName("appeng.integration.modules.emi.EmiEncodePatternHandler");
            requireMethod(handler, "ae2lt$onTransfer");
            requireMethod(handler, "ae2lt$restoreClosedLoopMode");
        }
        if (!ModList.get().isLoaded("jei")) return;

        Class<?> transferEntry;
        try {
            transferEntry = Class.forName("mezz.jei.common.transfer.RecipeTransferService");
        } catch (ClassNotFoundException legacyJei) {
            transferEntry = Class.forName("mezz.jei.common.transfer.RecipeTransferUtil");
        }
        boolean wirelessInjected = Arrays.stream(transferEntry.getDeclaredMethods())
                .anyMatch(method -> method.getName().contains("ae2lt$supply"));
        if (wirelessInjected != ModList.get().isLoaded("ae2wtlib")) {
            throw new AssertionError("JEI wireless supply injection does not match AE2WTLib availability");
        }

        Class<?> button;
        boolean controller;
        try {
            button = Class.forName("mezz.jei.gui.recipes.RecipeTransferButtonController");
            controller = true;
        } catch (ClassNotFoundException legacyButton) {
            button = Class.forName("mezz.jei.gui.recipes.RecipeTransferButton");
            controller = false;
        }
        if (Arrays.stream(button.getDeclaredMethods()).noneMatch(method ->
                method.getName().contains("ae2lt$legacyTransfer")
                        || method.getName().contains("ae2lt$contextTransfer"))) {
            throw new AssertionError("JEI transfer is missing its scoped metadata bridge");
        }
        // Avoid JEI's version-dependent constructor. A simulated click must return before
        // touching the uninitialized fields, just as it does for the real UI instance.
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        var unsafeField = unsafeClass.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object instance = unsafeClass.getMethod("allocateInstance", Class.class)
                .invoke(unsafeField.get(null), button);
        Method click;
        Object[] clickArguments;
        boolean expectedResult;
        if (controller) {
            var screenSupplier = button.getDeclaredField("screenSupplier");
            screenSupplier.setAccessible(true);
            screenSupplier.set(instance, (java.util.function.Supplier<Object>) () -> null);
            click = button.getDeclaredMethod("transferRecipe", boolean.class, boolean.class);
            clickArguments = new Object[] {false, true};
            expectedResult = false;
        } else {
            click = Arrays.stream(button.getDeclaredMethods())
                    .filter(method -> method.getName().equals("onMouseClicked"))
                    .findFirst().orElseThrow();
            Class<?> input = click.getParameterTypes()[0];
            Class<? extends Enum> inputType = (Class<? extends Enum>) Class.forName(input.getPackageName() + ".InputType");
            Object simulated = input.getConstructor(InputConstants.Key.class, double.class, double.class,
                    int.class, inputType).newInstance(InputConstants.UNKNOWN, 0.0, 0.0, 0,
                    Enum.valueOf(inputType, "SIMULATE"));
            clickArguments = new Object[] {simulated};
            expectedResult = true;
        }
        click.setAccessible(true);
        var metadata = JeiRecipeTransferMetadata.class.getDeclaredField("CURRENT");
        metadata.setAccessible(true);
        var current = (ThreadLocal<JeiRecipeTransferMetadata.Snapshot>) metadata.get(null);
        var sentinel = new JeiRecipeTransferMetadata.Snapshot(null, "sentinel", List.of(), List.of());
        current.set(sentinel);
        try {
            if (!Boolean.valueOf(expectedResult).equals(click.invoke(instance, clickArguments))) {
                throw new AssertionError("Simulated JEI click changed its return value");
            }
            if (current.get() != sentinel) throw new AssertionError("Simulated JEI click changed the outer metadata scope");
        } finally {
            current.remove();
        }

        Method success = Arrays.stream(button.getDeclaredMethods())
                .filter(method -> method.getName().contains("ae2lt$keepRecipePageForDirectUpload")
                        && method.getParameterCount() == 2 && method.getParameterTypes()[0] == Runnable.class)
                .findFirst().orElseThrow();
        success.setAccessible(true);
        var called = new AtomicBoolean();
        com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> operation = arguments -> {
            ((Runnable) arguments[0]).run();
            return null;
        };
        success.invoke(null, (Runnable) () -> called.set(true), operation);
        if (!called.get()) throw new AssertionError("JEI success callback was suppressed outside RecipesGui");
        System.out.println("GTO_RECIPE_TRANSFER_AUDIT PASS: EMI handlers; JEI simulated-click metadata; "
                + "success callback; wireless supply gate");
    }

    private static Method requireMethod(Class<?> target, String fragment) {
        return Arrays.stream(target.getDeclaredMethods())
                .filter(method -> method.getName().contains(fragment))
                .findFirst().orElseThrow(() -> new AssertionError("Missing injected handler " + target.getName() + "." + fragment));
    }
}
