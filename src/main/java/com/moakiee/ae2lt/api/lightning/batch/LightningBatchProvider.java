package com.moakiee.ae2lt.api.lightning.batch;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.Identifier;

/** Optional server-side batch execution capability. It never owns the caller's plan or task. */
public interface LightningBatchProvider {
    int API_VERSION = 1;
    Identifier CAPABILITY_ID = Identifier.fromNamespaceAndPath("ae2lt", "lightning_batch");

    default int capabilityVersion() {
        return API_VERSION;
    }

    default Identifier capabilityId() {
        return CAPABILITY_ID;
    }

    BatchCapability inspect(BatchRequest request);

    BatchSubmission submit(BatchRequest request);

    record BatchRequest(
            int apiVersion,
            Identifier targetCapabilityId,
            AEItemKey processingId,
            List<List<GenericStack>> inputsPerCraft,
            long requestedAmount,
            UUID nonce) {
        public BatchRequest {
            Objects.requireNonNull(targetCapabilityId, "targetCapabilityId");
            Objects.requireNonNull(processingId, "processingId");
            Objects.requireNonNull(nonce, "nonce");
            if (requestedAmount <= 0L) throw new IllegalArgumentException("requestedAmount must be positive");
            inputsPerCraft = List.copyOf(Objects.requireNonNull(inputsPerCraft, "inputsPerCraft").stream()
                    .map(List::copyOf)
                    .toList());
        }
    }

    record BatchCapability(
            int capabilityVersion,
            Identifier capabilityId,
            long acceptedAmount,
            long maxSafeBatch,
            int multiplier,
            RejectionReason rejectionReason) {
        public BatchCapability {
            Objects.requireNonNull(capabilityId, "capabilityId");
            Objects.requireNonNull(rejectionReason, "rejectionReason");
            if (acceptedAmount < 0L || maxSafeBatch < 0L || multiplier < 1) {
                throw new IllegalArgumentException("negative capability amount or invalid multiplier");
            }
            if (acceptedAmount > maxSafeBatch) throw new IllegalArgumentException("acceptedAmount exceeds maxSafeBatch");
        }

        public static BatchCapability rejected(RejectionReason reason) {
            return new BatchCapability(API_VERSION, CAPABILITY_ID, 0L, 0L, 1, reason);
        }
    }

    record BatchSubmission(
            SubmissionStatus status,
            long acceptedAmount,
            List<GenericStack> resultSnapshot,
            long unacceptedAmount,
            boolean retryable,
            RejectionReason rejectionReason) {
        public BatchSubmission {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(rejectionReason, "rejectionReason");
            resultSnapshot = List.copyOf(Objects.requireNonNull(resultSnapshot, "resultSnapshot"));
            if (acceptedAmount < 0L || unacceptedAmount < 0L) {
                throw new IllegalArgumentException("submission amounts must not be negative");
            }
        }

        public static BatchSubmission rejected(long requested, RejectionReason reason, boolean retryable) {
            return new BatchSubmission(SubmissionStatus.REJECTED, 0L, List.of(), requested, retryable, reason);
        }
    }

    enum SubmissionStatus { ACCEPTED, PARTIAL, REJECTED }

    enum RejectionReason {
        NONE,
        PROVIDER_BUSY,
        UNSUPPORTED_PROCESSING,
        NO_CAPACITY,
        VERSION_MISMATCH,
        CAPABILITY_MISMATCH,
        INVALID_REQUEST,
        NOT_SERVER_THREAD,
        SUBMISSION_FAILED
    }
}
