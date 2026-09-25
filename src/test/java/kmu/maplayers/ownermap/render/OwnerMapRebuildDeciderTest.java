package kmu.maplayers.ownermap.render;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.layer.ScreenMemoryScope;
import kmu.maplayers.base.layer.ScreenMemoryScopes;
import kmu.maplayers.base.machinery.SectorMapMachinery;
import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.sidebar.FilterSelection;
import kmu.maplayers.ownermap.OwnerPaintedViewFake;
import kmu.maplayers.ownermap.owners.holders.HolderProviderFake;
import kmu.maplayers.ownermap.preferences.OwnerMapBodyPreferencesFixtures;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Map;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

/**
 * Pins what a frame is decided to owe, and what the baselines that decision is taken against
 * advance on: a first frame owes everything; a frame after a recorded rebuild with nothing moved
 * owes nothing; the geometry signal owes a recut; a moved pick owes a rebuild but no recut; and
 * the baselines stand still until a stage is reported complete, so a rebuild that threw is asked
 * for again. Beside those, when the standing holding may be painted over rather than read, and how
 * the recut a caller is about to take names the reading it moves away from.
 *
 * <p>Nothing here opens a reading of the sector: the decision is answerable from the board and
 * the settings alone, which is the property the class exists to keep, so the sector the machinery
 * is built over is a bare mock nothing reaches. What is stubbed is what no test JVM answers - the
 * live LunaLib reads and the per-save picks - through the same seams every rebuild suite opens.
 */
final class OwnerMapRebuildDeciderTest {

    private static final String HEGEMONY_ID = "hegemony";

    // The screen each decision is taken for. A stand-in rather than one of the two live screens:
    // nothing here turns on which panel the frame was prepared for.
    // Any view: what the decider answers turns on what moved, never on who is painting.
    private static final OwnerPaintedViewFake VIEW =
        new OwnerPaintedViewFake(Map.of(), HolderProviderFake.createHoldingNothing());

    private static final ScreenMemoryScope SCREEN = ScreenMemoryScopes.createStandInScreen();

    private StaticSeams seams;
    private MockedStatic<FilterSelection> filterSelectionSeam;
    private SectorMapMachinery machinery;
    private OwnerMapRebuildDecider decider;

    @BeforeEach
    void openSeamsAndBuildTheDecider() {

        seams = new StaticSeams();
        filterSelectionSeam = seams.openSeam(FilterSelection.class);
        machinery = new SectorMapMachinery(mock(SectorAPI.class));
        decider = new OwnerMapRebuildDecider(machinery, OwnerMapBodyPreferencesFixtures.createUnderTestKeys());
    }

    @AfterEach
    void closeSeams() {

        seams.closeEverySeam();
    }

    @Nested
    class DecideWhatIsStale {

        @Test
        void decideWhatIsStaleOwesBothHalvesOnTheFirstFrame() {
            // Every baseline starts at its rebuild-forcing seed, and nothing has been cut, so the
            // first frame after a sector is installed on builds both halves from scratch.
            var staleHalves = decideWithNothingBuilt();

            assertThat(staleHalves.isContentStale())
                .isTrue();
            assertThat(staleHalves.isCellCutStale())
                .isTrue();
        }

        @Test
        void decideWhatIsStaleOwesNothingOnceTheRebuildIsRecordedAndNothingMoved() {
            // The frame the map spends nearly all of its life on: a rebuild has been recorded and
            // no input moved since, so the decision stops here and opens no reading.
            decideAndRecordARebuild();

            var staleHalves = decideAfterABuild();

            assertThat(staleHalves.isContentStale())
                .isFalse();
            assertThat(staleHalves.isCellCutStale())
                .isFalse();
        }

        @Test
        void decideWhatIsStaleOwesARecutWhenTheGeometrySignalIsRaised() {

            decideAndRecordARebuild();
            machinery.resolveRefreshBoard().requestRefresh(MapLayerCommonRefreshSignal.GEOMETRY);

            var staleHalves = decideAfterABuild();

            assertThat(staleHalves.isCellCutStale())
                .isTrue();
            assertThat(staleHalves.isContentStale())
                .isTrue();
        }

        @Test
        void decideWhatIsStaleOwesARebuildButNoRecutWhenTheSpotlitBlocMoves() {
            // A pick is a different map but the same cells: the content revision folds the sampled
            // reading and moves, while nothing the cells are cut from has.
            var rebuilt = decideAndRecordARebuild();

            spotlight(HEGEMONY_ID);

            var staleHalves = decideAfterABuild();

            assertThat(staleHalves.isContentStale())
                .isTrue();
            assertThat(staleHalves.isCellCutStale())
                .isFalse();
            assertThat(staleHalves.holdingRevision())
                .isNotEqualTo(rebuilt.holdingRevision());
        }

        @Test
        void decideWhatIsStaleGoesOnOwingTheRebuildUntilAStageIsRecorded() {
            // The baselines advance only as the cache reports each stage complete, which is what
            // lets a rebuild that threw part way be asked for again rather than taken as done.
            decideWithNothingBuilt();

            var staleHalves = decideAfterABuild();

            assertThat(staleHalves.isContentStale())
                .isTrue();
            assertThat(staleHalves.isCellCutStale())
                .isTrue();
        }
    }

    @Nested
    class CanReuseStandingHolding {

        @Test
        void canReuseStandingHoldingWhenNothingReachingTheResolveMoved() {

            decideAndRecordARebuild();

            assertThat(decider.canReuseStandingHolding(decideAfterABuild(), true, Set.of()))
                .isTrue();
        }

        @Test
        void canReuseStandingHoldingIsFalseWhileNothingIsStanding() {
            // A cache's first rebuild has nothing to keep, whatever the revisions say.
            decideAndRecordARebuild();

            assertThat(decider.canReuseStandingHolding(decideAfterABuild(), false, Set.of()))
                .isFalse();
        }

        @Test
        void canReuseStandingHoldingIsFalseWhenASystemIsMarked() {
            // A marked system is one the sector moved under, so a holding read before it moved no
            // longer says who holds it.
            decideAndRecordARebuild();

            assertThat(decider.canReuseStandingHolding(
                    decideAfterABuild(),
                    true,
                    Set.of(buildCellKey("marked"))))
                .isFalse();
        }

        @Test
        void canReuseStandingHoldingIsFalseWhenTheCellsAreRecut() {
            // A recut admits or drops systems, so the holding has to be read over the new set.
            decideAndRecordARebuild();
            machinery.resolveRefreshBoard().requestRefresh(MapLayerCommonRefreshSignal.GEOMETRY);

            assertThat(decider.canReuseStandingHolding(decideAfterABuild(), true, Set.of()))
                .isFalse();
        }

        @Test
        void canReuseStandingHoldingIsFalseWhenAPickReachingTheResolveMoved() {
            // The spotlight decides who holds a cell rather than only how it is coloured, so the
            // holding revision folds it and a moved pick reads the holding afresh.
            decideAndRecordARebuild();
            spotlight(HEGEMONY_ID);

            assertThat(decider.canReuseStandingHolding(decideAfterABuild(), true, Set.of()))
                .isFalse();
        }
    }

    @Nested
    class DescribeCellCutTransition {

        @Test
        void describeCellCutTransitionNamesTheNeverCutStateBeforeAnythingIsCut() {
            // The never-cut state is a reading of its own rather than a zero one, so the first
            // recut says so instead of naming a set of inputs no player was ever under.
            assertThat(decider.describeCellCutTransition(decideWithNothingBuilt()))
                .startsWith("from null to ");
        }

        @Test
        void describeCellCutTransitionNamesTheStandingReadingOnceACutIsRecorded() {
            // The other half of recording a cut: the reading a later recut moves away from is the
            // one that was actually cut, so a transition described after it names no absence.
            decideAndRecordARebuild();

            assertThat(decider.describeCellCutTransition(decideAfterABuild()))
                .doesNotContain("null");
        }
    }

    // Picks a bloc on the sidebar, as the seam standing in for the per-save pick reports it.
    private void spotlight(String blocId) {
        filterSelectionSeam
            .when(() -> FilterSelection.getSelectedIdOf(any()))
            .thenReturn(blocId);
    }

    // The first frame's decision, before anything has been built.
    private StaleHalves decideWithNothingBuilt() {
        return decider.decideWhatIsStale(VIEW, SCREEN, true);
    }

    // A later frame's decision, once something stands to compare against.
    private StaleHalves decideAfterABuild() {
        return decider.decideWhatIsStale(VIEW, SCREEN, false);
    }

    // One whole rebuild as the cache reports it: the first decision taken, and every stage of the
    // rebuild it owes recorded complete under it.
    private StaleHalves decideAndRecordARebuild() {

        var staleHalves = decideWithNothingBuilt();

        decider.recordCellsCut(staleHalves);
        decider.recordHoldingResolved(staleHalves);
        decider.recordContentRebuilt(staleHalves);

        return staleHalves;
    }
}
