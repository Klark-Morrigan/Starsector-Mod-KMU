package kmu.maplayers.base.layer;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the shape of a per-screen key and that a scope cannot be built without a screen to name.
 *
 * <p>The shape is pinned against literal strings rather than against the composition that produces
 * them, since the point of the type is that every holder spells its slot the same way: a composition
 * checked against itself would pass whatever it composed, and a changed shape would surface as saves
 * silently opening at their defaults.
 */
final class ScreenMemoryScopeTest {

    private static final ScreenMemoryScope MAP_SCOPE = new ScreenMemoryScope("map");
    private static final ScreenMemoryScope INTEL_SCOPE = new ScreenMemoryScope("intel");

    private static final String PREFERENCE_KEY = "$kmu_political_name_format";

    @Nested
    class Constructor {

        @Test
        void refusesAScopeNamingNoScreen() {

            assertThatThrownBy(() -> new ScreenMemoryScope(null))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void refusesAScopeWhoseScreenNameResolvesToNothing() {
            // A blank segment composes every preference to its bare key with a trailing separator, and
            // both screens to the same slot - which reads as the panels sharing a preference rather than
            // as the missing segment it is.
            assertThatThrownBy(() -> new ScreenMemoryScope("   "))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void holdsTwoScopesNamingOneScreenAsTheSameScreen() {
            // A scope travels with a screen's picks and is compared, never identified, so a holder built
            // under a copy of a screen's scope has to read and write the slots the original does.
            assertThat(new ScreenMemoryScope("map"))
                .isEqualTo(MAP_SCOPE);
        }
    }

    @Nested
    class ResolveKeyFor {

        @Test
        void resolveKeyForAppendsTheScreensSegmentAfterThePreferencesOwnKey() {

            assertThat(MAP_SCOPE.resolveKeyFor(PREFERENCE_KEY))
                .isEqualTo("$kmu_political_name_format_map");
        }

        @Test
        void resolveKeyForGivesTheTwoScreensSeparateSlotsForOnePreference() {
            // The whole of what the type is for: one preference, one base key, two saves.
            assertThat(INTEL_SCOPE.resolveKeyFor(PREFERENCE_KEY))
                .isEqualTo("$kmu_political_name_format_intel");

            assertThat(MAP_SCOPE.resolveKeyFor(PREFERENCE_KEY))
                .isNotEqualTo(INTEL_SCOPE.resolveKeyFor(PREFERENCE_KEY));
        }
    }
}
