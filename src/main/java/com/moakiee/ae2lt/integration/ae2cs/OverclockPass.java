package com.moakiee.ae2lt.integration.ae2cs;

/** Marks a processor pass that must skip its component and side-I/O tick. */
public interface OverclockPass {
    boolean ae2lt$isExtraPass();
}
