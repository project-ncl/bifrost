package org.jboss.pnc.bifrost;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LifecycleTest {

    @Test
    void podOrdinalNumber() {
        assertEquals(0, Lifecycle.podOrdinalNumber("liverpool-0"));
        assertEquals(0, Lifecycle.podOrdinalNumber("liverpool-00"));
        assertEquals(5, Lifecycle.podOrdinalNumber("liverpool-05"));
        assertEquals(57, Lifecycle.podOrdinalNumber("liverpool-57"));
        assertEquals(10, Lifecycle.podOrdinalNumber("liverpool-10"));
        assertEquals(315, Lifecycle.podOrdinalNumber("liverpool-315"));
    }
}