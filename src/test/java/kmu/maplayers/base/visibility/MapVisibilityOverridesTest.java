package kmu.maplayers.base.visibility;

import kmu.settings.KmuMapLayerSettings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the framework's "no overrides" value, which is what every caller with no reveal to
 * apply passes. A widening defaulting to on would open the map up with nothing at the call
 * site to show it, so the default is worth asserting rather than assuming.
 *
 * <p>Beside it, the settings read: two booleans in the same order, so a transposed mapping
 * would compile and read correctly while silently trading one widening for the other. Each
 * case raises exactly one toggle, which is what makes that swap fail.
 */
class MapVisibilityOverridesTest {

    @Nested
    class None {

        @Test
        void appliesNeitherWidening() {

            var overrides = MapVisibilityOverrides.NONE;

            assertThat(overrides.shouldIncludeUndiscoveredMarkets())
                .isFalse();
            assertThat(overrides.isForcedOntoMap())
                .isFalse();
        }
    }

    @Nested
    class ReadFromLunaSettings {

        @Test
        void widensTheInhabitationReadForTheUndiscoveredMarketsToggleAlone() {

            try (var settingsMock = mockStatic(KmuMapLayerSettings.class)) {

                settingsMock
                    .when(KmuMapLayerSettings::shouldShowUndiscoveredMarkets)
                    .thenReturn(true);
                settingsMock
                    .when(KmuMapLayerSettings::shouldShowHiddenSystems)
                    .thenReturn(false);

                var overrides = MapVisibilityOverrides.readFromLunaSettings();

                assertThat(overrides.shouldIncludeUndiscoveredMarkets())
                    .isTrue();
                assertThat(overrides.isForcedOntoMap())
                    .isFalse();
            }
        }

        @Test
        void forcesSystemsOntoTheMapForTheHiddenSystemsToggleAlone() {

            try (var settingsMock = mockStatic(KmuMapLayerSettings.class)) {

                settingsMock
                    .when(KmuMapLayerSettings::shouldShowUndiscoveredMarkets)
                    .thenReturn(false);
                settingsMock
                    .when(KmuMapLayerSettings::shouldShowHiddenSystems)
                    .thenReturn(true);

                var overrides = MapVisibilityOverrides.readFromLunaSettings();

                assertThat(overrides.isForcedOntoMap())
                    .isTrue();
                assertThat(overrides.shouldIncludeUndiscoveredMarkets())
                    .isFalse();
            }
        }
    }
}
