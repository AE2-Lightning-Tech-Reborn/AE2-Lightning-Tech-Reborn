package com.moakiee.ae2lt.logic;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import appeng.api.stacks.AEKey;
import net.minecraft.resources.ResourceLocation;

/**
 * Effective return filter: FE is never accepted. Other resources are unrestricted
 * when import filtering is disabled, otherwise limited to loaded pattern outputs.
 * <p>
 * STRICT outputs are matched by exact {@link AEKey} (including components/NBT).
 * ID_ONLY outputs are matched via {@link AEKey#dropSecondary()}, which strips
 * components but preserves key type — an AEItemKey will never match an
 * AEFluidKey even if they share the same registry id.
 */
public final class AllowedOutputFilter {
    private static final ResourceLocation FLUX_KEY_TYPE = new ResourceLocation("appflux", "flux");
    private static final ResourceLocation FE_ID = new ResourceLocation("appflux", "fe");
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
        if (isReturnableResource(key)) {
            strictOutputs.add(key);
        }
    }

    public void allowIdOnly(AEKey key) {
        Objects.requireNonNull(key, "key");
        if (isReturnableResource(key)) {
            idOnlyKeys.add(key.dropSecondary());
        }
    }

    public boolean isEmpty() {
        return !unrestricted && strictOutputs.isEmpty() && idOnlyKeys.isEmpty();
    }

    public boolean matches(AEKey key) {
        Objects.requireNonNull(key, "key");
        if (!isReturnableResource(key)) {
            return false;
        }
        if (unrestricted) {
            return true;
        }
        if (strictOutputs.contains(key)) {
            return true;
        }
        return idOnlyKeys.contains(key.dropSecondary());
    }

    private static boolean isReturnableResource(AEKey key) {
        // Match identifiers without loading optional AppFlux classes. An item or
        // fluid with the same resource ID remains returnable; induction is output-only.
        return !FLUX_KEY_TYPE.equals(key.getType().getId()) || !FE_ID.equals(key.getId());
    }

    @Override
    public String toString() {
        return "AllowedOutputFilter[unrestricted=" + unrestricted
                + ", strict=" + strictOutputs + ", idOnly=" + idOnlyKeys + "]";
    }
}
