package com.moakiee.ae2lt.client.ldlib;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import appeng.api.config.FullnessMode;
import appeng.api.config.FuzzyMode;
import appeng.api.config.OperationMode;
import appeng.api.config.RedstoneMode;
import appeng.api.config.Setting;
import appeng.api.config.Settings;
import appeng.client.gui.Icon;
import appeng.core.localization.ButtonToolTips;
import appeng.core.network.serverbound.ConfigButtonPacket;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class LdlibSettingButton extends LdlibAe2Button {
    private record Appearance(Icon icon, ButtonToolTips description) {
    }

    private final Supplier<Appearance> current;
    private final Component title;

    private LdlibSettingButton(Setting<?> setting, BooleanSupplier backwards, ButtonToolTips title,
            Supplier<Appearance> current) {
        super(16, title.text(), () -> PacketDistributor.sendToServer(
                new ConfigButtonPacket(setting, backwards.getAsBoolean())));
        this.current = current;
        this.title = getMessage();
        setMessage(Component.empty());
        toolbarFace(IGuiTexture.dynamic(() -> LdlibAe2Icons.texture(current.get().icon())), false);
    }

    @Override
    public List<Component> getTooltipMessage() {
        return List.of(title, current.get().description().text());
    }

    public static LdlibSettingButton fuzzy(Supplier<FuzzyMode> mode, BooleanSupplier backwards) {
        return new LdlibSettingButton(Settings.FUZZY_MODE, backwards, ButtonToolTips.FuzzyMode,
                () -> fuzzyAppearance(mode.get()));
    }

    public static LdlibAe2Button bareFuzzy(Supplier<FuzzyMode> mode, Runnable cycle) {
        var title = ButtonToolTips.FuzzyMode.text();
        return LdlibAe2Button.bareIcon(() -> fuzzyAppearance(mode.get()).icon(), title, cycle,
                () -> List.of(title, fuzzyAppearance(mode.get()).description().text()));
    }

    private static Appearance fuzzyAppearance(FuzzyMode mode) {
        return switch (mode) {
            case PERCENT_25 -> new Appearance(Icon.FUZZY_PERCENT_25, ButtonToolTips.FZPercent_25);
            case PERCENT_50 -> new Appearance(Icon.FUZZY_PERCENT_50, ButtonToolTips.FZPercent_50);
            case PERCENT_75 -> new Appearance(Icon.FUZZY_PERCENT_75, ButtonToolTips.FZPercent_75);
            case PERCENT_99 -> new Appearance(Icon.FUZZY_PERCENT_99, ButtonToolTips.FZPercent_99);
            case IGNORE_ALL -> new Appearance(Icon.FUZZY_IGNORE, ButtonToolTips.FZIgnoreAll);
        };
    }

    public static LdlibSettingButton fullness(Supplier<FullnessMode> mode, BooleanSupplier backwards) {
        return new LdlibSettingButton(Settings.FULLNESS_MODE, backwards, ButtonToolTips.OperationMode,
                () -> switch (mode.get()) {
                    case EMPTY -> new Appearance(Icon.FULLNESS_EMPTY, ButtonToolTips.MoveWhenEmpty);
                    case HALF -> new Appearance(Icon.FULLNESS_HALF, ButtonToolTips.MoveWhenWorkIsDone);
                    case FULL -> new Appearance(Icon.FULLNESS_FULL, ButtonToolTips.MoveWhenFull);
                });
    }

    public static LdlibSettingButton transferDirection(Supplier<OperationMode> mode, BooleanSupplier backwards) {
        return new LdlibSettingButton(Settings.OPERATION_MODE, backwards, ButtonToolTips.TransferDirection,
                () -> switch (mode.get()) {
                    case FILL -> new Appearance(Icon.ARROW_LEFT, ButtonToolTips.TransferToStorageCell);
                    case EMPTY -> new Appearance(Icon.ARROW_RIGHT, ButtonToolTips.TransferToNetwork);
                });
    }

    public static LdlibSettingButton redstone(Supplier<RedstoneMode> mode, BooleanSupplier backwards) {
        return new LdlibSettingButton(Settings.REDSTONE_CONTROLLED, backwards, ButtonToolTips.RedstoneMode,
                () -> switch (mode.get()) {
                    case IGNORE -> new Appearance(Icon.REDSTONE_IGNORE, ButtonToolTips.AlwaysActive);
                    case LOW_SIGNAL -> new Appearance(Icon.REDSTONE_LOW, ButtonToolTips.ActiveWithoutSignal);
                    case HIGH_SIGNAL -> new Appearance(Icon.REDSTONE_HIGH, ButtonToolTips.ActiveWithSignal);
                    case SIGNAL_PULSE -> new Appearance(Icon.REDSTONE_PULSE, ButtonToolTips.ActiveOnPulse);
                });
    }
}
