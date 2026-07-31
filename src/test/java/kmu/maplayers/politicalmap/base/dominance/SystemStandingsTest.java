package kmu.maplayers.politicalmap.base.dominance;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link SystemStandings}'s grouped ranking on hand-built footprints and a hand-built grouping,
 * free of any live economy: groups rank by summed score and members rank within, ties breaking by id
 * at both tiers. The faction cases prove the identity grouping renders every faction its own
 * singleton group; the alliance cases prove members fold into one bloc whose summed score can outrank
 * a lone faction no single member would. Each input lists the higher-scoring or higher-id entry first
 * so the ordering is shown to come from the rule, not the map's walk order.
 */
class SystemStandingsTest {

    @Nested
    class RankByDominationScore {

        @Test
        void returnsNoGroupsForAnUninhabitedSystem() {
            assertThat(SystemStandings.rankByDominationScore(
                    Map.of(), HolderGrouping.identity()))
                    .isEmpty();
        }

        @Test
        void returnsOneSingletonGroupForASingleHolderSystem() {
            var footprints = orderedFootprints(
                    "hegemony", new MarketFootprint(7, 5, 5));

            var standings = SystemStandings.rankByDominationScore(
                    footprints, HolderGrouping.identity());

            assertThat(standings).containsExactly(
                    new GroupStanding("hegemony", 7,
                            List.of(new FactionStanding("hegemony", 7))));
        }

        @Test
        void ranksAContestedSystemsFactionsDescendingByScore() {
            // Two owned markets: the identity grouping makes each faction its own singleton group, so
            // the two groups rank by score with the higher first though it is listed second.
            var footprints = orderedFootprints(
                    "tritachyon", new MarketFootprint(4, 4, 0),
                    "hegemony", new MarketFootprint(9, 5, 5));

            var standings = SystemStandings.rankByDominationScore(
                    footprints, HolderGrouping.identity());

            assertThat(standings).extracting(GroupStanding::blocId)
                    .containsExactly("hegemony", "tritachyon");
            assertThat(standings).extracting(GroupStanding::aggregateScore)
                    .containsExactly(9, 4);
        }

        @Test
        void breaksAGroupScoreTieByLowestBlocId() {
            // Equal scores fall to the lowest bloc id, so the order is deterministic and independent
            // of the walk order the higher id is listed in first.
            var footprints = orderedFootprints(
                    "tritachyon", new MarketFootprint(6, 6, 6),
                    "hegemony", new MarketFootprint(6, 6, 6));

            var standings = SystemStandings.rankByDominationScore(
                    footprints, HolderGrouping.identity());

            assertThat(standings).extracting(GroupStanding::blocId)
                    .containsExactly("hegemony", "tritachyon");
        }

        @Test
        void foldsAllianceMembersIntoOneGroupSummedAndRankedWithin() {
            // Both allied factions fold into one bloc: its aggregate is their sum, and its two
            // members are ranked descending by their own score though the weaker is listed first.
            var footprints = orderedFootprints(
                    "astral_armada", new MarketFootprint(3, 3, 0),
                    "hegemony", new MarketFootprint(8, 5, 5));

            var standings = SystemStandings.rankByDominationScore(footprints, allianceGrouping());

            assertThat(standings).containsExactly(
                    new GroupStanding("alliance-1", 11, List.of(
                            new FactionStanding("hegemony", 8),
                            new FactionStanding("astral_armada", 3))));
        }

        @Test
        void breaksAMemberScoreTieByLowestFactionId() {
            // Both allied members score the same within their bloc, so the lower faction id ranks
            // first - the id tie-break applies at the member tier as well as the group tier.
            var footprints = orderedFootprints(
                    "hegemony", new MarketFootprint(5, 5, 0),
                    "astral_armada", new MarketFootprint(5, 5, 0));

            var standings = SystemStandings.rankByDominationScore(footprints, allianceGrouping());

            assertThat(standings).hasSize(1);
            assertThat(standings.get(0).members()).extracting(FactionStanding::factionId)
                    .containsExactly("astral_armada", "hegemony");
        }

        @Test
        void keepsAPresentButWeightlessColonyInTheRanking() {
            // A colony that folds in at zero weight still marks presence (an unopposed weightless
            // colony still owns its system), so a zero-score faction stays a group rather than
            // vanishing from the breakdown.
            var footprints = orderedFootprints(
                    "hegemony", new MarketFootprint(0, 0, 0));

            var standings = SystemStandings.rankByDominationScore(
                    footprints, HolderGrouping.identity());

            assertThat(standings).containsExactly(
                    new GroupStanding("hegemony", 0, List.of(new FactionStanding("hegemony", 0))));
        }

        @Test
        void ranksABlocAboveALoneFactionNoSingleMemberWouldOutrank() {
            // Neither allied member outscores the outsider alone, but their summed bloc does, so the
            // bloc ranks first - the two-tier sum, not any single member, decides the top tier.
            var footprints = orderedFootprints(
                    "tritachyon", new MarketFootprint(7, 7, 0),
                    "hegemony", new MarketFootprint(5, 3, 3),
                    "astral_armada", new MarketFootprint(4, 4, 0));

            var standings = SystemStandings.rankByDominationScore(footprints, allianceGrouping());

            assertThat(standings).extracting(GroupStanding::blocId)
                    .containsExactly("alliance-1", "tritachyon");
            assertThat(standings).extracting(GroupStanding::aggregateScore)
                    .containsExactly(9, 7);
            assertThat(standings.get(0).members()).extracting(FactionStanding::factionId)
                    .containsExactly("hegemony", "astral_armada");
        }
    }

    // Two allied factions folded into one bloc, with tritachyon left an outsider mapped to itself, so
    // a test can pit the bloc's summed score against the lone faction off one grouping.
    private static HolderGrouping allianceGrouping() {
        return new HolderGrouping(
                Map.of("hegemony", "alliance-1", "astral_armada", "alliance-1"),
                Map.of("alliance-1", "hegemony"),
                Map.of("alliance-1", "Allied Powers"));
    }

    // Builds the footprint map preserving insertion order, so a test can list the higher-scoring or
    // higher-id entry first and still expect the rule to rank it correctly.
    private static Map<String, MarketFootprint> orderedFootprints(Object... idsAndFootprints) {
        var footprints = new LinkedHashMap<String, MarketFootprint>();
        for (var i = 0; i < idsAndFootprints.length; i += 2) {
            footprints.put((String) idsAndFootprints[i],
                    (MarketFootprint) idsAndFootprints[i + 1]);
        }
        return footprints;
    }
}
