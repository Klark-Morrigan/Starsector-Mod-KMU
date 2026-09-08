package kmu.maplayers.base.sidebar;

import kmu.KmuMod;
import kmu.maplayers.base.layer.ScreenMemoryScopes;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins that a picker's scope cannot be built without a list to name, that two mods naming one list
 * are two lists, and that the screen is dropped on the way from a selection slot.
 *
 * <p>Compared rather than spelled, since nothing addressed by this is serialised: it is a map key,
 * so what has to hold is that equal scopes are one entry and unequal ones are two.
 */
final class PickerScopeTest {

    private static final MapLayerStoreNamespace NAMESPACE =
        MapLayerStoreNamespaces.createStandInNamespace();

    private static final String SCOPE_ID = "factions";

    @Nested
    class Constructor {

        @Test
        void refusesAScopeNamingNoList() {

            assertThatThrownBy(() -> new PickerScope(NAMESPACE, null))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void refusesAScopeWhoseListNameResolvesToNothing() {
            // A blank id composes every list on a panel to one entry, so the picker that lost its id
            // would preview whatever row the pointer last rested on in any of them.
            assertThatThrownBy(() -> new PickerScope(NAMESPACE, "   "))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void holdsTwoModsNamingOneListAsTwoLists() {
            // The whole of what the namespace is doing here: the id is opaque, so two mods listing a
            // view called "factions" must not share an entry.
            assertThat(new PickerScope(KmuMod.MAP_STORE_NAMESPACE, SCOPE_ID))
                .isNotEqualTo(new PickerScope(NAMESPACE, SCOPE_ID));
        }
    }

    @Nested
    class ResolveScopeOf {

        @Test
        void resolveScopeOfTakesTheSlotsModAndList() {

            var slot = new SelectionSlot(
                new ScreenSelectionSlot(NAMESPACE, ScreenMemoryScopes.createStandInScreen()),
                SCOPE_ID);

            assertThat(PickerScope.resolveScopeOf(slot))
                .isEqualTo(new PickerScope(NAMESPACE, SCOPE_ID));
        }

        @Test
        void resolveScopeOfDropsTheScreenTheSlotWasPickedOn() {
            // Deliberate, and the one axis this address does not carry: the pass that reads a hover
            // back is drawing the screen that is up and has no pick of its own to resolve one from,
            // so a hover reported on either panel has to be the same entry.
            var slot = new SelectionSlot(
                new ScreenSelectionSlot(NAMESPACE, ScreenMemoryScopes.createStandInScreen()),
                SCOPE_ID);

            var otherScreenSlot = new SelectionSlot(
                new ScreenSelectionSlot(NAMESPACE, ScreenMemoryScopes.createOtherStandInScreen()),
                SCOPE_ID);

            assertThat(PickerScope.resolveScopeOf(slot))
                .isEqualTo(PickerScope.resolveScopeOf(otherScreenSlot));
        }
    }
}
