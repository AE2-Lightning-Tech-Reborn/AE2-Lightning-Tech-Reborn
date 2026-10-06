package com.moakiee.ae2lt.api.crafting;

import java.util.UUID;

/**
 * A provider started dispatch but could not determine how much was accepted.
 * Do not refund or resubmit with a new nonce: reconcile the provider first.
 */
public final class IndeterminateSubmissionException extends IllegalStateException {
    private final UUID nonce;

    public IndeterminateSubmissionException(UUID nonce, Throwable cause) {
        super("Submission outcome is unknown for nonce " + nonce, cause);
        this.nonce = nonce;
    }

    public UUID nonce() {
        return nonce;
    }
}
