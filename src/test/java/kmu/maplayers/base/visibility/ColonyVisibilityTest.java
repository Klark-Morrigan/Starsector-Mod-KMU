package kmu.maplayers.base.visibility;

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
        void admits_nothing_the_player_has_not_found() {

            assertThat(ColonyVisibility.BASE_FOG.shouldIncludeUndiscoveredMarkets())
                .isFalse();
        }

        @Test
        void asks_the_survey_vanilla_asks_before_naming_a_collapsed_colony() {
            // The fall-back rule mirrors the game rather than picking a bar of its own, so a map
            // reading no settings says exactly what the game says and no more.
            assertThat(ColonyVisibility.BASE_FOG.ungovernedColonySurveyLevel())
                .isEqualTo(DecivilisedMarkets.DEFAULT_SURVEY_LEVEL);
        }

        @Test
        void holds_back_nothing_beyond_the_fog() {
            // A gate nobody asked for must not appear out of an unstated argument, so the
            // fall-back rule adds nothing to the fog in any direction.
            assertThat(ColonyVisibility.BASE_FOG.revelationGates())
                .isEmpty();
        }
    }

    @Nested
    class Construct {

        @Test
        void reads_absent_gates_as_no_gates_at_all() {
            assertThat(new ColonyVisibility(false, SurveyLevel.SEEN, null).revelationGates())
                .isEmpty();
        }

        @Test
        void reads_an_absent_survey_level_as_the_fog_own_bar() {
            // The direction that matters: a missing argument must not be read as no bar at all, or
            // it would name every collapsed colony in the sector on a map that asked for nothing.
            var rule = new ColonyVisibility(false, null, Set.of());

            assertThat(rule.ungovernedColonySurveyLevel())
                .isEqualTo(DecivilisedMarkets.DEFAULT_SURVEY_LEVEL);
        }

        @Test
        void keeps_each_part_answering_for_the_one_thing_it_names() {
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
        void keeps_the_gates_it_was_built_with_when_the_source_set_changes_later() {

            var gates = new HashSet<RevelationGate>();
            gates.add(RevelationGate.SPACE_DERELICTS);

            var rule = new ColonyVisibility(false, SurveyLevel.SEEN, gates);

            gates.clear();

            assertThat(rule.revelationGates())
                .containsExactly(RevelationGate.SPACE_DERELICTS);
        }

        @Test
        void rejects_an_attempt_to_change_the_gates() {
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
