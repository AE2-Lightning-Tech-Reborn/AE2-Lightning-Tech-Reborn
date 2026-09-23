package com.moakiee.ae2lt.api.tianshu.synthesis;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.Identifier;

/** Optional synthesis capability. The caller retains ownership of its plan, retries and task lifecycle. */
public interface TianshuSynthesizer {
    int API_VERSION = 1;
    Identifier CAPABILITY_ID = Identifier.fromNamespaceAndPath("ae2lt", "tianshu_synthesizer");

    SynthesizerCapability inspect(SynthesisRequest request);

    SynthesisSubmission submit(SynthesisRequest request);

    record SynthesisRequest(AEItemKey processingId, List<List<GenericStack>> inputsPerCraft,
            long requestedAmount, Identifier targetCapabilityId, int apiVersion, UUID nonce) {
        public SynthesisRequest {
            Objects.requireNonNull(processingId, "processingId");
            Objects.requireNonNull(targetCapabilityId, "targetCapabilityId");
            Objects.requireNonNull(nonce, "nonce");
            if (requestedAmount <= 0L) throw new IllegalArgumentException("requestedAmount must be positive");
            inputsPerCraft = List.copyOf(inputsPerCraft.stream().map(List::copyOf).toList());
        }
    }

    record SynthesizerCapability(int capabilityVersion, Identifier capabilityId,
            long acceptedAmount, long maxSafeBatch, RejectionReason rejectionReason) {
        public SynthesizerCapability {
            Objects.requireNonNull(capabilityId, "capabilityId");
            Objects.requireNonNull(rejectionReason, "rejectionReason");
            if (acceptedAmount < 0L || maxSafeBatch < acceptedAmount) throw new IllegalArgumentException("invalid amounts");
        }
    }

    record SynthesisSubmission(Status status, long acceptedAmount, List<GenericStack> resultSnapshot,
            long unacceptedAmount, boolean retryable, RejectionReason rejectionReason) {
        public SynthesisSubmission {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(rejectionReason, "rejectionReason");
            resultSnapshot = List.copyOf(resultSnapshot);
            if (acceptedAmount < 0L || unacceptedAmount < 0L) throw new IllegalArgumentException("invalid amounts");
        }
    }

    enum Status { ACCEPTED, PARTIAL, REJECTED }
    enum RejectionReason { NONE, BUSY, UNSUPPORTED_PROCESSING, NO_CAPACITY, VERSION_MISMATCH,
        CAPABILITY_MISMATCH, INVALID_REQUEST, NOT_SERVER_THREAD, SUBMISSION_FAILED }
}
