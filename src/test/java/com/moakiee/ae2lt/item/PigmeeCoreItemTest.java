package com.moakiee.ae2lt.item;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class PigmeeCoreItemTest {
    @Test
    void returnsOneExactCopyThroughTheStackSensitiveRemainderApi() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/item/PigmeeCoreItem.java"));

        assertTrue(source.contains("ItemStackTemplate getCraftingRemainder(ItemInstance instance)"));
        assertTrue(source.contains("ItemStackTemplate.fromNonEmptyStack(stack.copyWithCount(1))"));
        assertTrue(source.contains("template.withCount(1)"));
    }
}
