package kmu.maplayers.ownermap.owners.holders.base;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.systems.SectorPassIndex;
import kmlib.starsector.ui.widgets.lists.ListSortModes;
import kmlib.testfixtures.statics.StaticSeams;

import kmu.maplayers.base.ribbon.RibbonSegmentLengths;
import kmu.maplayers.base.visibility.systems.MapVisibilityRules;
import kmu.maplayers.ownermap.owners.SectorWalk;
import kmu.maplayers.ownermap.picker.OwnerMetricsFake;
import kmu.maplayers.ownermap.picker.OwnerPresenceIndex;
import kmu.maplayers.ownermap.picker.OwnerStatsRead;
import kmu.maplayers.ownermap.picker.RankedOwner;
import kmu.maplayers.ownermap.ribbon.RibbonPlanRules;
import kmu.maplayers.ownermap.ribbon.UncontestedRibbonRuns;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static kmu.maplayers.ownermap.owners.holders.base.ColonyReadRulesFixtures.UNDER_THE_DEV_REVEAL;
import static kmu.maplayers.ownermap.owners.holders.base.ColonyReadRulesFixtures.UNDER_THE_FOG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Pins the one thing a view painting holders gets for declaring its parts: the reading and the
 * source the core asks for, assembled over one sampling of the view's grouping.
 *
 * <p>That single sampling is the whole of the type's promise. The grouping can be a live read of the
 * game, so a reading built over one sampling and a source folding under another would name and colour
 * a fold the owners were never resolved under - a disagreement that shows as a wrong colour rather
 * than as an error.
 *
 * <p>The picker read takes the same promise: one sampling for the walk and the rows naming it. What
 * the rows then are is the assembly's, pinned in its own suite.
 */
final class HolderPaintedViewTests {

    // The vocabulary a picker case is not about; the shared assembly bundles whatever it is handed.
    private static final ListSortModes<RankedOwner<OwnerMetricsFake>> ANY_MODES =
        new ListSortModes<>(List.of(), null);

    private static final HolderGrouping SAMPLED_GROUPING =
        HolderGroupingFixture.buildGroupOf("hegemony", "persean");

    private final StaticSeams seams = new StaticSeams();

    @AfterEach
    void closeSeams() {

        seams.closeEverySeam();
    }

    @Nested
    class ResolveViewReading {

        @Test
        void samplesTheGroupingOnceForBothAnswers() {

            var viewFake = HolderPaintedViewFake.createGroupingFake(SAMPLED_GROUPING);

            viewFake.resolveViewReading(mock(SectorAPI.class));

            assertThat(viewFake.countGroupingSamples())
                .isEqualTo(1);
        }

        @Test
        void resolvesTheReadingOverTheSampledGroupingAndTheHandedSector() {

            var viewFake = HolderPaintedViewFake.createGroupingFake(SAMPLED_GROUPING);
            var sectorMock = mock(SectorAPI.class);

            viewFake.resolveViewReading(sectorMock);

            assertThat(viewFake.listReadingSectors())
                .containsExactly(sectorMock);
            assertThat(viewFake.listReadingGroupings())
                .singleElement()
                .isSameAs(SAMPLED_GROUPING);
        }

        @Test
        void foldsTheSourceUnderThatSameSampling() {
            // Read back through the band planner the source builds, whose inputs carry the pass the
            // source opened - and so the grouping that pass folds under.
            seams
                .openSeam(DecivilisedColonyHabitation.class)
                .when(DecivilisedColonyHabitation::readFromLunaSettings)
                .thenReturn(DecivilisedColonyHabitation.COUNTS_AS_POPULATED);

            var viewFake = HolderPaintedViewFake.createGroupingFake(SAMPLED_GROUPING);

            viewFake
                .resolveViewReading(mock(SectorAPI.class))
                .source()
                .resolveRibbonPlanner(
                    new SectorWalk(new SectorPassIndex(null), MapVisibilityRules.BASE),
                    new RibbonPlanRules(new RibbonSegmentLengths(3, 1), new UncontestedRibbonRuns(false)));

            assertThat(viewFake.listPlannerInputs())
                .singleElement()
                .extracting(RibbonPlanInputs::grouping)
                .isSameAs(SAMPLED_GROUPING);
        }

        @Test
        void answersWithItselfAsTheView() {

            var viewFake = HolderPaintedViewFake.createGroupingFake(SAMPLED_GROUPING);

            assertThat(viewFake.resolveViewReading(mock(SectorAPI.class)).view())
                .isSameAs(viewFake);
        }

        @Test
        void countsBandsThroughTheViewsOwnPlanner() {
            // The band explains the fill it sits inside, so the source counts by the mechanic the
            // view declared rather than one of its own.
            seams
                .openSeam(DecivilisedColonyHabitation.class)
                .when(DecivilisedColonyHabitation::readFromLunaSettings)
                .thenReturn(DecivilisedColonyHabitation.COUNTS_AS_POPULATED);

            var viewFake = HolderPaintedViewFake.createGroupingFake(SAMPLED_GROUPING);
            var planner = viewFake
                .resolveViewReading(mock(SectorAPI.class))
                .source()
                .resolveRibbonPlanner(
                    new SectorWalk(new SectorPassIndex(null), MapVisibilityRules.BASE),
                    new RibbonPlanRules(new RibbonSegmentLengths(3, 1), new UncontestedRibbonRuns(false)));

            assertThat(planner)
                .isSameAs(HolderPaintedViewFake.PLANNER);
        }
    }

    @Nested
    class ReadPickerUnderOneGrouping {

        @Test
        void samplesTheGroupingOnce() {
            // A grouping can be a live read of the game, so a second sampling could hand the walk and
            // the rows two different memberships.
            var viewFake = HolderPaintedViewFake.createGroupingFake(SAMPLED_GROUPING);

            viewFake.readPickerUnderOneGrouping(
                mock(SectorAPI.class),
                grouping -> new OwnerStatsRead<>(Map.<String, OwnerMetricsFake>of(), OwnerPresenceIndex.EMPTY),
                ANY_MODES);

            assertThat(viewFake.countGroupingSamples())
                .isEqualTo(1);
        }

        @Test
        void readsTheWalkAndTheRowsUnderThatSampling() {
            // The rows come off the walk's numbers, so they are named under the grouping the walk was
            // read under rather than under one of their own.
            var viewFake = HolderPaintedViewFake.createGroupingFake(SAMPLED_GROUPING);
            var walkedGroupings = new ArrayList<HolderGrouping>();

            viewFake.readPickerUnderOneGrouping(
                mock(SectorAPI.class),
                grouping -> {
                    walkedGroupings.add(grouping);
                    return new OwnerStatsRead<>(Map.<String, OwnerMetricsFake>of(), OwnerPresenceIndex.EMPTY);
                },
                ANY_MODES);

            assertThat(walkedGroupings)
                .singleElement()
                .isSameAs(SAMPLED_GROUPING);
            assertThat(viewFake.listReadingGroupings())
                .singleElement()
                .isSameAs(SAMPLED_GROUPING);
        }
    }

    @Nested
    class ResolveOwnerPickerRead {

        @Test
        void handsThePlayersLiveColonyRulesOnWithTheSector() {
            // The core's sidebar and stale-selection heal hold no running pass, so the default reads
            // the player's live rules itself and hands them on beside the sector it was asked about.
            // The dev reveal stands in for them, being a reading no default could land on by chance.
            seams
                .openSeam(ColonyReadRules.class)
                .when(ColonyReadRules::readFromLunaSettings)
                .thenReturn(UNDER_THE_DEV_REVEAL);

            var viewFake = HolderPaintedViewFake.createGroupingFake(SAMPLED_GROUPING);
            var sectorMock = mock(SectorAPI.class);

            viewFake.resolveOwnerPickerRead(sectorMock);

            assertThat(viewFake.listPickerSectors())
                .containsExactly(sectorMock);
            assertThat(viewFake.listPickerRules())
                .containsExactly(UNDER_THE_DEV_REVEAL);
        }
    }

    @Nested
    class ResolveBlocPickerRead {

        @Test
        void defaultsToTheEmptyPickerSoASpotlightIsOptedInto() {
            // The base case is "no spotlight", so a view with nothing to list inherits a whole
            // answer rather than overriding with two arguments it would ignore. Pinned on the
            // fake rather than on the one view that currently relies on it, since what is under
            // test is the seam's default and not that view's choice to keep it.
            var viewFake = new HolderPaintedViewFake(Map.of());

            assertThat(viewFake.resolveBlocPickerRead(mock(SectorAPI.class), UNDER_THE_FOG).picker().items())
                .isEmpty();
        }

        @Test
        void defaultsToNoPresenceSoALookupAnswersRatherThanFaults() {
            // The other half of the default read. A view offering no rows can still be asked where a
            // bloc is - the ask reaches the seam before any row does - so the default has to answer
            // an empty set rather than leave a null for the lookup to fall over on.
            var viewFake = new HolderPaintedViewFake(Map.of());

            assertThat(viewFake.resolveBlocPickerRead(mock(SectorAPI.class), UNDER_THE_FOG)
                    .presenceIndex()
                    .readPresentSystemKeys("hegemony"))
                .isEmpty();
        }
    }

    @Nested
    class ResolveSelectableBlocGate {

        @Test
        void offersALoneFactionAndAGroupAlike() {
            // The default is for a view whose blocs are all of a kind, so it asks nothing about what
            // a bloc is: a lone faction and a group are both offered.
            var viewFake = HolderPaintedViewFake.createGroupingFake(SAMPLED_GROUPING);
            var gate = viewFake.resolveSelectableBlocGate(SAMPLED_GROUPING);

            assertThat(gate.test("tritachyon"))
                .isTrue();
            assertThat(gate.test(SAMPLED_GROUPING.resolveBlocId("hegemony")))
                .isTrue();
        }
    }
}
