package com.moakiee.ae2lt.logic.tianshu.terminal;

import java.util.Objects;

import appeng.api.implementations.blockentities.PatternContainerGroup;

/** Revalidates a captured provider group without treating display styles as identity. */
public final class TianshuUploadTargetGroupMatcher {
    private TianshuUploadTargetGroupMatcher() {
    }

    public static boolean matches(PatternContainerGroup captured, PatternContainerGroup current) {
        if (captured == null || current == null
                || !Objects.equals(captured.icon(), current.icon())
                || !captured.name().getString().equals(current.name().getString())
                || captured.tooltip().size() != current.tooltip().size()) {
            return false;
        }
        for (int i = 0; i < captured.tooltip().size(); i++) {
            if (!captured.tooltip().get(i).getString().equals(current.tooltip().get(i).getString())) {
                return false;
            }
        }
        return true;
    }
}
