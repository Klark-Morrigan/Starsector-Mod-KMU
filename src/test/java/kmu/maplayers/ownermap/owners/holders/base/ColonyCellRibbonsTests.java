package kmu.maplayers.ownermap.owners.holders.base;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.colour.ColourPair;

import kmu.maplayers.base.ribbon.RibbonPlan;
import kmu.maplayers.base.ribbon.RibbonSegment;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static kmu.maplayers.ownermap.owners.holders.base.HolderGroupingFixture.GROUP_BLOC_ID;
import static kmu.maplayers.ownermap.owners.holders.base.RibbonPlanFixtures.PERSEAN;
import static kmu.maplayers.ownermap.owners.holders.base.RibbonPlanFixtures.PERSEAN_BRIGHT;
import static kmu.maplayers.ownermap.owners.holders.base.RibbonPlanFixtures.PERSEAN_DARK;
import static kmu.maplayers.ownermap.owners.holders.base.RibbonPlanFixtures.VANISHED_BLOC;
import static kmu.maplayers.ownermap.owners.holders.base.RibbonPlanFixtures.buildAlliedInputsFor;
import static kmu.maplayers.ownermap.owners.holders.base.RibbonPlanFixtures.buildInputsFor;
import static kmu.maplayers.ownermap.ribbon.OwnerRibbonRunsFixtures.HEGEMONY;
import static kmu.maplayers.ownermap.ribbon.OwnerRibbonRunsFixtures.HEGEMONY_BRIGHT;
import static kmu.maplayers.ownermap.ribbon.OwnerRibbonRunsFixtures.HEGEMONY_DARK;
import static kmu.maplayers.ownermap.ribbon.OwnerRibbonRunsFixtures.NO_PAINTER;
import static kmu.maplayers.ownermap.ribbon.OwnerRibbonRunsFixtures.TRITACHYON;
import static kmu.maplayers.ownermap.ribbon.OwnerRibbonRunsFixtures.TRITACHYON_BRIGHT;
import static kmu.maplayers.ownermap.ribbon.OwnerRibbonRunsFixtures.TRITACHYON_DARK;

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
 *
 * <p>The member cases stand a per-member palette read in, since the live one answers a bloc's shades
 * for every member and so cannot show the grouping deciding the run while the read decides the
 * segment.
 */
final class ColonyCellRibbonsTests {

    // The size every stated colony carries. A band counts holdings rather than weighing them, so a
    // case varying it would vary nothing the rule can see.
    private static final int COLONY_SIZE = 5;

    // The pairs a per-member palette read answers, in the shared shades a case reads a band back in.
    private static final ColourPair HEGEMONY_PALETTE = new ColourPair(HEGEMONY_BRIGHT, HEGEMONY_DARK);
    private static final ColourPair TRITACHYON_PALETTE = new ColourPair(TRITACHYON_BRIGHT, TRITACHYON_DARK);
    private static final ColourPair PERSEAN_PALETTE = new ColourPair(PERSEAN_BRIGHT, PERSEAN_DARK);

    @Nested
    class PlanCellRibbon {

        // Two Hegemony colonies and one of Tri-Tachyon's: three runs, the Hegemony's own parted
        // by its dark shade and closed off in it where the rival's run takes over.
        @Test
        void drawsOneSegmentPerColonyABlocHoldsInTheSystem() {

            // ARRANGE
            var expectedSegments = List.of(
                new RibbonSegment(HEGEMONY_BRIGHT, 3),
                new RibbonSegment(HEGEMONY_DARK, 1),
                new RibbonSegment(HEGEMONY_BRIGHT, 3),
                new RibbonSegment(HEGEMONY_DARK, 1),
                new RibbonSegment(TRITACHYON_BRIGHT, 3));

            // ACT
            var actualSegments = planFor(
                Optional.of(HEGEMONY),
                List.of(HEGEMONY, TRITACHYON),
                HEGEMONY, HEGEMONY, TRITACHYON).segments();

            // ASSERT
            assertThat(actualSegments)
                .containsExactlyElementsOf(expectedSegments);
        }

        // The single-holder cell, which most of the sector is: the fill has said whose system
        // it is, and the band says how much is in it.
        @Test
        void drawsThePainterAloneWhereItIsTheOnlyBlocPresent() {

            // ARRANGE
            var expectedSegments = List.of(new RibbonSegment(HEGEMONY_BRIGHT, 3));

            // ACT
            var actualSegments = planFor(Optional.of(HEGEMONY), List.of(HEGEMONY), HEGEMONY).segments();

            // ASSERT
            assertThat(actualSegments)
                .containsExactlyElementsOf(expectedSegments);
        }

        // A bloc the mechanic ranked and the system does not hold - a faction whose only colony
        // there is one the fog keeps back, say - raises no run at all rather than a run of no
        // width, so the band is the painter's own and carries no trace of the ranking.
        @Test
        void drawsNoRunForABlocRankedButPresentInNothing() {

            // ARRANGE
            var expectedSegments = List.of(new RibbonSegment(HEGEMONY_BRIGHT, 3));

            // ACT
            var actualSegments = planFor(
                Optional.of(HEGEMONY),
                List.of(HEGEMONY, TRITACHYON),
                HEGEMONY).segments();

            // ASSERT
            assertThat(actualSegments)
                .containsExactlyElementsOf(expectedSegments);
        }

        // Nothing counted means nothing to report, so the cell draws no band rather than an
        // empty one.
        @Test
        void drawsNoBandForASystemNobodyLivesIn() {

            // ACT
            var actualSegments = planFor(NO_PAINTER, List.of()).segments();

            // ASSERT
            assertThat(actualSegments)
                .isEmpty();
        }

        // The mechanic ranked the painter and Tri-Tachyon, and the Persean League is in the
        // system through a colony no score was computed from. The ranked blocs keep their places
        // and the unranked one draws behind them - a place among the ranking would state it took
        // part in a contest it never entered.
        @Test
        void keepsTheMechanicsRankingAheadOfTheBlocsItNeverRanked() {

            // ARRANGE
            var expectedSegments = List.of(
                new RibbonSegment(HEGEMONY_BRIGHT, 3),
                new RibbonSegment(HEGEMONY_DARK, 1),
                new RibbonSegment(TRITACHYON_BRIGHT, 3),
                new RibbonSegment(TRITACHYON_DARK, 1),
                new RibbonSegment(PERSEAN_BRIGHT, 3));

            // ACT
            var actualSegments = planFor(
                Optional.of(HEGEMONY),
                List.of(HEGEMONY, TRITACHYON),
                HEGEMONY, PERSEAN, TRITACHYON).segments();

            // ASSERT
            assertThat(actualSegments)
                .containsExactlyElementsOf(expectedSegments);
        }

        // Two blocs the mechanic did not score have no order to inherit, so they take the one
        // order that cannot depend on which of them the walk reached first.
        @Test
        void ordersTheBlocsNoMechanicRankedById() {

            // ARRANGE
            var expectedSegments = List.of(
                new RibbonSegment(HEGEMONY_BRIGHT, 3),
                new RibbonSegment(HEGEMONY_DARK, 1),
                new RibbonSegment(PERSEAN_BRIGHT, 3),
                new RibbonSegment(PERSEAN_DARK, 1),
                new RibbonSegment(TRITACHYON_BRIGHT, 3));

            // ACT
            var actualSegments = planFor(
                Optional.of(HEGEMONY),
                List.of(HEGEMONY),
                TRITACHYON, PERSEAN, HEGEMONY).segments();

            // ASSERT
            assertThat(actualSegments)
                .containsExactlyElementsOf(expectedSegments);
        }

        // Two members of one group rank as their bloc twice. The bloc keeps the place its first
        // mention took and draws one run, so the band counts its colony once rather than once per
        // member that ranked it.
        @Test
        void drawsABlocTheRankingNamesTwiceOnceInItsFirstPlace() {

            // ARRANGE
            var expectedSegments = List.of(
                new RibbonSegment(TRITACHYON_BRIGHT, 3),
                new RibbonSegment(TRITACHYON_DARK, 1),
                new RibbonSegment(HEGEMONY_BRIGHT, 3));

            // ACT
            var actualSegments = planFor(
                Optional.of(TRITACHYON),
                List.of(TRITACHYON, HEGEMONY, TRITACHYON),
                HEGEMONY, TRITACHYON).segments();

            // ASSERT
            assertThat(actualSegments)
                .containsExactlyElementsOf(expectedSegments);
        }

        // A system two blocs are settled in that no layer paints for anybody. The counting is
        // the same as ever - the painter is the contest's question, not the count's - and neither
        // bloc is ranked, so both come out in ID order.
        @Test
        void countsTheColoniesOfACellNoFillCovers() {

            // ARRANGE
            var expectedSegments = List.of(
                new RibbonSegment(HEGEMONY_BRIGHT, 3),
                new RibbonSegment(HEGEMONY_DARK, 1),
                new RibbonSegment(TRITACHYON_BRIGHT, 3));

            // ACT
            var actualSegments = planFor(NO_PAINTER, List.of(), TRITACHYON, HEGEMONY).segments();

            // ASSERT
            assertThat(actualSegments)
                .containsExactlyElementsOf(expectedSegments);
        }

        // A bloc whose colour faction has gone from the sector has nothing to paint a run in,
        // so it is left out rather than drawn colourless - and the band is the painter's own
        // colony, with no gap where the vanished bloc's run would have been.
        @Test
        void leavesOutABlocWithNoShadesToDrawIn() {

            // ARRANGE
            var expectedSegments = List.of(new RibbonSegment(HEGEMONY_BRIGHT, 3));

            // ACT
            var actualSegments = planFor(
                Optional.of(HEGEMONY),
                List.of(HEGEMONY, VANISHED_BLOC),
                HEGEMONY, VANISHED_BLOC).segments();

            // ASSERT
            assertThat(actualSegments)
                .containsExactlyElementsOf(expectedSegments);
        }

        // The vanished bloc is dropped before the contest is judged, so it is no rival: the
        // shortening reaches the painter's run as it would on a cell the painter holds alone.
        @Test
        void leavesACellUncontestedByABlocWithNoShades() {

            // ARRANGE
            var systemMock = mock(StarSystemAPI.class);
            var inputs = buildAlliedInputsFor(stubPassHolding(systemMock, HEGEMONY, VANISHED_BLOC));
            var expectedSegments = List.of(new RibbonSegment(HEGEMONY_BRIGHT, 1));

            // ACT
            var actualSegments = ColonyCellRibbons.planCellRibbon(
                Optional.of(HEGEMONY),
                systemMock,
                List.of(HEGEMONY, VANISHED_BLOC),
                inputs).segments();

            // ASSERT
            assertThat(actualSegments)
                .containsExactlyElementsOf(expectedSegments);
        }

        // A palette read answering per member, as a layer colouring by something the group
        // does not decide does. The group stays one run, parted as one holding, and its
        // segments come out member by member in owner ID order whatever order the walk met them.
        @Test
        void laysAGroupsRunMemberByMemberInOwnerIdOrder() {

            // ARRANGE
            var palettesMock = mock(SectorBlocPalettes.class);
            var colonyOwnerIdsByBlocId = Map.of(
                GROUP_BLOC_ID, List.of(TRITACHYON, HEGEMONY, TRITACHYON),
                PERSEAN, List.of(PERSEAN));
            var expectedSegments = List.of(
                new RibbonSegment(HEGEMONY_BRIGHT, 3),
                new RibbonSegment(HEGEMONY_DARK, 1),
                new RibbonSegment(TRITACHYON_BRIGHT, 3),
                new RibbonSegment(TRITACHYON_DARK, 1),
                new RibbonSegment(TRITACHYON_BRIGHT, 3),
                new RibbonSegment(TRITACHYON_DARK, 1),
                new RibbonSegment(PERSEAN_BRIGHT, 3));

            when(palettesMock.readSegmentPalette(GROUP_BLOC_ID, HEGEMONY))
                .thenReturn(HEGEMONY_PALETTE);
            when(palettesMock.readSegmentPalette(GROUP_BLOC_ID, TRITACHYON))
                .thenReturn(TRITACHYON_PALETTE);
            when(palettesMock.readSegmentPalette(PERSEAN, PERSEAN))
                .thenReturn(PERSEAN_PALETTE);

            // ACT
            var actualSegments = planForMembers(
                List.of(GROUP_BLOC_ID, PERSEAN),
                palettesMock,
                colonyOwnerIdsByBlocId).segments();

            // ASSERT
            assertThat(actualSegments)
                .containsExactlyElementsOf(expectedSegments);
        }

        // One member of the group has gone from the sector. Its colony loses its segment, and
        // the group's run still stands on the colony that can be coloured.
        @Test
        void dropsTheSegmentOfAColonyWhoseOwnerHasNoShadesAndKeepsItsRun() {

            // ARRANGE
            var palettesMock = mock(SectorBlocPalettes.class);
            var colonyOwnerIdsByBlocId = Map.of(
                GROUP_BLOC_ID, List.of(HEGEMONY, VANISHED_BLOC),
                TRITACHYON, List.of(TRITACHYON));
            var expectedSegments = List.of(
                new RibbonSegment(HEGEMONY_BRIGHT, 3),
                new RibbonSegment(HEGEMONY_DARK, 1),
                new RibbonSegment(TRITACHYON_BRIGHT, 3));

            when(palettesMock.readSegmentPalette(GROUP_BLOC_ID, HEGEMONY))
                .thenReturn(HEGEMONY_PALETTE);
            when(palettesMock.readSegmentPalette(TRITACHYON, TRITACHYON))
                .thenReturn(TRITACHYON_PALETTE);

            // ACT
            var actualSegments = planForMembers(
                List.of(GROUP_BLOC_ID, TRITACHYON),
                palettesMock,
                colonyOwnerIdsByBlocId).segments();

            // ASSERT
            assertThat(actualSegments)
                .containsExactlyElementsOf(expectedSegments);
        }

        // The group is ranked last and every colony it holds is owned by a member with no
        // shades. It drops out whole, so the band ends on the Hegemony's segment rather than on a
        // parting that would close a run onto nothing.
        @Test
        void leavesNoPartingBehindABlocAllOfWhoseColoniesDrop() {

            // ARRANGE
            var palettesMock = mock(SectorBlocPalettes.class);
            var colonyOwnerIdsByBlocId = Map.of(
                HEGEMONY, List.of(HEGEMONY),
                GROUP_BLOC_ID, List.of(VANISHED_BLOC, VANISHED_BLOC));
            var expectedSegments = List.of(new RibbonSegment(HEGEMONY_BRIGHT, 3));

            when(palettesMock.readSegmentPalette(HEGEMONY, HEGEMONY))
                .thenReturn(HEGEMONY_PALETTE);

            // ACT
            var actualSegments = planForMembers(
                List.of(HEGEMONY, GROUP_BLOC_ID),
                palettesMock,
                colonyOwnerIdsByBlocId).segments();

            // ASSERT
            assertThat(actualSegments)
                .containsExactlyElementsOf(expectedSegments);
        }
    }

    // The band for one cell painted for the first ranked bloc, whose system holds the colonies each
    // bloc is mapped to - by the faction owning each one - coloured through the palette read given.
    private static RibbonPlan planForMembers(
            List<String> rankedBlocIds,
            SectorBlocPalettes palettes,
            Map<String, List<String>> colonyOwnerIdsByBlocId) {

        var habitationByBlocId = new LinkedHashMap<String, BlocHabitation>();

        for (var blocId : rankedBlocIds) {
            habitationByBlocId.put(
                blocId,
                new BlocHabitation(colonyOwnerIdsByBlocId.get(blocId), COLONY_SIZE));
        }

        var systemMock = mock(StarSystemAPI.class);
        var passMock = mock(HolderPass.class);

        when(passMock.readHabitationIn(systemMock))
            .thenReturn(new SystemHabitation(List.of(), habitationByBlocId));
        when(passMock.palettes())
            .thenReturn(palettes);

        return ColonyCellRibbons.planCellRibbon(
            Optional.of(rankedBlocIds.get(0)),
            systemMock,
            rankedBlocIds,
            buildInputsFor(passMock));
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
                new BlocHabitation(List.of(blocId), COLONY_SIZE),
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
