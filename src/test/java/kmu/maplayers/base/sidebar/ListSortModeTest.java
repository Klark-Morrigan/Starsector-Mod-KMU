package kmu.maplayers.base.sidebar;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the seam's one piece of behaviour of its own: a mode that declares no trailing value shows
 * blank, so a layer whose rows carry no number gets a plain list without writing anything for it.
 * Everything else the interface declares is the implementing layer's to fill and is pinned where
 * it is filled.
 */
final class ListSortModeTest {

    @Nested
    class ResolveTrailingValue {

        @Test
        void resolveTrailingValueDefaultsToBlankForAModeThatDeclaresNone() {
            // The foreign fixture leaves the default in place, so its rows read as a plain list.
            assertThat(HazardSortMode.SEVERITY.resolveTrailingValue(new Hazard("Mild", 1, 5)))
                .isEmpty();
        }
    }
}
