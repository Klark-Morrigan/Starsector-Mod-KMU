package kmu.maplayers.politicalmap.base;

import kmu.maplayers.base.visibility.MapVisibilityOverrides;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the conversion into the framework's visibility overrides. Both sides are two
 * booleans in the same order, so a transposed mapping would compile and read correctly
 * while silently trading one reveal for the other - each case here raises exactly one
 * toggle, which is what makes that swap fail.
 *
 * <p>{@link PoliticalMapDevOverrides#readFromLunaSettings()} is not exercised: it is the
 * settings read itself, and its whole purpose is to be the only thing here that touches
 * LunaLib.
 */
class PoliticalMapDevOverridesTest {

    @Nested
    class ResolveVisibilityOverrides {

        @Test
        void answersTheFrameworkNoOverrideValueWhenNoRevealIsOn() {
            assertThat(PoliticalMapDevOverrides.NONE.resolveVisibilityOverrides())
                    .isEqualTo(MapVisibilityOverrides.NONE);
        }

        @Test
        void mapsForcingAllSystemsOntoTheForcedOntoMapWidening() {
            var overrides = new PoliticalMapDevOverrides(false, true);

            var converted = overrides.resolveVisibilityOverrides();

            assertThat(converted.isForcedOntoMap()).isTrue();
            assertThat(converted.shouldIncludeUndiscoveredMarkets()).isFalse();
        }

        @Test
        void mapsShowingAllFactionsOntoTheUndiscoveredMarketWidening() {
            var overrides = new PoliticalMapDevOverrides(true, false);

            var converted = overrides.resolveVisibilityOverrides();

            assertThat(converted.shouldIncludeUndiscoveredMarkets()).isTrue();
            assertThat(converted.isForcedOntoMap()).isFalse();
        }
    }
}
