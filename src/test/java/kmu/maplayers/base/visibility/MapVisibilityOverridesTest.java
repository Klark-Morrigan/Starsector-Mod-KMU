package kmu.maplayers.base.visibility;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the framework's "no overrides" value, which is what every caller with no reveal to
 * apply passes. A widening defaulting to on would open the map up with nothing at the call
 * site to show it, so the default is worth asserting rather than assuming.
 */
class MapVisibilityOverridesTest {

    @Nested
    class None {

        @Test
        void appliesNeitherWidening() {
            var overrides = MapVisibilityOverrides.NONE;

            assertThat(overrides.shouldIncludeUndiscoveredMarkets()).isFalse();
            assertThat(overrides.isForcedOntoMap()).isFalse();
        }
    }
}
