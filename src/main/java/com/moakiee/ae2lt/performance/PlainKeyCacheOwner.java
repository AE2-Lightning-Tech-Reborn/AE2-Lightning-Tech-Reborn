package com.moakiee.ae2lt.performance;

/** One bounded cache slot per registered item/fluid, supplied by the platform mixins. */
public interface PlainKeyCacheOwner {
    PlainKeyReuse.Entry ae2lt$getPlainKey();
    void ae2lt$setPlainKey(PlainKeyReuse.Entry entry);
}
