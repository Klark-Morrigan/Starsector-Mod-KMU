package kmu.maplayers.ownermap.owners.holders.base;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.ownermap.ribbon.RibbonPlan;
import kmu.maplayers.ownermap.ribbon.RibbonSegment;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import static kmu.maplayers.ownermap.owners.holders.base.RibbonPlanFixtures.HEGEMONY;
import static kmu.maplayers.ownermap.owners.holders.base.RibbonPlanFixtures.HEGEMONY_BRIGHT;
import static kmu.maplayers.ownermap.owners.holders.base.RibbonPlanFixtures.HEGEMONY_DARK;
import static kmu.maplayers.ownermap.owners.holders.base.RibbonPlanFixtures.NO_PAINTER;
import static kmu.maplayers.ownermap.owners.holders.base.RibbonPlanFixtures.PERSEAN;
import static kmu.maplayers.ownermap.owners.holders.base.RibbonPlanFixtures.PERSEAN_BRIGHT;
import static kmu.maplayers.ownermap.owners.holders.base.RibbonPlanFixtures.PERSEAN_DARK;
import static kmu.maplayers.ownermap.owners.holders.base.RibbonPlanFixtures.TRITACHYON;
import static kmu.maplayers.ownermap.owners.holders.base.RibbonPlanFixtures.TRITACHYON_BRIGHT;
import static kmu.maplayers.ownermap.owners.holders.base.RibbonPlanFixtures.TRITACHYON_DARK;
import static kmu.maplayers.ownermap.owners.holders.base.RibbonPlanFixtures.VANISHED_BLOC;
import static kmu.maplayers.ownermap.owners.holders.base.RibbonPlanFixtures.buildAlliedInputsFor;
import static kmu.maplayers.ownermap.owners.holders.base.RibbonPlanFixtures.buildInputsFor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the one counting rule every painting mechanic hands its cells to: how many segments each
 * bloc's run earns, and where that run falls in the band.
 *
 * <p>The habitation a cell is counted from is stated outright through a stand-in pass, since which
 * colonies it admits is the pass's own projection and is pinned in its own suite. What the band
 * makes of a real pass's walk - the fog, a reveal, a derelict, a fold - is read end to end in
 * {@link ColonyCellRibbonsIntegrationTests}.
 *
 * <p>The ordering cases are posed on a ranking that does not cover every bloc present, which is the
 * shape the count and the ranking produce between them: a mechanic ranks the blocs it scored, and
 * the habitation projection hands back blocs it never weighed.
 *
 * <p>The segment grammar itself has its own suite, so what is asserted here is the counts and the
 * order reaching it - read off the runs, those being the only place either is visible.
 */
final class ColonyCellRibbonsTests {

    // The size every stated colony carries. A band counts holdings rather than weighing them, so a
    // case varying it would vary nothing the rule can see.
    private static final int COLONY_SIZE = 5;

    @Nested
    class PlanCellRibbon {

        @Test
        void drawsOneSegmentPerColonyABlocHoldsInTheSystem() {
            // Two Hegemony colonies and one of Tri-Tachyon's: three runs, the Hegemony's own parted
            // by its dark shade and closed off in it where the rival's run takes over.
            assertThat(planFor(
                    Optional.of(HEGEMONY),
                    List.of(HEGEMONY, TRITACHYON),
                    HEGEMONY, HEGEMONY, TRITACHYON).segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }

        @Test
        void drawsThePainterAloneWhereItIsTheOnlyBlocPresent() {
            // The single-holder cell, which most of the sector is: the fill has said whose system
            // it is, and the band says how much is in it.
            assertThat(planFor(Optional.of(HEGEMONY), List.of(HEGEMONY), HEGEMONY).segments())
                .containsExactly(new RibbonSegment(HEGEMONY_BRIGHT, 3));
        }

        @Test
        void drawsNoRunForABlocRankedButPresentInNothing() {
            // A bloc the mechanic ranked and the system does not hold - a faction whose only colony
            // there is one the fog keeps back, say - raises no run at all rather than a run of no
            // width, so the band is the painter's own and carries no trace of the ranking.
            assertThat(planFor(Optional.of(HEGEMONY), List.of(HEGEMONY, TRITACHYON), HEGEMONY).segments())
                .containsExactly(new RibbonSegment(HEGEMONY_BRIGHT, 3));
        }

        @Test
        void drawsNoBandForASystemNobodyLivesIn() {
            // Nothing counted means nothing to report, so the cell draws no band rather than an
            // empty one.
            assertThat(planFor(NO_PAINTER, List.of()).segments())
                .isEmpty();
        }

        @Test
        void keepsTheMechanicsRankingAheadOfTheBlocsItNeverRanked() {
            // The mechanic ranked the painter and Tri-Tachyon, and the Persean League is in the
            // system through a colony no score was computed from. The ranked blocs keep their places
            // and the unranked one draws behind them - a place among the ranking would state it took
            // part in a contest it never entered.
            assertThat(planFor(
                    Optional.of(HEGEMONY),
                    List.of(HEGEMONY, TRITACHYON),
                    HEGEMONY, PERSEAN, TRITACHYON).segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3),
                    new RibbonSegment(TRITACHYON_DARK, 1),
                    new RibbonSegment(PERSEAN_BRIGHT, 3));
        }

        @Test
        void ordersTheBlocsNoMechanicRankedById() {
            // Two blocs the mechanic did not score have no order to inherit, so they take the one
            // order that cannot depend on which of them the walk reached first.
            assertThat(planFor(
                    Optional.of(HEGEMONY),
                    List.of(HEGEMONY),
                    TRITACHYON, PERSEAN, HEGEMONY).segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(PERSEAN_BRIGHT, 3),
                    new RibbonSegment(PERSEAN_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }

        @Test
        void drawsABlocTheRankingNamesTwiceOnceInItsFirstPlace() {
            // Two members of one group rank as their bloc twice. The bloc keeps the place its first
            // mention took and draws one run, so the band counts its colony once rather than once per
            // member that ranked it.
            assertThat(planFor(
                    Optional.of(TRITACHYON),
                    List.of(TRITACHYON, HEGEMONY, TRITACHYON),
                    HEGEMONY, TRITACHYON).segments())
                .containsExactly(
                    new RibbonSegment(TRITACHYON_BRIGHT, 3),
                    new RibbonSegment(TRITACHYON_DARK, 1),
                    new RibbonSegment(HEGEMONY_BRIGHT, 3));
        }

        @Test
        void countsTheColoniesOfACellNoFillCovers() {
            // A system two blocs are settled in that no layer paints for anybody. The counting is
            // the same as ever - the painter is the contest's question, not the count's - and neither
            // bloc is ranked, so both come out in ID order.
            assertThat(planFor(NO_PAINTER, List.of(), TRITACHYON, HEGEMONY).segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }

        @Test
        void leavesOutABlocWithNoShadesToDrawIn() {
            // A bloc whose colour faction has gone from the sector has nothing to paint a run in,
            // so it is left out rather than drawn colourless - and the band is the painter's own
            // colony, with no gap where the vanished bloc's run would have been.
            assertThat(planFor(
                    Optional.of(HEGEMONY),
                    List.of(HEGEMONY, VANISHED_BLOC),
                    HEGEMONY, VANISHED_BLOC).segments())
                .containsExactly(new RibbonSegment(HEGEMONY_BRIGHT, 3));
        }

        @Test
        void leavesACellUncontestedByABlocWithNoShades() {
            // The vanished bloc is dropped before the contest is judged, so it is no rival: the
            // shortening reaches the painter's run as it would on a cell the painter holds alone.
            var systemMock = mock(StarSystemAPI.class);

            var plan = ColonyCellRibbons.planCellRibbon(
                Optional.of(HEGEMONY),
                systemMock,
                List.of(HEGEMONY, VANISHED_BLOC),
                buildAlliedInputsFor(stubPassHolding(systemMock, HEGEMONY, VANISHED_BLOC)));

            assertThat(plan.segments())
                .containsExactly(new RibbonSegment(HEGEMONY_BRIGHT, 1));
        }
    }

    // The band for one cell whose system holds one colony per named bloc, under the standard laying
    // rules and nobody standing with anybody - leaving a case to state the painter, the ranking and
    // who lives there.
    private static RibbonPlan planFor(
            Optional<String> paintingBlocId,
            List<String> rankedBlocIds,
            String... colonyBlocIds) {

        var systemMock = mock(StarSystemAPI.class);

        return ColonyCellRibbons.planCellRibbon(
            paintingBlocId,
            systemMock,
            rankedBlocIds,
            buildInputsFor(stubPassHolding(systemMock, colonyBlocIds)));
    }

    // A pass answering the system's habitation as one colony per named bloc, folded in the order
    // named - a bloc named twice lives on two colonies there.
    private static HolderPass stubPassHolding(StarSystemAPI system, String... colonyBlocIds) {

        var habitationByBlocId = new LinkedHashMap<String, BlocHabitation>();

        for (var blocId : colonyBlocIds) {
            habitationByBlocId.merge(
                blocId,
                new BlocHabitation(1, COLONY_SIZE),
                BlocHabitation::addHabitation);
        }

        // Built ahead of the stubbing below, since it stubs mocks of its own.
        var palettes = RibbonPlanFixtures.buildShadedPalettes();
        var passMock = mock(HolderPass.class);

        when(passMock.readHabitationIn(system))
            .thenReturn(new SystemHabitation(List.of(), habitationByBlocId));
        when(passMock.palettes())
            .thenReturn(palettes);

        return passMock;
    }
}
