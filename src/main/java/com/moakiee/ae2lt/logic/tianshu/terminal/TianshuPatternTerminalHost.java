package com.moakiee.ae2lt.logic.tianshu.terminal;

import appeng.helpers.IPatternTerminalMenuHost;
import org.jetbrains.annotations.Nullable;

public interface TianshuPatternTerminalHost extends IPatternTerminalMenuHost, TianshuTerminalHost {
    TianshuEncodingMode getTianshuEncodingMode();
    void setTianshuEncodingMode(TianshuEncodingMode mode);

    default boolean isMaintainableView() {
        return false;
    }

    default void setMaintainableView(boolean enabled) {
    }

    /** Whether this host is currently backed by AE2WTLib's universal terminal item. */
    default boolean isUniversalWirelessTerminal() {
        return false;
    }

    @Nullable
    default ClosedLoopTerminalDraft getClosedLoopTerminalDraft() {
        return null;
    }

    default void setClosedLoopTerminalDraft(@Nullable ClosedLoopTerminalDraft draft) {
    }

    @Nullable
    default ProcessingPatternTerminalDraft getProcessingPatternTerminalDraft() {
        return null;
    }

    default void setProcessingPatternTerminalDraft(
            @Nullable ProcessingPatternTerminalDraft draft) {
    }

}
