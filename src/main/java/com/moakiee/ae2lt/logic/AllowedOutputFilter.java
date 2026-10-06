package com.moakiee.ae2lt.logic;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import appeng.api.stacks.AEKey;

/**
 * Output filter used by auto-return to decide whether a machine stack belongs
 * to one of the patterns loaded in the provider.
 * <p>
 * STRICT outputs are matched by exact {@link AEKey} (including components/NBT).
 * ID_ONLY outputs are matched via {@link AEKey#dropSecondary()}, which strips
 * components but preserves key type — an AEItemKey will never match an
 * AEFluidKey even if they share the same registry id.
 */
public final class AllowedOutputFilter {
    private static final AllowedOutputFilter UNRESTRICTED = new AllowedOutputFilter(true);

    private final boolean unrestricted;
    private final Set<AEKey> strictOutputs = new LinkedHashSet<>();
    private final Set<AEKey> idOnlyKeys = new LinkedHashSet<>();

    public AllowedOutputFilter() {
        this(false);
    }

    private AllowedOutputFilter(boolean unrestricted) {
        this.unrestricted = unrestricted;
    }

    public static AllowedOutputFilter unrestricted() {
        return UNRESTRICTED;
    }

    public void allowStrict(AEKey key) {
        Objects.requireNonNull(key, "key");
        strictOutputs.add(key);
    }

    public void allowIdOnly(AEKey key) {
        Objects.requireNonNull(key, "key");
        idOnlyKeys.add(key.dropSecondary());
    }

    public boolean isEmpty() {
        return !unrestricted && strictOutputs.isEmpty() && idOnlyKeys.isEmpty();
    }

    public boolean matches(AEKey key) {
        Objects.requireNonNull(key, "key");
        if (unrestricted) {
            return true;
        }
        if (strictOutputs.contains(key)) {
            return true;
        }
        return idOnlyKeys.contains(key.dropSecondary());
    }

    @Override
    public String toString() {
        return "AllowedOutputFilter[unrestricted=" + unrestricted
                + ", strict=" + strictOutputs + ", idOnly=" + idOnlyKeys + "]";
    }
}
