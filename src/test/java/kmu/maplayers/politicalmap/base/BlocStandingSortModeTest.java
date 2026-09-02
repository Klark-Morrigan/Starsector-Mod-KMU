package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.campaign.RepLevel;

import kmlib.starsector.relation.PlayerStanding;
import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.lists.ListSortMode;
import kmlib.starsector.ui.widgets.lists.SortDirection;

import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.Map;

import static kmu.maplayers.politicalmap.base.BlocSortFixtures.ROW_COLOUR;
import static kmu.maplayers.politicalmap.base.BlocSortFixtures.buildStandInBloc;
import static kmu.maplayers.politicalmap.base.BlocSortFixtures.listIdsInModeOrder;
import static kmu.maplayers.politicalmap.base.BlocSortFixtures.listIdsInOrder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the ranking and the drawn value the standing mode adds: the three cases laid out as one scale
 * with the range's two ends behind them, both ends of that scale following the player's direction
 * control, and each of the four things a row can draw for a standing.
 *
 * <p>Run over the payload no view declares, since the mode reads no metrics at all - which is itself
 * what a case over that payload shows.
 *
 * <p>No case reads the drawn label or the range separator: both go through the live settings, which
 * the test JVM has none of, so an assertion on either would pin the strings fallback rather than the
 * text. The separator run is still read for what the mode decides about it - the tone it takes and
 * where it sits.
 */
final class BlocStandingSortModeTest {

    // The shades relations resolve to, distinct so a run carrying the wrong end's standing shows as a
    // colour rather than only as a number.
    private static final Color GREEN = new Color(60, 180, 60);
    private static final Color RED = new Color(200, 50, 50);
    private static final Color GREY = new Color(140, 140, 140);

    private static final PlayerStanding FRIENDLY = new PlayerStanding(RepLevel.FRIENDLY, 60, GREEN);
    private static final PlayerStanding HOSTILE = new PlayerStanding(RepLevel.HOSTILE, -40, RED);
    private static final PlayerStanding NEUTRAL = new PlayerStanding(RepLevel.NEUTRAL, 0, GREY);

    // A second friendly standing one point off the first, so a pair level on the low end of the range
    // has a high end left to break it.
    private static final PlayerStanding WARMER = new PlayerStanding(RepLevel.FRIENDLY, 61, GREEN);

    // An alliance of two, which is the smallest grouping in which a bloc's standing is a range over
    // more than one member.
    private static final HolderGrouping PACT_GROUPING = new HolderGrouping(
        Map.of("hegemony", "pact", "tritachyon", "pact"),
        Map.of("pact", "hegemony"),
        Map.of("pact", "Persean Pact"));

    @Nested
    class PersistenceKey {

        @Test
        void persistenceKeyIsTheOneKeyEveryViewStoresTheChoiceUnder() {

            // One key rather than one per vocabulary, which is what lets the choice survive a switch
            // between the views.
            assertThat(buildMode(HolderGrouping.identity(), Map.of()).persistenceKey())
                .isEqualTo("player_standing");
        }
    }

    @Nested
    class DefaultDirection {

        @Test
        void defaultDirectionIsDescendingSoTheFriendliestBlocLeads() {

            assertThat(buildMode(HolderGrouping.identity(), Map.of()).defaultDirection())
                .isEqualTo(SortDirection.DESCENDING);
        }
    }

    @Nested
    class Comparator {

        @Test
        void comparatorRanksMeasuredBlocsByTheLowEndOfTheirRange() {

            var mode = buildMode(
                HolderGrouping.identity(),
                Map.of("hegemony", HOSTILE, "tritachyon", FRIENDLY));

            assertThat(listIdsInModeOrder(
                    mode,
                    buildStandInBloc("hegemony", "Hegemony", 0, 0),
                    buildStandInBloc("tritachyon", "Tri-Tachyon", 0, 0)))
                .containsExactly("tritachyon", "hegemony");
        }

        @Test
        void comparatorBreaksATieOnTheLowEndByTheHighEnd() {

            // Both blocs hold a hostile low end; only the alliance's friendlier member separates them,
            // which is the high end doing the work.
            var mode = buildMode(
                PACT_GROUPING,
                Map.of("hegemony", HOSTILE, "tritachyon", FRIENDLY, "luddic_church", HOSTILE));

            assertThat(listIdsInModeOrder(
                    mode,
                    buildStandInBloc("luddic_church", "Luddic Church", 0, 0),
                    buildStandInBloc("pact", "Persean Pact", 0, 0)))
                .containsExactly("pact", "luddic_church");
        }

        @Test
        void comparatorPutsThePlayersOwnBlocAboveEveryMeasuredBloc() {

            // Above the friendliest faction there is, so the placing is the case rather than a number
            // the player's own bloc happens to hold.
            var mode = buildMode(
                HolderGrouping.identity(),
                "player",
                Map.of("player", HOSTILE, "tritachyon", FRIENDLY));

            assertThat(listIdsInModeOrder(
                    mode,
                    buildStandInBloc("tritachyon", "Tri-Tachyon", 0, 0),
                    buildStandInBloc("player", "Player", 0, 0)))
                .containsExactly("player", "tritachyon");
        }

        @Test
        void comparatorPutsAnUnreadableBlocBelowEveryMeasuredBloc() {

            // Below the most hostile faction there is, rather than ranking with the neutrals as a
            // reputation of nought would have it.
            var mode = buildMode(HolderGrouping.identity(), Map.of("hegemony", HOSTILE));

            assertThat(listIdsInModeOrder(
                    mode,
                    buildStandInBloc("nobody", "Nobody", 0, 0),
                    buildStandInBloc("hegemony", "Hegemony", 0, 0)))
                .containsExactly("hegemony", "nobody");
        }

        @Test
        void comparatorSendsBothEndsOfTheScaleTheOtherWayUnderTheFlip() {

            // The case ordering is part of the primary key, so the player's own bloc trails and the
            // unreadable one leads - neither is the row the direction control does not govern.
            var mode = buildMode(
                HolderGrouping.identity(),
                "player",
                Map.of("player", NEUTRAL, "hegemony", HOSTILE, "tritachyon", FRIENDLY));

            assertThat(listIdsInOrder(
                    mode.comparator(SortDirection.ASCENDING),
                    buildStandInBloc("player", "Player", 0, 0),
                    buildStandInBloc("tritachyon", "Tri-Tachyon", 0, 0),
                    buildStandInBloc("hegemony", "Hegemony", 0, 0),
                    buildStandInBloc("nobody", "Nobody", 0, 0)))
                .containsExactly("nobody", "hegemony", "tritachyon", "player");
        }

        @Test
        void comparatorBreaksAPairOfBlankStandingsBySharedTail() {

            // Neither bloc holds a number at all, so the ranking separates nothing before the tail and
            // the labels decide. The payload numbers point the other way, which is what shows the mode
            // reads no vocabulary's chain on the way there.
            var mode = buildMode(HolderGrouping.identity(), Map.of());

            assertThat(listIdsInModeOrder(
                    mode,
                    buildStandInBloc("zeta", "Zeta", 9, 9),
                    buildStandInBloc("alpha", "Alpha", 1, 1)))
                .containsExactly("alpha", "zeta");
        }

        @Test
        void comparatorBreaksAPairHoldingTheSameStandingBySharedTail() {

            // Identical standings, so the ranking falls straight to the shared tail and the labels
            // decide - the mode has no numeric chain of its own to fall through first.
            var mode = buildMode(
                HolderGrouping.identity(),
                Map.of("hegemony", FRIENDLY, "tritachyon", FRIENDLY));

            assertThat(listIdsInModeOrder(
                    mode,
                    buildStandInBloc("tritachyon", "Zeta", 9, 9),
                    buildStandInBloc("hegemony", "Alpha", 1, 1)))
                .containsExactly("hegemony", "tritachyon");
        }
    }

    @Nested
    class ResolveTrailingRuns {

        @Test
        void resolveTrailingRunsDrawsOneSignedNumberWhenTheRangesEndsCoincide() {

            // A lone faction folds to a range whose ends are the same standing, so the value collapses
            // to that one number in its own relation's colour.
            var mode = buildMode(HolderGrouping.identity(), Map.of("tritachyon", FRIENDLY));

            assertThat(mode.resolveTrailingRuns(
                    buildStandInBloc("tritachyon", "Tri-Tachyon", 0, 0),
                    ROW_COLOUR))
                .containsExactly(new TextSpan("+60", GREEN));
        }

        @Test
        void resolveTrailingRunsDrawsANegativeStandingWithItsOwnMinus() {

            var mode = buildMode(HolderGrouping.identity(), Map.of("hegemony", HOSTILE));

            assertThat(mode.resolveTrailingRuns(
                    buildStandInBloc("hegemony", "Hegemony", 0, 0),
                    ROW_COLOUR))
                .containsExactly(new TextSpan("-40", RED));
        }

        @Test
        void resolveTrailingRunsDrawsANeutralStandingUnsigned() {

            // Nought leans neither way, so it is drawn plain rather than claiming a direction.
            var mode = buildMode(HolderGrouping.identity(), Map.of("hegemony", NEUTRAL));

            assertThat(mode.resolveTrailingRuns(
                    buildStandInBloc("hegemony", "Hegemony", 0, 0),
                    ROW_COLOUR))
                .containsExactly(new TextSpan("0", GREY));
        }

        @Test
        void resolveTrailingRunsDrawsBothEndsInTheirOwnColoursWhenMembersDisagree() {

            var mode = buildMode(
                PACT_GROUPING,
                Map.of("hegemony", HOSTILE, "tritachyon", FRIENDLY));

            var runs = mode.resolveTrailingRuns(
                buildStandInBloc("pact", "Persean Pact", 0, 0),
                ROW_COLOUR);

            // The two ends in the shades their own relations resolved to, so the range is painted the
            // way the game paints each half of it.
            assertThat(runs).hasSize(3);
            assertThat(runs.get(0))
                .isEqualTo(new TextSpan("-40", RED));
            assertThat(runs.get(2))
                .isEqualTo(new TextSpan("+60", GREEN).joinsPreviousRun());
        }

        @Test
        void resolveTrailingRunsJoinsTheRangeIntoOneRowColouredValue() {

            var mode = buildMode(
                PACT_GROUPING,
                Map.of("hegemony", HOSTILE, "tritachyon", FRIENDLY));

            var runs = mode.resolveTrailingRuns(
                buildStandInBloc("pact", "Persean Pact", 0, 0),
                ROW_COLOUR);

            // The separator takes the row's own tone, belonging to the value's punctuation rather than
            // to either relation, and every run behind the first butts against the one before it - so
            // the three read as one value rather than as three words.
            assertThat(runs.get(1).colour()).isEqualTo(ROW_COLOUR);
            assertThat(runs.get(0).isJoinedToPreviousRun()).isFalse();
            assertThat(runs.get(1).isJoinedToPreviousRun()).isTrue();
            assertThat(runs.get(2).isJoinedToPreviousRun()).isTrue();
        }

        @Test
        void resolveTrailingRunsDrawsTheHighEndItsMembersActuallyReached() {

            // The same alliance with a warmer second member draws that member's number, so the high
            // end is read off whoever holds it rather than restated from the low end.
            var mode = buildMode(
                PACT_GROUPING,
                Map.of("hegemony", HOSTILE, "tritachyon", WARMER));

            assertThat(mode.resolveTrailingRuns(
                    buildStandInBloc("pact", "Persean Pact", 0, 0),
                    ROW_COLOUR))
                .element(2)
                .isEqualTo(new TextSpan("+61", GREEN).joinsPreviousRun());
        }

        @Test
        void resolveTrailingRunsDrawsNothingForThePlayersOwnBloc() {

            // The bloc the scale is measured from draws no number: it would be the engine's answer to
            // a question nobody asked.
            var mode = buildMode(
                HolderGrouping.identity(),
                "player",
                Map.of("player", FRIENDLY));

            assertThat(mode.resolveTrailingRuns(
                    buildStandInBloc("player", "Player", 0, 0),
                    ROW_COLOUR))
                .isEmpty();
        }

        @Test
        void resolveTrailingRunsDrawsNothingForAnUnreadableBloc() {

            var mode = buildMode(HolderGrouping.identity(), Map.of());

            assertThat(mode.resolveTrailingRuns(
                    buildStandInBloc("nobody", "Nobody", 0, 0),
                    ROW_COLOUR))
                .isEmpty();
        }
    }

    // The mode over a sector in which no player faction is established, which is every case that is
    // not about the player's own bloc.
    private static ListSortMode<RankedBloc<HazardRating>> buildMode(
            HolderGrouping grouping,
            Map<String, PlayerStanding> standingByFactionId) {

        return buildMode(grouping, null, standingByFactionId);
    }

    // The mode over a stated grouping and a stated set of readable standings.
    private static ListSortMode<RankedBloc<HazardRating>> buildMode(
            HolderGrouping grouping,
            String establishedPlayerFactionId,
            Map<String, PlayerStanding> standingByFactionId) {

        return new BlocStandingSortMode<>(new BlocStandingReader(
            grouping,
            new PlayerStandingSourceFake(establishedPlayerFactionId, standingByFactionId)));
    }
}
