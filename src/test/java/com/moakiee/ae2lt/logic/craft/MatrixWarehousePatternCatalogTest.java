package com.moakiee.ae2lt.logic.craft;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import com.moakiee.thunderbolt.core.crafting.pattern.IProviderLookupPattern;

class MatrixWarehousePatternCatalogTest {
    @Test
    void onlyTheWarehousesCurrentRegistrationAcceptsEquivalentAndWrappedDetails() {
        var catalog = new MatrixWarehousePatternCatalog();
        var first = new Pattern("first");
        var second = new Pattern("second");
        catalog.replace(List.of(first));

        assertTrue(catalog.contains(first));
        assertTrue(catalog.contains(new Pattern("first")));
        assertTrue(catalog.contains(new Wrapper(new Pattern("first"))));
        assertFalse(catalog.contains(second));

        catalog.replace(List.of(second));
        assertFalse(catalog.contains(first));
        assertFalse(catalog.contains(new Wrapper(first)));
        assertTrue(catalog.contains(second));

    }

    private record Pattern(String id, AEItemKey definition) implements IPatternDetails {
        private Pattern(String id) {
            this(id, null);
        }

        @Override
        public AEItemKey getDefinition() {
            return definition;
        }

        @Override
        public IInput[] getInputs() {
            return new IInput[0];
        }

        @Override
        public GenericStack[] getOutputs() {
            return new GenericStack[0];
        }
    }

    private record Wrapper(IPatternDetails delegate) implements IPatternDetails, IProviderLookupPattern {
        @Override
        public IPatternDetails providerLookupPattern() {
            return delegate;
        }

        @Override
        public AEItemKey getDefinition() {
            return delegate.getDefinition();
        }

        @Override
        public IInput[] getInputs() {
            return delegate.getInputs();
        }

        @Override
        public GenericStack[] getOutputs() {
            return delegate.getOutputs();
        }
    }
}
