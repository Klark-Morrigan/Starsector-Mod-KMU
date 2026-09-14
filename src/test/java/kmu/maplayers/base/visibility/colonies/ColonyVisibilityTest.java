package kmu.maplayers.base.visibility.colonies;

import com.fs.starfarer.api.campaign.econ.MarketAPI.SurveyLevel;

import kmlib.starsector.markets.DecivilisedMarkets;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins {@link ColonyVisibility#BASE_FOG}, the rule a caller stating none of its own is read as,
 * and the fixed-rule guarantee its construction makes. Each member's cases live in a
 * {@link Nested} group so the suite reports as a per-member tree.
 *
 * <p>Worth pinning although the record itself holds no logic: everything it carries is
 * load-bearing in a direction nothing else would catch. A reveal set here by mistake would put
 * every undiscovered colony in the sector on the map, a survey bar dropped here would name every
 * collapsed colony in it, and a gate set here would hold back a derelict nobody asked to hide -
 * all silently, and all under the name the whole library falls back to.
 */
final class ColonyVisibilityTest {

    @Nested
    class BaseFog {

        @Test
        void admitsNothingThePlayerHasNotFound() {

            assertThat(ColonyVisibility.BASE_FOG.shouldIncludeUndiscoveredMarkets())
                .isFalse();
        }

        @Test
        void asksTheSurveyVanillaAsksBeforeNamingACollapsedColony() {
            // The fall-back rule mirrors the game rather than picking a bar of its own, so a map
            // reading no settings says exactly what the game says and no more.
            assertThat(ColonyVisibility.BASE_FOG.ungovernedColonySurveyLevel())
                .isEqualTo(DecivilisedMarkets.DEFAULT_SURVEY_LEVEL);
        }

        @Test
        void holdsBackNothingBeyondTheFog() {
            // A gate nobody asked for must not appear out of an unstated argument, so the
            // fall-back rule adds nothing to the fog in any direction.
            assertThat(ColonyVisibility.BASE_FOG.revelationGates())
                .isEmpty();
        }
    }

    @Nested
    class Construct {

        @Test
        void readsAbsentGatesAsNoGatesAtAll() {
            assertThat(new ColonyVisibility(false, SurveyLevel.SEEN, null).revelationGates())
                .isEmpty();
        }

        @Test
        void readsAnAbsentSurveyLevelAsTheFogOwnBar() {
            // The direction that matters: a missing argument must not be read as no bar at all, or
            // it would name every collapsed colony in the sector on a map that asked for nothing.
            var rule = new ColonyVisibility(false, null, Set.of());

            assertThat(rule.ungovernedColonySurveyLevel())
                .isEqualTo(DecivilisedMarkets.DEFAULT_SURVEY_LEVEL);
        }

        @Test
        void keepsEachPartAnsweringForTheOneThingItNames() {
            // The whole of what parts one knob from the next: a survey bar dropped to nothing says
            // nothing about whether an undiscovered colony counts, or about any gate.
            var rule = new ColonyVisibility(false, SurveyLevel.NONE, Set.of());

            assertThat(rule.ungovernedColonySurveyLevel())
                .isEqualTo(SurveyLevel.NONE);
            assertThat(rule.shouldIncludeUndiscoveredMarkets())
                .isFalse();
            assertThat(rule.revelationGates())
                .isEmpty();
        }

        @Test
        void keepsTheGatesItWasBuiltWithWhenTheSourceSetChangesLater() {

            var gates = new HashSet<RevelationGate>();
            gates.add(RevelationGate.SPACE_DERELICTS);

            var rule = new ColonyVisibility(false, SurveyLevel.SEEN, gates);

            gates.clear();

            assertThat(rule.revelationGates())
                .containsExactly(RevelationGate.SPACE_DERELICTS);
        }

        @Test
        void rejectsAnAttemptToChangeTheGates() {
            // A rule is resolved once for a whole render pass and handed to every reader in it,
            // so one reader able to change it would be re-fogging the map underneath the others.
            var rule = new ColonyVisibility(
                false,
                SurveyLevel.SEEN,
                Set.of(RevelationGate.HIDDEN_COLONIES));

            assertThatThrownBy(() -> rule.revelationGates().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
