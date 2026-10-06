package com.moakiee.ae2lt.api.lightning;

import java.util.Objects;

/**
 * Best-effort two-tier payment, not an atomic storage transaction. Call on the
 * server thread with a conforming handler. Persist returned refund debts before
 * allowing another operation; handlers may partially accept a refund.
 */
public final class LightningPayment {
    private LightningPayment() {}

    public record Result(boolean paid, long refundHv, long refundEhv) {
        public Result {
            if (refundHv < 0 || refundEhv < 0 || (paid && (refundHv != 0 || refundEhv != 0))) {
                throw new IllegalArgumentException("invalid refund debt");
            }
        }
    }

    public static Result pay(ILightningEnergyHandler storage, long hv, long ehv) {
        Objects.requireNonNull(storage);
        if (hv < 0 || ehv < 0) throw new IllegalArgumentException("negative cost");
        if ((hv > 0 && storage.extract(LightningTier.HIGH_VOLTAGE, hv, true) != hv)
                || (ehv > 0 && storage.extract(LightningTier.EXTREME_HIGH_VOLTAGE, ehv, true) != ehv)) {
            return new Result(false, 0, 0);
        }
        long takenHv = hv == 0 ? 0 : storage.extract(LightningTier.HIGH_VOLTAGE, hv, false);
        long takenEhv = takenHv == hv && ehv > 0
                ? storage.extract(LightningTier.EXTREME_HIGH_VOLTAGE, ehv, false) : 0;
        if (takenHv == hv && takenEhv == ehv) return new Result(true, 0, 0);
        long returnedHv = takenHv == 0 ? 0 : storage.insert(LightningTier.HIGH_VOLTAGE, takenHv, false);
        long returnedEhv = takenEhv == 0 ? 0 : storage.insert(LightningTier.EXTREME_HIGH_VOLTAGE, takenEhv, false);
        return new Result(false, takenHv - returnedHv, takenEhv - returnedEhv);
    }
}
