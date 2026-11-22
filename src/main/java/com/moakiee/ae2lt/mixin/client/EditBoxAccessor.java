package com.moakiee.ae2lt.mixin.client;

import java.util.function.BiFunction;
import java.util.function.Predicate;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Copies an accepted hosted edit without invoking EditBox.setValue's two notifications. */
@Mixin(EditBox.class)
public interface EditBoxAccessor {
    @Accessor("value")
    void ae2lt$setEditorValue(String value);

    @Accessor("filter")
    Predicate<String> ae2lt$getEditorFilter();

    @Accessor("highlightPos")
    int ae2lt$getEditorAnchor();

    @Accessor("cursorPos")
    void ae2lt$setEditorCursor(int cursor);

    @Accessor("highlightPos")
    void ae2lt$setEditorAnchor(int anchor);

    @Accessor("displayPos")
    int ae2lt$getEditorDisplayPos();

    @Accessor("displayPos")
    void ae2lt$setEditorDisplayPos(int position);

    @Accessor("font")
    Font ae2lt$getEditorFont();

    @Accessor("formatter")
    BiFunction<String, Integer, FormattedCharSequence> ae2lt$getEditorFormatter();

    @Accessor("suggestion")
    String ae2lt$getEditorSuggestion();

    @Accessor("focusedTime")
    long ae2lt$getEditorFocusedTime();

    @Invoker("getMaxLength")
    int ae2lt$getEditorMaxLength();

    @Invoker("onValueChange")
    void ae2lt$notifyEditorChange(String value);
}
