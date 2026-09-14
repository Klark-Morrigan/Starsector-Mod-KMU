package kmu.maplayers.base.sidebar;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the shape of a namespaced store key, that two mods never compose one store to the same key, and
 * that a namespace cannot be built without a mod to name.
 *
 * <p>The shape is pinned against literal strings rather than against the composition that produces
 * them, for the reason the per-screen key is: the point of the type is that every store spells its key
 * the same way, and a composition checked against itself would pass whatever it composed.
 */
final class MapLayerStoreNamespaceTest {

    private static final MapLayerStoreNamespace NAMESPACE = new MapLayerStoreNamespace("$amod_map_");

    private static final MapLayerStoreNamespace OTHER_NAMESPACE =
        new MapLayerStoreNamespace("$bmod_map_");

    private static final String STORE_KEY = "sort_mode_";

    @Nested
    class Constructor {

        @Test
        void refusesANamespaceNamingNoMod() {

            assertThatThrownBy(() -> new MapLayerStoreNamespace(null))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void refusesANamespaceWhosePrefixResolvesToNothing() {
            // A blank prefix puts the mod that has one in the un-namespaced keys, where it would read
            // and write the picks of whichever mod's keys already spell themselves that way.
            assertThatThrownBy(() -> new MapLayerStoreNamespace("   "))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class ResolveNamespacedKey {

        @Test
        void resolveNamespacedKeyLeadsTheStoresOwnKeyWithTheModsPrefix() {

            assertThat(NAMESPACE.resolveNamespacedKey(STORE_KEY))
                .isEqualTo("$amod_map_sort_mode_");
        }

        @Test
        void resolveNamespacedKeyGivesTwoModsSeparateKeysForOneStore() {
            // The whole of what the type is for: one store, one key of its own, two mods' saves.
            assertThat(OTHER_NAMESPACE.resolveNamespacedKey(STORE_KEY))
                .isEqualTo("$bmod_map_sort_mode_");

            assertThat(NAMESPACE.resolveNamespacedKey(STORE_KEY))
                .isNotEqualTo(OTHER_NAMESPACE.resolveNamespacedKey(STORE_KEY));
        }

        @Test
        void resolveNamespacedKeyAppendsNoSeparatorOfItsOwn() {
            // The prefix carries its own, so what a namespace composes is exactly what the holding mod
            // already ships - a separator added here would move every frozen key one character.
            assertThat(new MapLayerStoreNamespace("$amod_map").resolveNamespacedKey(STORE_KEY))
                .isEqualTo("$amod_mapsort_mode_");
        }
    }
}
