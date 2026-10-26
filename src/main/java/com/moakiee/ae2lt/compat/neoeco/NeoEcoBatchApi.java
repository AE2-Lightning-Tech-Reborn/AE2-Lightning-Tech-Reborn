package com.moakiee.ae2lt.compat.neoeco;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.UUID;
import net.minecraft.world.level.Level;

/** Links only NeoECO's public, allocated-input contract. All handles are validated before publication. */
final class NeoEcoBatchApi {
    private static final String ROOT = "cn.dancingsnow.neoecoae.api.me.";
    final Class<?> providerType;
    private final Method prepare, count, power, submit, pushForJob;
    private final Object energyAccount;

    NeoEcoBatchApi(ClassLoader loader) throws ReflectiveOperationException {
        this(Class.forName(ROOT + "provider.ECOFastPathDispatchProvider", false, loader),
                Class.forName(ROOT + "ECOFastPathFacade", false, loader),
                Class.forName(ROOT + "ECOFastPathFacade$EnergyAccount", false, loader),
                Class.forName(ROOT + "ECOFastPathFacade$Reservation", false, loader));
    }

    NeoEcoBatchApi(Class<?> provider, Class<?> facade, Class<?> energy, Class<?> reservation)
            throws ReflectiveOperationException {
        if (!energy.isInterface() || !reservation.isInterface()) {
            throw new NoSuchMethodException("NeoECO energy and reservation must be interfaces");
        }
        providerType = provider;
        prepare = method(facade, "prepareAllocated", null, true, ICraftingProvider.class,
                IPatternDetails.class, KeyCounter[].class, long.class, Level.class, UUID.class);
        Class<?> prepared = prepare.getReturnType();
        count = method(prepared, "craftCount", long.class, false);
        power = method(prepared, "power", double.class, false);
        submit = method(prepared, "submit", boolean.class, false, energy);
        pushForJob = method(facade, "pushPatternForJob", boolean.class, true,
                ICraftingProvider.class, IPatternDetails.class, KeyCounter[].class, UUID.class);
        method(energy, "reserve", reservation, false, double.class);
        method(reservation, "commit", void.class, false);
        method(reservation, "refund", void.class, false);
        Object paid = Proxy.newProxyInstance(reservation.getClassLoader(), new Class<?>[]{reservation},
                (proxy, method, args) -> switch (method.getName()) {
                    case "commit", "refund" -> null; // The calling CPU owns this reservation.
                    default -> objectMethod(proxy, method, args);
                });
        energyAccount = Proxy.newProxyInstance(energy.getClassLoader(), new Class<?>[]{energy},
                (proxy, method, args) -> {
                    if (method.getName().equals("reserve")) {
                        // prepareAllocated promises zero power; never silently waive a changed API's charge.
                        double amount = (double) args[0];
                        if (amount != 0.0) throw new IllegalStateException("Unexpected NeoECO energy debit: " + amount);
                        return paid;
                    }
                    return objectMethod(proxy, method, args);
                });
    }

    private static Object objectMethod(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "toString" -> "AE2LT CPU-owned allocated reservation";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> throw new UnsupportedOperationException(method.toString());
        };
    }

    private static Method method(Class<?> owner, String name, Class<?> result, boolean isStatic, Class<?>... args)
            throws NoSuchMethodException {
        Method method = owner.getMethod(name, args);
        if (Modifier.isStatic(method.getModifiers()) != isStatic
                || result != null && method.getReturnType() != result) {
            throw new NoSuchMethodException("Incompatible NeoECO method: " + method);
        }
        return method;
    }

    Prepared prepare(ICraftingProvider provider, IPatternDetails pattern, KeyCounter[] inputs,
            long offered, Level level, UUID job) {
        Object value = invoke(prepare, null, provider, pattern, inputs, offered, level, job);
        if (value == null) return null;
        long crafts = (long) invoke(count, value);
        if (crafts <= 0 || crafts > offered || (double) invoke(power, value) != 0.0) {
            throw new IllegalStateException("Invalid NeoECO allocated batch; no submission made");
        }
        return new Prepared(value, crafts);
    }

    boolean pushSingle(ICraftingProvider provider, IPatternDetails pattern, KeyCounter[] inputs, UUID job) {
        return (boolean) invoke(pushForJob, null, provider, pattern, inputs, job);
    }

    final class Prepared {
        private final Object value;
        final long crafts;
        private boolean submitted;

        private Prepared(Object value, long crafts) { this.value = value; this.crafts = crafts; }

        boolean submit() {
            if (submitted) throw new IllegalStateException("NeoECO batch already submitted");
            submitted = true;
            return (boolean) invoke(submit, value, energyAccount);
        }
    }

    static Object invoke(Method method, Object owner, Object... args) {
        try { return method.invoke(owner, args); }
        catch (InvocationTargetException failure) {
            // In particular, preserve ECOIndeterminateBatchException. The executor quarantines
            // an ambiguous transfer; it must never refund it or try ordinary dispatch a second time.
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("NeoECO API invocation failed", cause);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("NeoECO API invocation failed", failure);
        }
    }
}
