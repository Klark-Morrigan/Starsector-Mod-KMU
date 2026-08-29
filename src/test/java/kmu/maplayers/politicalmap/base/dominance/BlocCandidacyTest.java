package kmu.maplayers.politicalmap.base.dominance;

import com.fs.starfarer.api.impl.campaign.ids.Factions;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link BlocCandidacy}: the neutral placeholder is barred from holding a system and everyone
 * else may hold one, at both shapes the rule is asked at - a plain faction id, and a bloc id read
 * through the grouping that names its colour faction.
 */
class BlocCandidacyTest {

    private static final String ALLIANCE_ID = "alliance:trade-bloc";
    private static final String HEGEMONY = "hegemony";
    private static final String TRITACHYON = "tritachyon";

    @Nested
    class IsCandidateFaction {

        @Test
        void barsTheNeutralFaction() {
            assertThat(BlocCandidacy.isCandidateFaction(Factions.NEUTRAL))
                .isFalse();
        }

        @Test
        void admitsAnOrdinaryFaction() {
            assertThat(BlocCandidacy.isCandidateFaction(HEGEMONY))
                .isTrue();
        }

        @Test
        void admitsAnIndependentFaction() {
            // Independent is the politically active owner with no allegiance, which is a different
            // thing from the placeholder the sector hands unowned markets to.
            assertThat(BlocCandidacy.isCandidateFaction(Factions.INDEPENDENT))
                .isTrue();
        }

        @Test
        void admitsAnIdNamingNoFaction() {
            assertThat(BlocCandidacy.isCandidateFaction(null))
                .isTrue();
        }
    }

    @Nested
    class CreateForGrouping {

        @Test
        void barsTheNeutralBlocUnderIdentity() {
            // Under identity a bloc id is its own faction id, so the two forms of the rule agree.
            assertThat(BlocCandidacy.createForGrouping(HolderGrouping.identity())
                    .test(Factions.NEUTRAL))
                .isFalse();
        }

        @Test
        void admitsAnOrdinaryBlocUnderIdentity() {
            assertThat(BlocCandidacy.createForGrouping(HolderGrouping.identity())
                    .test(HEGEMONY))
                .isTrue();
        }

        @Test
        void admitsAnAlliancePaintingInAnOrdinaryFaction() {
            // An alliance id is no faction id at all, so it is judged by the faction it stands as.
            var grouping = buildAllianceGrouping(HEGEMONY);

            assertThat(BlocCandidacy.createForGrouping(grouping).test(ALLIANCE_ID))
                .isTrue();
        }

        @Test
        void barsAnAlliancePaintingInTheNeutralFaction() {
            var grouping = buildAllianceGrouping(Factions.NEUTRAL);

            assertThat(BlocCandidacy.createForGrouping(grouping).test(ALLIANCE_ID))
                .isFalse();
        }

        @Test
        void admitsABlocTheGroupingDoesNotName() {
            // An id the grouping has no entry for falls through to itself, which is what keeps the
            // rule uniform whether or not anything grouped the factions.
            var grouping = buildAllianceGrouping(HEGEMONY);

            assertThat(BlocCandidacy.createForGrouping(grouping).test(TRITACHYON))
                .isTrue();
        }
    }

    // One alliance folding both factions, painting in whichever of them is named.
    private static HolderGrouping buildAllianceGrouping(String colourFactionId) {
        return new HolderGrouping(
            Map.of(
                HEGEMONY, ALLIANCE_ID,
                TRITACHYON, ALLIANCE_ID),
            Map.of(ALLIANCE_ID, colourFactionId),
            Map.of(ALLIANCE_ID, "Trade Bloc"));
    }
}
