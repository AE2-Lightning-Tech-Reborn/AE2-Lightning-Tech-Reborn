package com.moakiee.ae2lt.asm;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.spongepowered.asm.mixin.injection.At;

/** WrapOperation with private scratch locals; selectors must include development and SRG names. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface IsolatedWrapOperation {
    String[] method();

    At[] at();

    boolean remap() default false;

    int require() default 1;
}
