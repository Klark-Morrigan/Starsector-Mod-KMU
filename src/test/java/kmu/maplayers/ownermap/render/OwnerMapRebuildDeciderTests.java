package kmu.maplayers.ownermap.render;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.testfixtures.statics.StaticSeams;

import kmu.maplayers.base.layer.ScreenMemoryScope;
import kmu.maplayers.base.layer.ScreenMemoryScopes;
import kmu.maplayers.base.machinery.SectorMapMachinery;
import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.sidebar.FilterSelection;
import kmu.maplayers.ownermap.OwnerPaintedViewFake;
import kmu.maplayers.ownermap.owners.OwnerSourceFake;
import kmu.maplayers.ownermap.preferences.NameFormatPreference;
import kmu.maplayers.ownermap.preferences.OwnerMapBodyPreferences;
import kmu.maplayers.ownermap.preferences.OwnerMapBodyPreferencesFixtures;
import kmu.maplayers.ownermap.preferences.OwnerNameFormatChoice;
import kmu.settings.KmuLunaSettings;
import kmu.settings.KmuOwnerMapGeometrySettings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/**
 * Pins what a frame is decided to owe, what the baselines that decision is taken against advance
 * on, and when the standing holding may be painted over rather than read.
 *
 * <p>Nothing here opens a reading of the sector: the decision is answerable from the board and
 * the settings alone, which is the property the class exists to keep, so the sector the machinery
 * is built over is a bare mock nothing reaches. What is stubbed is what no test JVM answers - the
 * live LunaLib reads and the per-save picks - through seams that otherwise call the real reads, so
 * a case moves only the one it is about.
 */
final class OwnerMapRebuildDeciderTests {

    private static final String HEGEMONY_ID = "hegemony";

    // A cell reach no default lands on, so a recut seen after it is the reach's doing.
    private static final double MOVED_CELL_RADIUS = 1234.0;

    // The screen each decision is taken for. A stand-in rather than one of the two live screens:
    // nothing here turns on which panel the frame was prepared for.
    private static final ScreenMemoryScope SCREEN = ScreenMemoryScopes.createStandInScreen();

    private MockedStatic<FilterSelection> filterSelectionSeam;
    private MockedStatic<KmuLunaSettings> lunaSettingsSeam;
    private MockedStatic<KmuOwnerMapGeometrySettings> geometrySettingsSeam;

    private StaticSeams seams;
    private NameFormatPreference nameFormatMock;
    private SectorMapMachinery machinery;
    private int settingsRevision;

    // Any view: what the decider answers turns on what moved, never on who is painting. One per
    // case, because a case moving its fingerprint must not move the next case's.
    private OwnerPaintedViewFake viewFake;
    private OwnerMapRebuildDecider decider;

    @BeforeEach
    void openSeamsAndBuildTheDecider() {

        seams = new StaticSeams();
        filterSelectionSeam = seams.openSeam(FilterSelection.class);

        lunaSettingsSeam = seams.holdSeam(
            mockStatic(KmuLunaSettings.class, Mockito.CALLS_REAL_METHODS));
        geometrySettingsSeam = seams.holdSeam(
            mockStatic(KmuOwnerMapGeometrySettings.class, Mockito.CALLS_REAL_METHODS));

        nameFormatMock = mock(NameFormatPreference.class);

        pickNameFormat(OwnerNameFormatChoice.FULL);

        var preferences = OwnerMapBodyPreferencesFixtures.createUnderTestKeys();

        machinery = new SectorMapMachinery(mock(SectorAPI.class));
        viewFake = new OwnerPaintedViewFake(new OwnerSourceFake());
        decider = new OwnerMapRebuildDecider(
            machinery,
            new OwnerMapBodyPreferences(
                nameFormatMock,
                preferences.uninhabitedOutline(),
                preferences.filterRecede()));
    }

    @AfterEach
    void closeSeams() {

        seams.closeEverySeam();
    }

    @Nested
    class DecideWhatIsStale {

        @Test
        void owesBothHalvesOnTheFirstFrame() {
            // Every baseline starts at its rebuild-forcing seed, and nothing has been cut, so the
            // first frame after a sector is installed on builds both halves from scratch.
            var staleHalves = decideWithNothingBuilt();

            assertThat(staleHalves.isContentStale())
                .isTrue();
            assertThat(staleHalves.isCellCutStale())
                .isTrue();
        }

        @Test
        void owesNothingOnceTheRebuildIsRecordedAndNothingMoved() {
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
        void owesARebuildButNoRecutWhileNothingIsBuilt() {
            // Nothing built forces the build even when every revision matches its baseline, but
            // the cells were cut, so they stand.
            decideAndRecordARebuild();

            var staleHalves = decideWithNothingBuilt();

            assertThat(staleHalves.isContentStale())
                .isTrue();
            assertThat(staleHalves.isCellCutStale())
                .isFalse();
        }

        @Test
        void owesARecutWhenTheGeometrySignalIsRaised() {

            decideAndRecordARebuild();
            raiseTheGeometrySignal();

            var staleHalves = decideAfterABuild();

            assertThat(staleHalves.isCellCutStale())
                .isTrue();
            assertThat(staleHalves.isContentStale())
                .isTrue();
        }

        @Test
        void owesARecutWhenACellSettingMovesWithoutTheGeometrySignal() {
            // The cell reach reseeds every cell without the reachable set moving, which is why the
            // cut is asked of what it is cut from rather than of the signal.
            decideAndRecordARebuild();
            moveTheCellRadius();

            assertThat(decideAfterABuild().isCellCutStale())
                .isTrue();
        }

        @Test
        void keepsTheCellSettingsItReadWhileTheSettingsRevisionHolds() {
            // Every cell setting is a LunaLib field, so an unmoved revision means an unmoved
            // setting, and the per-frame decision reads them once rather than every frame.
            decideAndRecordARebuild();
            decideAfterABuild();
            decideAfterABuild();

            geometrySettingsSeam.verify(
                KmuOwnerMapGeometrySettings::getOwnerMapCellRadius,
                times(1));
        }

        @Test
        void owesARebuildButNoRecutWhenOnlyTheSettingsRevisionMoves() {
            // A settings change restyles every cell over the fixed geometry, so it owes a rebuild;
            // the cut reads the same values again and so owes none.
            decideAndRecordARebuild();
            moveTheSettingsRevision();

            var staleHalves = decideAfterABuild();

            assertThat(staleHalves.isContentStale())
                .isTrue();
            assertThat(staleHalves.isCellCutStale())
                .isFalse();
        }

        @Test
        void owesARebuildWhenTheViewsLiveInputsMove() {
            // The view's fingerprint stands for whatever it samples, so a change to one rebuilds
            // without any setting moving.
            decideAndRecordARebuild();
            viewFake.setContentRevision(1);

            assertThat(decideAfterABuild().isContentStale())
                .isTrue();
        }

        @Test
        void owesARebuildButNoRecutWhenTheSpotlitOwnerMoves() {
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
        void owesARebuildButKeepsTheHoldingRevisionWhenAStylePickMoves() {
            // The name format reaches the paint and nothing before it, so the rebuild it owes is
            // one that may keep the holding it already read.
            var rebuilt = decideAndRecordARebuild();

            pickNameFormat(OwnerNameFormatChoice.SHORT);

            var staleHalves = decideAfterABuild();

            assertThat(staleHalves.isContentStale())
                .isTrue();
            assertThat(staleHalves.holdingRevision())
                .isEqualTo(rebuilt.holdingRevision());
        }

        @Test
        void goesOnOwingTheRebuildUntilAStageIsRecorded() {
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
        void isTrueWhenNothingReachingTheResolveMoved() {

            decideAndRecordARebuild();

            assertThat(decider.canReuseStandingHolding(decideAfterABuild(), true, Set.of()))
                .isTrue();
        }

        @Test
        void isTrueWhenOnlyAStylePickMoved() {
            // The case the holding revision exists for: a rebuild is owed, and the holding it would
            // read afresh is the one already standing.
            decideAndRecordARebuild();
            pickNameFormat(OwnerNameFormatChoice.SHORT);

            assertThat(decider.canReuseStandingHolding(decideAfterABuild(), true, Set.of()))
                .isTrue();
        }

        @Test
        void isFalseWhileNothingIsStanding() {
            // A cache's first rebuild has nothing to keep, whatever the revisions say.
            decideAndRecordARebuild();

            assertThat(decider.canReuseStandingHolding(decideAfterABuild(), false, Set.of()))
                .isFalse();
        }

        @Test
        void isFalseWhenASystemIsMarked() {
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
        void isFalseWhenTheCellsAreRecut() {
            // A recut admits or drops systems, so the holding has to be read over the new set.
            decideAndRecordARebuild();
            raiseTheGeometrySignal();

            assertThat(decider.canReuseStandingHolding(decideAfterABuild(), true, Set.of()))
                .isFalse();
        }

        @Test
        void isFalseWhenAPickReachingTheResolveMoved() {
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
        void namesTheNeverCutStateBeforeAnythingIsCut() {
            // The never-cut state is a reading of its own rather than a zero one, so the first
            // recut says so instead of naming a set of inputs no player was ever under.
            assertThat(decider.describeCellCutTransition(decideWithNothingBuilt()))
                .startsWith("from null to ");
        }

        @Test
        void namesTheStandingReadingOnceACutIsRecorded() {
            // The other half of recording a cut: the reading a later recut moves away from is the
            // one that was actually cut, so a transition described after it names no absence.
            decideAndRecordARebuild();

            assertThat(decider.describeCellCutTransition(decideAfterABuild()))
                .doesNotContain("null");
        }
    }

    @Nested
    class RecordCellsCut {

        @Test
        void clearsTheRecutAloneLeavingTheRebuildOwed() {

            decider.recordCellsCut(decideWithNothingBuilt());

            var staleHalves = decideAfterABuild();

            assertThat(staleHalves.isCellCutStale())
                .isFalse();
            assertThat(staleHalves.isContentStale())
                .isTrue();
        }
    }

    @Nested
    class RecordContentRebuilt {

        @Test
        void clearsTheRebuildWithoutLettingTheHoldingBeKept() {
            // The clusters and the holding advance apart: a rebuild that painted over a holding it
            // never recorded resolving has nothing a later rebuild may keep.
            var staleHalves = decideWithNothingBuilt();

            decider.recordCellsCut(staleHalves);
            decider.recordContentRebuilt(staleHalves);

            var nextHalves = decideAfterABuild();

            assertThat(nextHalves.isContentStale())
                .isFalse();
            assertThat(decider.canReuseStandingHolding(nextHalves, true, Set.of()))
                .isFalse();
        }

        @Test
        void paysAnOwedRebuild() {
            // The owed rebuild is the one that read the dropped systems, so once it is recorded the
            // map is current again.
            decideAndRecordARebuild();

            decider.recordRebuildOwed();
            decider.recordContentRebuilt(decideAfterABuild());

            assertThat(decideAfterABuild().isContentStale())
                .isFalse();
        }
    }

    @Nested
    class RecordHoldingResolved {

        @Test
        void letsTheHoldingBeKeptWhileTheRebuildIsStillOwed() {
            // A rebuild that resolved the holding and then threw leaves the holding good to keep,
            // so the retry paints over it rather than reading the sector again.
            var staleHalves = decideWithNothingBuilt();

            decider.recordCellsCut(staleHalves);
            decider.recordHoldingResolved(staleHalves);

            var nextHalves = decideAfterABuild();

            assertThat(nextHalves.isContentStale())
                .isTrue();
            assertThat(decider.canReuseStandingHolding(nextHalves, true, Set.of()))
                .isTrue();
        }
    }

    @Nested
    class RecordRebuildOwed {

        @Test
        void owesARebuildButNoRecutWithNothingElseMoved() {
            // A dropped batch is the only record that an owner changed, and no revision moved for
            // it, so the owed rebuild is what the next decision turns on. The cells stand: an owner
            // change seeds no cell.
            decideAndRecordARebuild();
            decider.recordRebuildOwed();

            var staleHalves = decideAfterABuild();

            assertThat(staleHalves.isContentStale())
                .isTrue();
            assertThat(staleHalves.isCellCutStale())
                .isFalse();
        }

        @Test
        void goesOnOwingTheRebuildUntilOneIsRecorded() {
            // A rebuild that threw before reporting itself leaves the change unread, so it is asked
            // for again.
            decideAndRecordARebuild();
            decider.recordRebuildOwed();
            decideAfterABuild();

            assertThat(decideAfterABuild().isContentStale())
                .isTrue();
        }
    }

    // Moves the cell reach, as a LunaLib save does: the new value, and the revision that says so.
    private void moveTheCellRadius() {

        geometrySettingsSeam
            .when(KmuOwnerMapGeometrySettings::getOwnerMapCellRadius)
            .thenReturn(MOVED_CELL_RADIUS);

        moveTheSettingsRevision();
    }

    // Announces a settings change with no value behind it, as LunaLib does for any of KMU's fields.
    private void moveTheSettingsRevision() {

        settingsRevision++;

        var announcedRevision = settingsRevision;

        lunaSettingsSeam
            .when(KmuLunaSettings::getSettingsRevision)
            .thenReturn(announcedRevision);
    }

    // Picks a name format on the sidebar, a style pick that reaches the paint alone.
    private void pickNameFormat(OwnerNameFormatChoice nameFormat) {

        when(nameFormatMock.getSelectedNameFormat(any()))
            .thenReturn(nameFormat);
    }

    // Raises the reachable-set signal on this decider's own board.
    private void raiseTheGeometrySignal() {

        machinery
            .resolveRefreshBoard()
            .requestRefresh(MapLayerCommonRefreshSignal.GEOMETRY);
    }

    // Picks an owner on the sidebar, as the seam standing in for the per-save pick reports it.
    private void spotlight(String ownerId) {

        filterSelectionSeam
            .when(() -> FilterSelection.getSelectedIdOf(any()))
            .thenReturn(ownerId);
    }

    // The first frame's decision, before anything has been built.
    private StaleHalves decideWithNothingBuilt() {

        return decider.decideWhatIsStale(viewFake, SCREEN, true);
    }

    // A later frame's decision, once something stands to compare against.
    private StaleHalves decideAfterABuild() {

        return decider.decideWhatIsStale(viewFake, SCREEN, false);
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
