package com.moakiee.ae2lt.mixin.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.NumberEntryType;
import appeng.client.gui.WidgetContainer;
import appeng.client.gui.me.crafting.CraftAmountScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.NumberEntryWidget;
import appeng.menu.me.crafting.CraftAmountMenu;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.client.crafting.AE2LtCraftAmountScreen;
import com.moakiee.ae2lt.client.ldlib.LdlibResizableTextButton;
import com.moakiee.ae2lt.client.ldlib.LdlibNumberEntryWidget;
import com.moakiee.ae2lt.client.widgets.BigNumberEntry;
import com.moakiee.ae2lt.crafting.big.BigAmountMenu;
import com.moakiee.ae2lt.network.tianshu.ConfirmBigAmountPacket;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.math.BigInteger;

@Mixin(value = CraftAmountScreen.class, remap = false)
public abstract class BigCraftAmountScreenMixin extends AEBaseScreen<CraftAmountMenu> {
    @Shadow @Final private NumberEntryWidget amountToCraft;
    @Shadow @Final private Button next;
    @Unique private boolean ae2lt$restored;

    protected BigCraftAmountScreenMixin(
            CraftAmountMenu m, Inventory i, Component t, ScreenStyle s) {
        super(m, i, t, s);
    }

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE",
            target = "Lappeng/client/gui/WidgetContainer;addButton(Ljava/lang/String;Lnet/minecraft/network/chat/Component;Ljava/lang/Runnable;)Lappeng/client/gui/widgets/AE2Button;"))
    private AE2Button ae2lt$createNextButton(WidgetContainer widgets, String id, Component label,
            Runnable action, Operation<AE2Button> original) {
        if ((Object) this instanceof AE2LtCraftAmountScreen) {
            var button = new LdlibResizableTextButton(label, action);
            widgets.add(id, button);
            return button;
        }
        return original.call(widgets, id, label, action);
    }

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE",
            target = "Lappeng/client/gui/WidgetContainer;addNumberEntryWidget(Ljava/lang/String;Lappeng/client/gui/NumberEntryType;)Lappeng/client/gui/widgets/NumberEntryWidget;"))
    private NumberEntryWidget ae2lt$createAmount(WidgetContainer widgets, String id, NumberEntryType type,
            Operation<NumberEntryWidget> original) {
        if ((Object) this instanceof AE2LtCraftAmountScreen) {
            var target = menu.getWhatToCraft();
            var amount = new LdlibNumberEntryWidget(style,
                    target == null ? type : NumberEntryType.of(target.what()), true);
            widgets.add(id, amount);
            return amount;
        }
        return original.call(widgets, id, type);
    }

    @Inject(method = "updateBeforeRender", at = @At("TAIL"))
    private void ae2lt$update(CallbackInfo ci) {
        if (!(menu instanceof BigAmountMenu big)) return;
        if (big.ae2lt$bigAvailable() && ((Object) this).getClass() == CraftAmountScreen.class) {
            var whatToCraft = menu.getWhatToCraft();
            if (whatToCraft != null) {
                switchToScreen(new AE2LtCraftAmountScreen(menu, getTitle(), style));
            }
            return;
        }
        var widget = (BigNumberEntry) amountToCraft;
        ae2lt$syncAmountRange(big, widget);
        if (!big.ae2lt$longAvailable()) return;
        if (!ae2lt$restored && !big.ae2lt$initialAmount().isEmpty()) {
            widget.ae2lt$setBig(new BigInteger(big.ae2lt$initialAmount()));
            ae2lt$restored = true;
        }
        next.active = !ae2lt$hasRejectedEdit() && widget.ae2lt$getBig().isPresent();
    }

    @Inject(method = "confirm", at = @At("HEAD"), cancellable = true)
    private void ae2lt$confirm(CallbackInfo ci) {
        if (!(menu instanceof BigAmountMenu big)) return;
        var widget = (BigNumberEntry) amountToCraft;
        if (amountToCraft instanceof LdlibNumberEntryWidget) {
            ae2lt$syncAmountRange(big, widget);
            if (ae2lt$hasRejectedEdit()) {
                ci.cancel();
                return;
            }
        }
        if (!big.ae2lt$longAvailable()) return;
        var n = widget.ae2lt$getBig();
        if (n.isEmpty()) {
            ci.cancel();
            return;
        }
        // AE2's amount packet is int-sized. Our packet transports larger values, but the
        // server still uses the normal selected planner/CPU path up to Long.MAX_VALUE.
        if (n.get().compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
            PacketDistributor.sendToServer(
                    new ConfirmBigAmountPacket(
                            menu.containerId,
                            n.get(),
                            amountToCraft.startsWithEquals(),
                            hasShiftDown()));
            ci.cancel();
        }
    }

    @Unique
    private void ae2lt$syncAmountRange(BigAmountMenu big, BigNumberEntry widget) {
        if (amountToCraft instanceof LdlibNumberEntryWidget ldlib) {
            ldlib.setExtendedRange(big.ae2lt$longAvailable(), big.ae2lt$bigAvailable());
        } else {
            widget.ae2lt$setExtendedRange(big.ae2lt$longAvailable(), big.ae2lt$bigAvailable());
        }
    }

    @Unique
    private boolean ae2lt$hasRejectedEdit() {
        return amountToCraft instanceof LdlibNumberEntryWidget ldlib && ldlib.hasRejectedEdit();
    }
}
