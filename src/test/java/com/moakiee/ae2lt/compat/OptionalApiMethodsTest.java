package com.moakiee.ae2lt.compat;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class OptionalApiMethodsTest {
    private static int initializations;

    @Test
    void discoveryDoesNotRunOptionalStaticInitializers() throws Exception {
        var type = OptionalApiMethods.load(getClass().getClassLoader(),
                getClass().getName() + "$UninitializedApi");
        OptionalApiMethods.staticMethod(type, "query", boolean.class);
        assertEquals(0, initializations);
    }

    @Test
    void rejectsWrongReturnTypeStaticnessAndNonPublicOwner() {
        assertThrows(NoSuchMethodException.class,
                () -> OptionalApiMethods.instance(PublicApi.class, "query", long.class));
        assertThrows(NoSuchMethodException.class,
                () -> OptionalApiMethods.staticMethod(PublicApi.class, "query", boolean.class));
        assertThrows(NoSuchMethodException.class,
                () -> OptionalApiMethods.instance(HiddenApi.class, "query", boolean.class));
    }

    public static final class UninitializedApi {
        static { initializations++; }
        public static boolean query() { return true; }
    }
    public static final class PublicApi { public boolean query() { return true; } }
    private static final class HiddenApi { public boolean query() { return true; } }
}
