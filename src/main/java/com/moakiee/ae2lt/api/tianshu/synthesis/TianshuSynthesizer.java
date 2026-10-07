package com.moakiee.ae2lt.api.tianshu.synthesis;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Optional synthesis capability. The caller retains ownership of its plan, retries and task lifecycle. */
public interface TianshuSynthesizer {
    int API_VERSION = 1;
    ResourceLocation CAPABILITY_ID = ResourceLocation.fromNamespaceAndPath("ae2lt", "tianshu_synthesizer");

    SynthesizerCapability inspect(SynthesisRequest request);

    /**
     * Server-thread only. The same nonce must describe exactly the same request.
     * A retryable zero-acceptance result may be retried with that nonce; partial
     * and successful submissions replay their receipt without dispatching again.
     * Receipts are instance-local and keep 1024 completed requests, not durable
     * across unload/restart. An indeterminate dispatch is pinned until unload.
     * @throws com.moakiee.ae2lt.api.crafting.IndeterminateSubmissionException
     * when dispatch may have moved inputs; do not refund or automatically resubmit
     */
    SynthesisSubmission submit(SynthesisRequest request);

    record SynthesisRequest(AEItemKey processingId, List<List<GenericStack>> inputsPerCraft,
            long requestedAmount, ResourceLocation targetCapabilityId, int apiVersion, UUID nonce) {
        public SynthesisRequest {
            Objects.requireNonNull(processingId, "processingId");
            Objects.requireNonNull(targetCapabilityId, "targetCapabilityId");
            Objects.requireNonNull(nonce, "nonce");
            if (requestedAmount <= 0L) throw new IllegalArgumentException("requestedAmount must be positive");
            inputsPerCraft = List.copyOf(inputsPerCraft.stream().map(List::copyOf).toList());
            if (inputsPerCraft.stream().flatMap(List::stream).anyMatch(s -> s.amount() <= 0L)) {
                throw new IllegalArgumentException("input amounts must be positive");
            }
        }
    }

    record SynthesizerCapability(int capabilityVersion, ResourceLocation capabilityId,
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
