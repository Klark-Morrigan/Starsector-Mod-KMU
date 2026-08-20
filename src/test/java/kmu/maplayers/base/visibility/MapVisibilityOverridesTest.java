package kmu.maplayers.base.visibility;

import kmlib.starsector.colonies.ColonyVisibility;
import kmlib.starsector.colonies.RevelationGate;

import kmu.settings.KmuMapLayerSettings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the framework's "no overrides" value, which is what every caller with no override to
 * apply passes. A widening defaulting to on would open the map up with nothing at the call
 * site to show it, so the default is worth asserting rather than assuming.
 *
 * <p>Beside it, the settings read: the two toggles in the same order, so a transposed mapping
 * would compile and read correctly while silently trading one for the other. Each case raises
 * exactly one toggle, which is what makes that swap fail.
 *
 * <p>And the gates, which no toggle reaches yet: a read that quietly dropped them would leave
 * every derelict in the sector on the map the moment its entity was found, with nothing in the
 * settings screen to explain it.
 *
 * <p>The construction guard is here on the same grounds. Every failure this value can carry
 * shows up as a map drawing the wrong amount rather than as anything that announces itself, so
 * refusing a rule nobody stated is worth pinning rather than trusting.
 */
class MapVisibilityOverridesTest {

    @Nested
    class None {

        @Test
        void appliesNeitherWidening() {

            var overrides = MapVisibilityOverrides.NONE;

            assertThat(overrides.colonyVisibility())
                .isEqualTo(ColonyVisibility.BASE_FOG);
            assertThat(overrides.isForcedOntoMap())
                .isFalse();
        }
    }

    @Nested
    class Constructor {

        @Test
        void rejectsAnUnstatedColonyRule() {
            // Every value of this record is built from the settings or from a rule its caller
            // already holds, so a null is that construction having gone wrong. Standing the fog
            // in would answer it with a map that draws less than it should, and nothing on screen
            // would report it.
            assertThatThrownBy(() -> new MapVisibilityOverrides(null, false))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class ReadFromLunaSettings {

        @Test
        void widensTheColonyRuleForTheUndiscoveredMarketsToggleAlone() {

            try (var settingsMock = mockStatic(KmuMapLayerSettings.class)) {

                settingsMock
                    .when(KmuMapLayerSettings::shouldShowUndiscoveredMarkets)
                    .thenReturn(true);
                settingsMock
                    .when(KmuMapLayerSettings::shouldShowHiddenSystems)
                    .thenReturn(false);

                var overrides = MapVisibilityOverrides.readFromLunaSettings();

                assertThat(overrides.colonyVisibility().shouldIncludeUndiscoveredMarkets())
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
                assertThat(overrides.colonyVisibility().shouldIncludeUndiscoveredMarkets())
                    .isFalse();
            }
        }

        @Test
        void holdsEveryRevelationGateWhicheverWayTheTogglesFall() {

            // Asserted against the enum's own values rather than against a list written out
            // here: a gate added later must reach the live rule without this case being
            // remembered, since a shape nobody thought to name is exactly the one that leaks.
            for (var isRevealing : new boolean[] {false, true}) {

                try (var settingsMock = mockStatic(KmuMapLayerSettings.class)) {

                    settingsMock
                        .when(KmuMapLayerSettings::shouldShowUndiscoveredMarkets)
                        .thenReturn(isRevealing);
                    settingsMock
                        .when(KmuMapLayerSettings::shouldShowHiddenSystems)
                        .thenReturn(false);

                    var overrides = MapVisibilityOverrides.readFromLunaSettings();

                    assertThat(overrides.colonyVisibility().revelationGates())
                        .containsExactlyInAnyOrder(RevelationGate.values());
                }
            }
        }
    }
}
