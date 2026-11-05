package kmu.maplayers.politicalmap.dominance.standings;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.ownermap.owners.holders.base.ColonyReadRulesFixtures;
import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;
import kmu.maplayers.politicalmap.dominance.DominancePass;
import kmu.maplayers.politicalmap.dominance.weighting.BaseSizeWeighting;
import kmu.maplayers.politicalmap.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.dominance.weighting.MarketFootprint;
import kmu.maplayers.politicalmap.dominance.weighting.PatrolWeighting;
import kmu.maplayers.politicalmap.dominance.weighting.StationWeighting;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static kmu.maplayers.politicalmap.dominance.weighting.MarketFootprintFixtures.buildWeightedFootprint;
import static kmu.maplayers.politicalmap.dominance.weighting.MarketFootprintFixtures.listOrderedFootprints;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Pins {@link SystemStandings}'s grouped ranking on hand-built inputs and a hand-built grouping,
 * free of any live economy: groups rank by summed score and members rank within, ties breaking by ID
 * at both tiers. The faction cases prove the identity grouping renders every faction its own
 * singleton group; the alliance cases prove members fold into one bloc whose summed score can outrank
 * a lone faction no single member would. Each input lists the higher-scoring or higher-ID entry first
 * so the ordering is shown to come from the rule, not the map's walk order.
 *
 * <p>The presence cases pin the second input: a faction the pass could weigh nothing for is still
 * ranked, at the nought its standing carries, so the box over a cell can name every faction the band
 * inside it counts. It is the wider of the two inputs, so every case states it as the footprints'
 * own owners plus whoever it poses as present without a weight - and the two nameless-owner cases
 * pin that a faction the grouping can put no bloc to is dropped whichever input it arrived by.
 */
class SystemStandingsTests {

    @Nested
    class RankByDominationScore {

        // The live entry ranks the footprints the box folded, so the ranking, the holder and the
        // account beneath it all explain one weighing. Over no sector, so the pass reads no colony
        // at all - and a ranking that weighed for itself would find nobody.
        @Test
        void ranksTheFootprintsItWasHandedRatherThanWeighingAgain() {

            // ARRANGE
            var pass = DominancePass.createOver(
                null,
                new DominanceRules(
                    false,
                    new BaseSizeWeighting(1.0, null, 1.0, 1.0),
                    new StationWeighting(false, 1.0, 0.5, 0.5),
                    new PatrolWeighting(false, 0.25, 0.5, 1.0, 0.5)),
                ColonyReadRulesFixtures.UNDER_THE_FOG,
                HolderGrouping.identity());
            var footprints = listOrderedFootprints(
                "hegemony",
                buildWeightedFootprint(2000, 2000, 2000));

            var expectedStandings = List.of(
                new GroupStanding(
                    "hegemony",
                    2000,
                    List.of(new WeighedFactionStanding("hegemony", 2000))));

            // ACT
            var actualStandings = SystemStandings.rankByDominationScore(
                mock(StarSystemAPI.class),
                pass,
                footprints);

            // ASSERT
            assertThat(actualStandings)
                .isEqualTo(expectedStandings);
        }

        @Test
        void returnsNoGroupsForAnUninhabitedSystem() {

            // ACT
            var actualStandings = SystemStandings.rankByDominationScore(
                Map.of(),
                Set.of(),
                HolderGrouping.identity());

            // ASSERT
            assertThat(actualStandings)
                .isEmpty();
        }

        @Test
        void returnsOneSingletonGroupForASingleHolderSystem() {

            // ARRANGE
            var footprints = listOrderedFootprints(
                "hegemony",
                buildWeightedFootprint(7, 5, 5));

            var expectedStandings = List.of(
                new GroupStanding(
                    "hegemony",
                    7,
                    List.of(new WeighedFactionStanding("hegemony", 7))));

            // ACT
            var actualStandings = SystemStandings.rankByDominationScore(
                footprints,
                listPresent(footprints),
                HolderGrouping.identity());

            // ASSERT
            assertThat(actualStandings)
                .isEqualTo(expectedStandings);
        }

        // Two owned markets: the identity grouping makes each faction its own singleton group, so the
        // two groups rank by score with the higher first though it is listed second.
        @Test
        void ranksAContestedSystemsFactionsDescendingByScore() {

            // ARRANGE
            var footprints = listOrderedFootprints(
                "tritachyon",
                buildWeightedFootprint(4, 4, 0),
                "hegemony",
                buildWeightedFootprint(9, 5, 5));

            // ACT
            var actualStandings = SystemStandings.rankByDominationScore(
                footprints,
                listPresent(footprints),
                HolderGrouping.identity());

            // ASSERT
            assertThat(actualStandings)
                .extracting(GroupStanding::blocId)
                .containsExactly("hegemony", "tritachyon");
            assertThat(actualStandings)
                .extracting(GroupStanding::aggregateScore)
                .containsExactly(9, 4);
        }

        // Equal scores fall to the lowest bloc ID, so the order is deterministic and independent of
        // the walk order the higher ID is listed in first.
        @Test
        void breaksAGroupScoreTieByLowestBlocId() {

            // ARRANGE
            var footprints = listOrderedFootprints(
                "tritachyon",
                buildWeightedFootprint(6, 6, 6),
                "hegemony",
                buildWeightedFootprint(6, 6, 6));

            // ACT
            var actualStandings = SystemStandings.rankByDominationScore(
                footprints,
                listPresent(footprints),
                HolderGrouping.identity());

            // ASSERT
            assertThat(actualStandings)
                .extracting(GroupStanding::blocId)
                .containsExactly("hegemony", "tritachyon");
        }

        // Both allied factions fold into one bloc: its aggregate is their sum, and its two members
        // are ranked descending by their own score though the weaker is listed first.
        @Test
        void foldsAllianceMembersIntoOneGroupSummedAndRankedWithin() {

            // ARRANGE
            var footprints = listOrderedFootprints(
                "astral_armada",
                buildWeightedFootprint(3, 3, 0),
                "hegemony",
                buildWeightedFootprint(8, 5, 5));

            var expectedStandings = List.of(
                new GroupStanding(
                    "alliance-1",
                    11,
                    List.of(
                        new WeighedFactionStanding("hegemony", 8),
                        new WeighedFactionStanding("astral_armada", 3))));

            // ACT
            var actualStandings = SystemStandings.rankByDominationScore(
                footprints,
                listPresent(footprints),
                buildAllianceGrouping());

            // ASSERT
            assertThat(actualStandings)
                .isEqualTo(expectedStandings);
        }

        // Both allied members score the same within their bloc, so the lower faction ID ranks first -
        // the ID tie-break applies at the member tier as well as the group tier.
        @Test
        void breaksAMemberScoreTieByLowestFactionId() {

            // ARRANGE
            var footprints = listOrderedFootprints(
                "hegemony",
                buildWeightedFootprint(5, 5, 0),
                "astral_armada",
                buildWeightedFootprint(5, 5, 0));

            // ACT
            var actualStandings = SystemStandings.rankByDominationScore(
                footprints,
                listPresent(footprints),
                buildAllianceGrouping());

            // ASSERT
            assertThat(actualStandings)
                .hasSize(1);
            assertThat(actualStandings.get(0).members())
                .extracting(FactionStanding::factionId)
                .containsExactly("astral_armada", "hegemony");
        }

        // A colony that folds in at zero weight still marks presence (an unopposed weightless colony
        // still owns its system), so a zero-score faction stays a group rather than vanishing from the
        // breakdown - and its nought is one the weighing arrived at, so the standing is the weighed
        // kind.
        @Test
        void keepsAPresentButWeightlessColonyInTheRanking() {

            // ARRANGE
            var footprints = listOrderedFootprints(
                "hegemony",
                buildWeightedFootprint(0, 0, 0));

            var expectedStandings = List.of(
                new GroupStanding(
                    "hegemony",
                    0,
                    List.of(new WeighedFactionStanding("hegemony", 0))));

            // ACT
            var actualStandings = SystemStandings.rankByDominationScore(
                footprints,
                listPresent(footprints),
                HolderGrouping.identity());

            // ASSERT
            assertThat(actualStandings)
                .isEqualTo(expectedStandings);
        }

        // Neither allied member outscores the outsider alone, but their summed bloc does, so the bloc
        // ranks first - the two-tier sum, not any single member, decides the top tier.
        @Test
        void ranksABlocAboveALoneFactionNoSingleMemberWouldOutrank() {

            // ARRANGE
            var footprints = listOrderedFootprints(
                "tritachyon",
                buildWeightedFootprint(7, 7, 0),
                "hegemony",
                buildWeightedFootprint(5, 3, 3),
                "astral_armada",
                buildWeightedFootprint(4, 4, 0));

            // ACT
            var actualStandings = SystemStandings.rankByDominationScore(
                footprints,
                listPresent(footprints),
                buildAllianceGrouping());

            // ASSERT
            assertThat(actualStandings)
                .extracting(GroupStanding::blocId)
                .containsExactly("alliance-1", "tritachyon");
            assertThat(actualStandings)
                .extracting(GroupStanding::aggregateScore)
                .containsExactly(9, 7);
            assertThat(actualStandings.get(0).members())
                .extracting(FactionStanding::factionId)
                .containsExactly("hegemony", "astral_armada");
        }

        // The Anathema shape: the only colony a faction holds here is one the economy does not list,
        // so no footprint was ever raised for it. Listed all the same, at the nought its standing
        // carries, since the map is plainly drawing that station in its colours.
        @Test
        void ranksAFactionPresentThroughUnweighedColoniesAloneAtANought() {

            // ARRANGE
            var expectedStandings = List.of(
                new GroupStanding(
                    "tritachyon",
                    0,
                    List.of(new PresenceOnlyFactionStanding("tritachyon"))));

            // ACT
            var actualStandings = SystemStandings.rankByDominationScore(
                Map.of(),
                Set.of("tritachyon"),
                HolderGrouping.identity());

            // ASSERT
            assertThat(actualStandings)
                .isEqualTo(expectedStandings);
        }

        // Presence takes no weight, so it settles at the foot on the ordinary score rule - below the
        // weighed faction here though its own ID sorts first.
        @Test
        void ranksANoughtBelowEveryWeighedFactionAndBehindThemById() {

            // ARRANGE
            var footprints = listOrderedFootprints(
                "hegemony",
                buildWeightedFootprint(4, 4, 0));

            // ACT
            var actualStandings = SystemStandings.rankByDominationScore(
                footprints,
                listPresent(footprints, "astral_armada"),
                HolderGrouping.identity());

            // ASSERT
            assertThat(actualStandings)
                .extracting(GroupStanding::blocId)
                .containsExactly("hegemony", "astral_armada");
        }

        // Presence is the wider set, so a weighed faction is in both inputs. It keeps the standing
        // its arithmetic earned and is listed once rather than twice - a second, presence-only line
        // for the same faction would read as a second holder.
        @Test
        void listsAFactionOnceWhereThePassAlsoWeighedIt() {

            // ARRANGE
            var footprints = listOrderedFootprints(
                "hegemony",
                buildWeightedFootprint(6, 6, 0));

            var expectedStandings = List.of(
                new GroupStanding(
                    "hegemony",
                    6,
                    List.of(new WeighedFactionStanding("hegemony", 6))));

            // ACT
            var actualStandings = SystemStandings.rankByDominationScore(
                footprints,
                listPresent(footprints),
                HolderGrouping.identity());

            // ASSERT
            assertThat(actualStandings)
                .isEqualTo(expectedStandings);
        }

        // Neither ally holds anything the pass could weigh, so the bloc's aggregate is a nought nobody
        // worked out. It is still the bloc the alliances view paints, listed with both members under
        // it rather than dropped for having no weight.
        @Test
        void carriesABlocWhoseMembersAllComeToNoughtAsABloc() {

            // ARRANGE
            var expectedStandings = List.of(
                new GroupStanding(
                    "alliance-1",
                    0,
                    List.of(
                        new PresenceOnlyFactionStanding("astral_armada"),
                        new PresenceOnlyFactionStanding("hegemony"))));

            // ACT
            var actualStandings = SystemStandings.rankByDominationScore(
                Map.of(),
                Set.of("hegemony", "astral_armada"),
                buildAllianceGrouping());

            // ASSERT
            assertThat(actualStandings)
                .isEqualTo(expectedStandings);
            assertThat(actualStandings.get(0).hasWeighedMember())
                .isFalse();
        }

        // The aggregate is a sum somebody worked out as soon as one member was weighed, however many
        // of its allies are merely present - which is what the box's quiet shade turns on.
        @Test
        void marksABlocWeighedWhereOneMemberHoldsAWeighedColony() {

            // ARRANGE
            var footprints = listOrderedFootprints(
                "hegemony",
                buildWeightedFootprint(5, 5, 0));

            // ACT
            var actualStandings = SystemStandings.rankByDominationScore(
                footprints,
                listPresent(footprints, "astral_armada"),
                buildAllianceGrouping());

            // ASSERT
            assertThat(actualStandings)
                .hasSize(1);
            assertThat(actualStandings.get(0).hasWeighedMember())
                .isTrue();
        }

        // A colony a mod hung on a faction carrying no ID. A nameless key would travel on as a bloc
        // the box cannot name, and the group tie-break would fault on it the moment a second group
        // stood beside it - which is the ordinary case.
        @Test
        void leavesOutAWeighedFactionTheGroupingCanNameNoBlocFor() {

            // ARRANGE
            var footprints = listOrderedFootprints(
                "hegemony",
                buildWeightedFootprint(4, 4, 0),
                null,
                buildWeightedFootprint(9, 9, 0));

            // ACT
            var actualStandings = SystemStandings.rankByDominationScore(
                footprints,
                listPresent(footprints),
                HolderGrouping.identity());

            // ASSERT
            assertThat(actualStandings)
                .extracting(GroupStanding::blocId)
                .containsExactly("hegemony");
        }

        // The same nameless owner reaching the ranking through presence rather than through a weight,
        // which an unregistered colony is all it takes.
        @Test
        void leavesOutAPresentFactionTheGroupingCanNameNoBlocFor() {

            // ARRANGE
            var footprints = listOrderedFootprints(
                "hegemony",
                buildWeightedFootprint(4, 4, 0));

            // ACT
            var actualStandings = SystemStandings.rankByDominationScore(
                footprints,
                listPresent(footprints, (String) null),
                HolderGrouping.identity());

            // ASSERT
            assertThat(actualStandings)
                .extracting(GroupStanding::blocId)
                .containsExactly("hegemony");
        }
    }

    // Everyone in the system: the owners the footprints name, plus any the case poses as present
    // without a weight. Derived from the footprints rather than restated beside them because that
    // is the relation the rule is handed - presence is the wider set, and a case stating the two
    // apart could pose a system where a weighed faction is not in it.
    private static Set<String> listPresent(
            Map<String, MarketFootprint> footprints,
            String... unweighedFactionIds) {

        var presentFactionIds = new LinkedHashSet<>(footprints.keySet());

        presentFactionIds.addAll(Arrays.asList(unweighedFactionIds));

        return presentFactionIds;
    }

    // Two allied factions folded into one bloc, with tritachyon left an outsider mapped to itself, so
    // a test can pit the bloc's summed score against the lone faction off one grouping.
    private static HolderGrouping buildAllianceGrouping() {
        return new HolderGrouping(
            Map.of("hegemony", "alliance-1", "astral_armada", "alliance-1"),
            Map.of("alliance-1", "hegemony"),
            Map.of("alliance-1", "Allied Powers"));
    }

}
