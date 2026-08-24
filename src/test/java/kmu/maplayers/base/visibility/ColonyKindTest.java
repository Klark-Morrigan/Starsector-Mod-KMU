package kmu.maplayers.base.visibility;

import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmlib.testfixtures.starsector.colonies.ColonyMarketFixture;

import kmu.maplayers.DecivilisedPlanetFixtures;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the contract of {@link ColonyKind#resolveKind} - which kind of place a market stands for.
 * The cases live in a {@link Nested} group so the suite reports as a per-method tree, and the
 * markets they are posed against are {@link ColonyMarketFixture}'s.
 *
 * <p>What makes a market wear the derelict shape is the market read's own contract and is pinned
 * with it; what this adds is that the classification routes to the right kind - including which of
 * the two derelict-shaped kinds an owner or a listing makes it - and that anything unrecognised
 * falls to the ordinary one rather than being erased.
 */
final class ColonyKindTest {

    // What the economy's own listing said of the market being classified, which the walk that
    // selected it decides rather than the market. Named so a case reads as the listing it poses
    // instead of as a bare boolean at the end of a call.
    private static final boolean LISTED_BY_ECONOMY = true;
    private static final boolean UNLISTED_BY_ECONOMY = false;

    @Nested
    class ResolveKind {

        @Test
        void reads_a_derelict_station_held_by_nobody_as_a_space_derelict() {

            var derelict = ColonyMarketFixture.buildDerelictStation();

            assertThat(ColonyKind.resolveKind(derelict, UNLISTED_BY_ECONOMY))
                .isEqualTo(ColonyKind.SPACE_DERELICT);
        }

        @Test
        void reads_a_derelict_conditioned_market_a_faction_holds_as_an_outpost() {
            // The condition is not vanilla's alone - a mod may hang it on a station it means to be
            // manned - so the owner is one of the two things that part a kept station from a hulk.
            // Posed against the case above, which differs in the owner and in nothing else.
            var outpost = ColonyMarketFixture.buildOutpost("hegemony");

            assertThat(ColonyKind.resolveKind(outpost, UNLISTED_BY_ECONOMY))
                .isEqualTo(ColonyKind.OUTPOST);
        }

        @Test
        void reads_an_unowned_derelict_the_economy_lists_as_an_outpost() {
            // The other of the two, and the case that says either alone is enough. The routine
            // that builds a derelict pointedly does not register one, so a hulk trading anyway was
            // made economically real on purpose - and reading the owner alone left it taking a
            // dominance weight while counting toward nobody living there.
            var derelict = ColonyMarketFixture.buildDerelictStation();

            assertThat(ColonyKind.resolveKind(derelict, LISTED_BY_ECONOMY))
                .isEqualTo(ColonyKind.OUTPOST);
        }

        @Test
        void reads_a_derelict_conditioned_market_the_neutral_faction_holds_as_a_space_derelict() {
            // Neutral is an owner on paper and nobody in fact, which is the whole reason the kind
            // read asks the faction rather than merely asking whether one is set.
            var derelict = ColonyMarketFixture.buildOutpost(Factions.NEUTRAL);

            assertThat(ColonyKind.resolveKind(derelict, UNLISTED_BY_ECONOMY))
                .isEqualTo(ColonyKind.SPACE_DERELICT);
        }

        @Test
        void reads_an_ordinary_colony_as_a_colony() {

            var colony = ColonyMarketFixture.buildVisibleColony("hegemony");

            assertThat(ColonyKind.resolveKind(colony, LISTED_BY_ECONOMY))
                .isEqualTo(ColonyKind.COLONY);
        }

        @Test
        void reads_a_decivilised_world_as_a_dead_colony() {

            var decivilisedWorld = DecivilisedPlanetFixtures.buildRevealedDecivilisedMarket();

            assertThat(ColonyKind.resolveKind(decivilisedWorld, UNLISTED_BY_ECONOMY))
                .isEqualTo(ColonyKind.UNGOVERNED_COLONY);
        }

        @Test
        void reads_a_dead_world_the_player_has_not_surveyed_as_a_dead_colony() {
            // What the world is and whether the player may be told are different questions asked at
            // different layers, so an unread ruin is classified as the ruin it is and withheld by
            // the fog above rather than by being misfiled here.
            var decivilisedWorld = DecivilisedPlanetFixtures.buildUnsurveyedDecivilisedMarket();

            assertThat(ColonyKind.resolveKind(decivilisedWorld, UNLISTED_BY_ECONOMY))
                .isEqualTo(ColonyKind.UNGOVERNED_COLONY);
        }

        @Test
        void reads_a_bare_planets_placeholder_as_a_colony() {
            // The condition-only market every uninhabited world carries, and the case that says the
            // ruin arm turns on the decivilised condition rather than on being condition-only. Such
            // a market never reaches a colony set in the first place, ownership refusing it.
            var placeholder = ColonyMarketFixture.buildConditionOnlyMarket();

            assertThat(ColonyKind.resolveKind(placeholder, UNLISTED_BY_ECONOMY))
                .isEqualTo(ColonyKind.COLONY);
        }

        @Test
        void reads_a_null_market_as_a_colony() {
            // The default arm, posed at its extreme: nothing at all to read still yields the kind
            // that keeps a place on the map, because misfiling a settlement as a hulk erases it.
            assertThat(ColonyKind.resolveKind(null, UNLISTED_BY_ECONOMY))
                .isEqualTo(ColonyKind.COLONY);
        }
    }

    @Nested
    class IsSettlingLocation {

        @Test
        void answers_true_for_the_kinds_somebody_is_at() {

            assertThat(ColonyKind.COLONY.isSettlingLocation())
                .isTrue();
            assertThat(ColonyKind.OUTPOST.isSettlingLocation())
                .isTrue();
        }

        @Test
        void answers_false_for_a_dead_colony() {
            // Somewhere people were is not somewhere people are: a ruin inhabits its place and has
            // nobody left to say what else is standing in it.
            assertThat(ColonyKind.UNGOVERNED_COLONY.isSettlingLocation())
                .isFalse();
        }

        @Test
        void answers_false_for_a_space_derelict() {

            assertThat(ColonyKind.SPACE_DERELICT.isSettlingLocation())
                .isFalse();
        }
    }
}
