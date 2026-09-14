package kmu.maplayers.base.visibility.systems;

import com.fs.starfarer.api.campaign.econ.MarketAPI.SurveyLevel;

import kmlib.starsector.markets.DecivilisedMarkets;

import kmu.maplayers.base.visibility.colonies.RevelationGate;
import kmu.settings.KmuMapVisibilitySettings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static kmu.maplayers.base.visibility.colonies.ColonyVisibility.BASE_FOG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the framework's base value, which is what every caller stating no rules of its own
 * passes. A widening defaulting to on would open the map up with nothing at the call site to
 * show it, so the default is worth asserting rather than assuming.
 *
 * <p>Beside it, the settings read: every knob mapped to the one thing it names, so a transposed
 * mapping would compile and read correctly while silently trading one for another. Each case moves
 * exactly one knob and asserts the others stayed put, which is what makes that swap fail.
 *
 * <p>The two spoiler toggles are held the same way, and one thing more: each is named for what
 * switching it on shows, so it is the toggle left off that carries a gate. A read that took them
 * the right way round but the wrong polarity would leave every derelict in the sector on the map
 * the moment its entity was found, with the settings screen saying the opposite.
 *
 * <p>The construction guard is here on the same grounds. Every failure this value can carry
 * shows up as a map drawing the wrong amount rather than as anything that announces itself, so
 * refusing a rule nobody stated is worth pinning rather than trusting.
 */
class MapVisibilityRulesTest {

    @Nested
    class Base {

        @Test
        void widensNothingAndForcesNothing() {

            var visibilityRules = MapVisibilityRules.BASE;

            assertThat(visibilityRules.colonyVisibility())
                .isEqualTo(BASE_FOG);
            assertThat(visibilityRules.isForcedOntoMap())
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
            assertThatThrownBy(() -> new MapVisibilityRules(null, false))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class ReadFromLunaSettings {

        @Test
        void widensTheColonyRuleForTheUndiscoveredMarketsToggleAlone() {

            try (var settingsMock = mockStatic(KmuMapVisibilitySettings.class)) {

                settingsMock
                    .when(KmuMapVisibilitySettings::shouldShowUndiscoveredMarkets)
                    .thenReturn(true);
                settingsMock
                    .when(KmuMapVisibilitySettings::shouldShowHiddenSystems)
                    .thenReturn(false);

                var visibilityRules = MapVisibilityRules.readFromLunaSettings();

                assertThat(visibilityRules.colonyVisibility().shouldIncludeUndiscoveredMarkets())
                    .isTrue();
                assertThat(visibilityRules.colonyVisibility().ungovernedColonySurveyLevel())
                    .isEqualTo(DecivilisedMarkets.DEFAULT_SURVEY_LEVEL);
                assertThat(visibilityRules.isForcedOntoMap())
                    .isFalse();
            }
        }

        @Test
        void carriesTheSurveyLevelForTheDecivilisedWorldsKnobAlone() {

            // The two knobs answer different arms of the fog, so each has to reach its own and
            // neither the other's: a world flown past but never surveyed is discovered and unread
            // at once, which is exactly the world a folded pair would leave unreachable.
            try (var settingsMock = mockStatic(KmuMapVisibilitySettings.class)) {

                settingsMock
                    .when(KmuMapVisibilitySettings::getDecivilisedWorldSurveyLevel)
                    .thenReturn(SurveyLevel.FULL);
                settingsMock
                    .when(KmuMapVisibilitySettings::shouldShowUndiscoveredMarkets)
                    .thenReturn(false);

                var visibilityRules = MapVisibilityRules.readFromLunaSettings();

                assertThat(visibilityRules.colonyVisibility().ungovernedColonySurveyLevel())
                    .isEqualTo(SurveyLevel.FULL);
                assertThat(visibilityRules.colonyVisibility().shouldIncludeUndiscoveredMarkets())
                    .isFalse();
            }
        }

        @Test
        void forcesSystemsOntoTheMapForTheHiddenSystemsToggleAlone() {

            try (var settingsMock = mockStatic(KmuMapVisibilitySettings.class)) {

                settingsMock
                    .when(KmuMapVisibilitySettings::shouldShowUndiscoveredMarkets)
                    .thenReturn(false);
                settingsMock
                    .when(KmuMapVisibilitySettings::shouldShowHiddenSystems)
                    .thenReturn(true);

                var visibilityRules = MapVisibilityRules.readFromLunaSettings();

                assertThat(visibilityRules.isForcedOntoMap())
                    .isTrue();
                assertThat(visibilityRules.colonyVisibility().shouldIncludeUndiscoveredMarkets())
                    .isFalse();
            }
        }

        @Test
        void holdsEveryRevelationGateWhereNeitherSpoilerToggleIsOn() {

            // The shipped state, both spoiler toggles being off. Asserted against the enum's own
            // values rather than against a list written out here: a gate added later must reach
            // the live rule without this case being remembered, since a shape nobody thought to
            // name is exactly the one that leaks.
            try (var settingsMock = mockStatic(KmuMapVisibilitySettings.class)) {

                var visibilityRules = MapVisibilityRules.readFromLunaSettings();

                assertThat(visibilityRules.colonyVisibility().revelationGates())
                    .containsExactlyInAnyOrder(RevelationGate.values());
            }
        }

        @Test
        void dropsTheDerelictGateForTheUnseenDerelictsToggleAlone() {

            try (var settingsMock = mockStatic(KmuMapVisibilitySettings.class)) {

                settingsMock
                    .when(KmuMapVisibilitySettings::shouldShowUnseenAbandonedStations)
                    .thenReturn(true);
                settingsMock
                    .when(KmuMapVisibilitySettings::shouldShowUnseenHiddenMarkets)
                    .thenReturn(false);

                var visibilityRules = MapVisibilityRules.readFromLunaSettings();

                // The concealment gate is what survives, which is what a transposed mapping
                // fails on: reading the two toggles the wrong way round leaves the derelict
                // gate standing instead, and every other assertion here would still pass.
                assertThat(visibilityRules.colonyVisibility().revelationGates())
                    .containsExactly(RevelationGate.HIDDEN_COLONIES);
            }
        }

        @Test
        void dropsTheConcealmentGateForTheUnseenHiddenColoniesToggleAlone() {

            try (var settingsMock = mockStatic(KmuMapVisibilitySettings.class)) {

                settingsMock
                    .when(KmuMapVisibilitySettings::shouldShowUnseenAbandonedStations)
                    .thenReturn(false);
                settingsMock
                    .when(KmuMapVisibilitySettings::shouldShowUnseenHiddenMarkets)
                    .thenReturn(true);

                var visibilityRules = MapVisibilityRules.readFromLunaSettings();

                assertThat(visibilityRules.colonyVisibility().revelationGates())
                    .containsExactly(RevelationGate.SPACE_DERELICTS);
            }
        }

        @Test
        void holdsNoRevelationGateWhereBothSpoilerTogglesAreOn() {

            // A player asking to see both shapes drops each back to the fog alone - which is
            // still the fog, so this widens what is shown without ever showing what has not
            // been found.
            try (var settingsMock = mockStatic(KmuMapVisibilitySettings.class)) {

                settingsMock
                    .when(KmuMapVisibilitySettings::shouldShowUnseenAbandonedStations)
                    .thenReturn(true);
                settingsMock
                    .when(KmuMapVisibilitySettings::shouldShowUnseenHiddenMarkets)
                    .thenReturn(true);

                var visibilityRules = MapVisibilityRules.readFromLunaSettings();

                assertThat(visibilityRules.colonyVisibility().revelationGates())
                    .isEmpty();
            }
        }
    }
}
