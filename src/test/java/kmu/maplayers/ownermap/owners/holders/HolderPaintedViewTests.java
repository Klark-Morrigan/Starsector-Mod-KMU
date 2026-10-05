package kmu.maplayers.ownermap.owners.holders;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.systems.SectorPassIndex;
import kmlib.testfixtures.statics.StaticSeams;

import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.base.visibility.systems.MapVisibilityRules;
import kmu.maplayers.ownermap.holding.DecivilisedColonyHabitation;
import kmu.maplayers.ownermap.holding.HolderGrouping;
import kmu.maplayers.ownermap.holding.HolderGroupingFixture;
import kmu.maplayers.ownermap.owners.OwnerReading;
import kmu.maplayers.ownermap.owners.OwnerReadingFake;
import kmu.maplayers.ownermap.owners.SectorWalk;
import kmu.maplayers.ownermap.render.style.HolderCategories;
import kmu.maplayers.ownermap.render.style.OwnerCategories;
import kmu.maplayers.ownermap.ribbon.RibbonPlan;
import kmu.maplayers.ownermap.ribbon.RibbonPlanInputs;
import kmu.maplayers.ownermap.ribbon.RibbonPlanRules;
import kmu.maplayers.ownermap.ribbon.RibbonSegmentLengths;
import kmu.maplayers.ownermap.ribbon.SystemRibbonPlanner;
import kmu.maplayers.ownermap.ribbon.UncontestedRibbonRuns;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

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
 */
final class HolderPaintedViewTests {

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

            var viewFake = new CountingHolderViewFake();

            viewFake.resolveViewReading(mock(SectorAPI.class));

            assertThat(viewFake.groupingSamples)
                .isEqualTo(1);
        }

        @Test
        void resolvesTheReadingOverTheSampledGroupingAndTheHandedSector() {

            var viewFake = new CountingHolderViewFake();
            var sectorMock = mock(SectorAPI.class);

            viewFake.resolveViewReading(sectorMock);

            assertThat(viewFake.readingSectors)
                .containsExactly(sectorMock);
            assertThat(viewFake.readingGroupings)
                .singleElement()
                .isSameAs(SAMPLED_GROUPING);
        }

        @Test
        void foldsTheSourceUnderThatSameSampling() {

            var viewFake = new CountingHolderViewFake();

            assertThat(viewFake.resolveViewReading(mock(SectorAPI.class)).source())
                .isInstanceOfSatisfying(
                    HolderOwnerSource.class,
                    holderSource -> assertThat(holderSource.grouping()).isSameAs(SAMPLED_GROUPING));
        }

        @Test
        void answersWithItselfAsTheView() {

            var viewFake = new CountingHolderViewFake();

            assertThat(viewFake.resolveViewReading(mock(SectorAPI.class)).view())
                .isSameAs(viewFake);
        }

        @Test
        void countsBandsThroughTheViewsOwnPlanner() {
            // The band explains the fill it sits inside, so the source counts by the mechanic the
            // view declared rather than one of its own.
            seams.openSeam(DecivilisedColonyHabitation.class)
                .when(DecivilisedColonyHabitation::readFromLunaSettings)
                .thenReturn(DecivilisedColonyHabitation.COUNTS_AS_POPULATED);

            var viewFake = new CountingHolderViewFake();

            var planner = viewFake.resolveViewReading(mock(SectorAPI.class))
                .source()
                .resolveRibbonPlanner(
                    new SectorWalk(new SectorPassIndex(null), MapVisibilityRules.BASE),
                    new RibbonPlanRules(new RibbonSegmentLengths(3, 1), new UncontestedRibbonRuns(false)));

            assertThat(planner)
                .isSameAs(CountingHolderViewFake.PLANNER);
        }
    }

    /** A holder view counting how often its grouping is sampled and what its reading is built over. */
    private static final class CountingHolderViewFake implements HolderPaintedView {

        static final SystemRibbonPlanner PLANNER = system -> RibbonPlan.NONE;

        private final List<SectorAPI> readingSectors = new ArrayList<>();
        private final List<HolderGrouping> readingGroupings = new ArrayList<>();
        private int groupingSamples;

        @Override
        public HolderGrouping resolveGrouping() {
            groupingSamples++;
            return SAMPLED_GROUPING;
        }

        @Override
        public HolderGrouping resolveContestGrouping() {
            return HolderGrouping.identity();
        }

        @Override
        public HolderProvider resolveHolderProvider() {
            return HolderProviderFake.createHoldingNothing();
        }

        @Override
        public SystemHolderResolveSource resolveSystemHolderResolveSource() {
            return SystemHolderResolveFake.createSourceHoldingNothing();
        }

        @Override
        public SystemRibbonPlanner resolveRibbonPlanner(RibbonPlanInputs inputs) {
            return PLANNER;
        }

        @Override
        public OwnerReading resolveOwnerReading(SectorAPI sector, HolderGrouping grouping) {
            readingSectors.add(sector);
            readingGroupings.add(grouping);
            return OwnerReadingFake.createAnsweringNothing();
        }

        @Override
        public OwnerCategories resolveCategories() {
            return HolderCategories.INSTANCE;
        }

        @Override
        public String getId() {
            return "counting";
        }

        @Override
        public String getSegmentLabelKey() {
            return "counting_label";
        }

        @Override
        public int getContentRevision(MapLayerRefreshBoard board) {
            return 0;
        }
    }
}
