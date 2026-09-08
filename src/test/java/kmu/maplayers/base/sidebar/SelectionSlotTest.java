package kmu.maplayers.base.sidebar;

import kmu.maplayers.base.layer.ScreenMemoryScopes;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins that a selection slot cannot be built without a list to name.
 *
 * <p>Only the guard. What the slot composes is pinned against literal keys in the suites of the
 * stores addressed by it, since those keys are what every existing save holds - checking the
 * composition here as well would pin one spelling twice.
 */
final class SelectionSlotTest {

    private static final ScreenSelectionSlot SCREEN_SLOT = new ScreenSelectionSlot(
        MapLayerStoreNamespaces.createStandInNamespace(),
        ScreenMemoryScopes.createStandInScreen());

    @Nested
    class Constructor {

        @Test
        void refusesASlotNamingNoList() {

            assertThatThrownBy(() -> new SelectionSlot(SCREEN_SLOT, null))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void refusesASlotWhoseListNameResolvesToNothing() {
            // A blank id composes every scope on a screen to one key, so the picker that lost its id
            // would quietly share a stored pick with every other picker on the panel.
            assertThatThrownBy(() -> new SelectionSlot(SCREEN_SLOT, "   "))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
