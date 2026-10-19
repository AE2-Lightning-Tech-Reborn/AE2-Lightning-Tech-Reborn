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

        Class<?> button = Class.forName("mezz.jei.gui.recipes.RecipeTransferButton");
        requireMethod(button, "ae2lt$clearRecipeTransferMetadata");
        requireMethod(button, "ae2lt$beginRecipeTransferMetadata");
        // Avoid JEI's version-dependent constructor. A simulated click must return before
        // touching the uninitialized fields, just as it does for the real UI instance.
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        var unsafeField = unsafeClass.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object instance = unsafeClass.getMethod("allocateInstance", Class.class)
                .invoke(unsafeField.get(null), button);
        Method click = Arrays.stream(button.getDeclaredMethods())
                .filter(method -> method.getName().equals("onMouseClicked"))
                .findFirst().orElseThrow();
        click.setAccessible(true);
        Class<?> input = click.getParameterTypes()[0];
        Class<? extends Enum> inputType = (Class<? extends Enum>) Class.forName(input.getPackageName() + ".InputType");
        Object simulated = input.getConstructor(InputConstants.Key.class, double.class, double.class,
                int.class, inputType).newInstance(InputConstants.UNKNOWN, 0.0, 0.0, 0,
                Enum.valueOf(inputType, "SIMULATE"));
        var metadata = JeiRecipeTransferMetadata.class.getDeclaredField("CURRENT");
        metadata.setAccessible(true);
        var current = (ThreadLocal<JeiRecipeTransferMetadata.Snapshot>) metadata.get(null);
        current.set(new JeiRecipeTransferMetadata.Snapshot(null, "sentinel", List.of()));
        try {
            if (!Boolean.TRUE.equals(click.invoke(instance, simulated))) {
                throw new AssertionError("Simulated JEI click changed its return value");
            }
            if (current.get() != null) throw new AssertionError("Simulated JEI click retained transfer metadata");
        } finally {
            current.remove();
        }

        Method success = requireMethod(button, "ae2lt$keepRecipePageForDirectUpload");
        success.setAccessible(true);
        var called = new AtomicBoolean();
        success.invoke(instance, (Runnable) () -> called.set(true));
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
