package com.moakiee.ae2lt.mixin.client;

import appeng.client.gui.NumberEntryType;
import appeng.client.gui.widgets.*;
import appeng.core.localization.GuiText;

import com.moakiee.ae2lt.client.BigNumberEntry;
import com.moakiee.thunderbolt.core.storage.big.BigAmounts;

import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

import java.math.*;
import java.util.*;

/** Opt-in extension of the native input; all other number widgets retain their original limits. */
@Mixin(value = NumberEntryWidget.class, remap = false)
public abstract class BigNumberEntryWidgetMixin implements BigNumberEntry {
    @Shadow @Final private ConfirmableTextField textField;
    @Shadow private NumberEntryType type;
    @Shadow private ValidationIcon validationIcon;
    @Shadow @Final private int normalTextColor;
    @Shadow @Final private int errorTextColor;
    @Shadow private long minValue;
    @Shadow @Final private java.text.DecimalFormat decimalFormat;

    @Shadow
    private Optional<BigDecimal> getValueInternal() {
        throw new AssertionError();
    }

    @Shadow
    private void validate() {
        throw new AssertionError();
    }

    @Unique private boolean ae2lt$big;

    public void ae2lt$enableBig() {
        if (!ae2lt$big) {
            ae2lt$big = true;
            textField.setMaxLength(5000);
            decimalFormat.setMaximumIntegerDigits(5000);
            validate();
        }
    }

    public Optional<BigInteger> ae2lt$getBig() {
        try {
            var parsed = getValueInternal();
            if (parsed.isEmpty()) return Optional.empty();
            var value = parsed.get().multiply(BigDecimal.valueOf(type.amountPerUnit()));
            if (value.precision() - (long) value.scale() > 4933 || value.scale() > 5000)
                return Optional.empty();
            var amount = BigAmounts.nonNegative(value.toBigIntegerExact());
            return amount.compareTo(BigInteger.valueOf(minValue)) < 0
                    ? Optional.empty()
                    : Optional.of(amount);
        } catch (ArithmeticException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public void ae2lt$setBig(BigInteger amount) {
        // AE2 key units are powers of ten, so this conversion is exact.
        var value = new BigDecimal(amount).divide(BigDecimal.valueOf(type.amountPerUnit()));
        textField.setValue(decimalFormat.format(value));
    }

    @Inject(method = "validate", at = @At("HEAD"), cancellable = true)
    private void ae2lt$validate(CallbackInfo ci) {
        if (!ae2lt$big) return;
        var amount = ae2lt$getBig();
        var hints =
                amount.isPresent()
                        ? List.<Component>of(Component.literal(amount.get().toString()))
                        : List.<Component>of(GuiText.InvalidNumber.text());
        textField.setTextColor(amount.isPresent() ? normalTextColor : errorTextColor);
        textField.setTooltipMessage(hints);
        if (validationIcon != null) {
            validationIcon.setValid(amount.isPresent());
            validationIcon.setTooltip(hints);
        }
        ci.cancel();
    }

    @Inject(method = "addQty", at = @At("HEAD"), cancellable = true)
    private void ae2lt$add(long n, CallbackInfo ci) {
        if (!ae2lt$big) return;
        var current = ae2lt$getBig().orElse(BigInteger.ZERO);
        var next =
                current.add(
                        BigInteger.valueOf(n).multiply(BigInteger.valueOf(type.amountPerUnit())));
        if (current.equals(BigInteger.valueOf(type.amountPerUnit())) && n > 1)
            next = next.subtract(current);
        next = next.max(BigInteger.valueOf(minValue));
        if (next.bitLength() <= BigAmounts.MAX_BITS) ae2lt$setBig(next);
        ci.cancel();
    }
}
