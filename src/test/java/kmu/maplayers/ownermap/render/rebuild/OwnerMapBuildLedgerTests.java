package kmu.maplayers.ownermap.render.rebuild;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.cache.revisions.RevisionStamp;
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
 * Pins what a frame is decided to owe, what the ledger entries that decision is taken against advance
 * on, and when the standing owners may be painted over rather than read.
 *
 * <p>Nothing here opens a reading of the sector: the decision is answerable from the board and
 * the settings alone, which is the property the class exists to keep, so the sector the machinery
 * is built over is a bare mock nothing reaches. What is stubbed is what no test JVM answers - the
 * live LunaLib reads and the per-save picks - through seams that otherwise call the real reads, so
 * a case moves only the one it is about.
 */
final class OwnerMapBuildLedgerTests {

    private static final String HEGEMONY_ID = "hegemony";

    // A cell reach no default lands on, so a recut seen after it is the reach's doing.
    private static final double MOVED_CELL_RADIUS = 1234.0;

    // Two readings of a view's revisions that fold to one int under the Objects.hash polynomial:
    // 31 * (31 + 0) + 31 and 31 * (31 + 1) + 0 are both 992.
    private static final RevisionStamp FOLD_COLLIDING_VIEW_REVISIONS = RevisionStamp.of(0, 31);
    private static final RevisionStamp OTHER_FOLD_COLLIDING_VIEW_REVISIONS = RevisionStamp.of(1, 0);

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

    // Any view: what the ledger answers turns on what moved, never on who is painting. One per
    // case, because a case moving its revisions must not move the next case's.
    private OwnerPaintedViewFake viewFake;
    private OwnerMapBuildLedger ledger;

    @BeforeEach
    void openSeamsAndBuildTheLedger() {

        seams = new StaticSeams();
        filterSelectionSeam = seams.openSeam(FilterSelection.class);

        lunaSettingsSeam = seams.holdSeam(
            mockStatic(KmuLunaSettings.class, Mockito.CALLS_REAL_METHODS));

        geometrySettingsSeam = seams.holdSeam(
            mockStatic(KmuOwnerMapGeometrySettings.class, Mockito.CALLS_REAL_METHODS));

        nameFormatMock = mock(NameFormatPreference.class);

        announceSettingsRevision(settingsRevision);
        pickNameFormat(OwnerNameFormatChoice.FULL);

        var preferences = OwnerMapBodyPreferencesFixtures.createUnderTestKeys();

        machinery = new SectorMapMachinery(mock(SectorAPI.class));
        viewFake = new OwnerPaintedViewFake(new OwnerSourceFake());
        ledger = new OwnerMapBuildLedger(
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

        // Every baseline starts at its never-built state, and nothing has been cut, so the first
        // frame after a sector is installed on builds both halves from scratch.
        @Test
        void owesTheCutAndTheContentOnTheFirstFrame() {

            // ARRANGE
            var expectedStaleness = Staleness.CUT_AND_CONTENT;

            // ACT
            var actualStaleness = decideWithNothingBuilt().staleness();

            // ASSERT
            assertThat(actualStaleness)
                .isEqualTo(expectedStaleness);
        }

        // The frame the map spends nearly all of its life on: a rebuild has been recorded and no
        // input moved since, so the decision stops here and opens no reading.
        @Test
        void owesNothingOnceTheRebuildIsRecordedAndNothingMoved() {

            // ARRANGE
            var expectedStaleness = Staleness.NOTHING;

            decideAndRecordARebuild();

            // ACT
            var actualStaleness = decideAfterABuild().staleness();

            // ASSERT
            assertThat(actualStaleness)
                .isEqualTo(expectedStaleness);
        }

        // Nothing built forces the build even when every key matches its baseline, but the cells
        // were cut, so they stand.
        @Test
        void owesTheContentAloneWhileNothingIsBuilt() {

            // ARRANGE
            var expectedStaleness = Staleness.CONTENT;

            decideAndRecordARebuild();

            // ACT
            var actualStaleness = decideWithNothingBuilt().staleness();

            // ASSERT
            assertThat(actualStaleness)
                .isEqualTo(expectedStaleness);
        }

        // The reachable set moved, so the cells it seeds are cut again and the clusters over them
        // rebuilt.
        @Test
        void owesTheCutAndTheContentWhenTheGeometrySignalIsRaised() {

            // ARRANGE
            var expectedStaleness = Staleness.CUT_AND_CONTENT;

            decideAndRecordARebuild();
            raiseTheGeometrySignal();

            // ACT
            var actualStaleness = decideAfterABuild().staleness();

            // ASSERT
            assertThat(actualStaleness)
                .isEqualTo(expectedStaleness);
        }

        // The cell reach reseeds every cell without the reachable set moving, which is why the cut
        // is asked of what it is cut from rather than of the signal.
        @Test
        void owesTheCutWhenACellSettingMovesWithoutTheGeometrySignal() {

            // ARRANGE
            var expectedStaleness = Staleness.CUT_AND_CONTENT;

            decideAndRecordARebuild();
            moveTheCellRadius();

            // ACT
            var actualStaleness = decideAfterABuild().staleness();

            // ASSERT
            assertThat(actualStaleness)
                .isEqualTo(expectedStaleness);
        }

        // Every cell setting is a LunaLib field, so an unmoved revision means an unmoved setting,
        // and the per-frame decision reads them once rather than every frame.
        @Test
        void keepsTheCellSettingsItReadWhileTheSettingsRevisionHolds() {

            // ARRANGE
            decideAndRecordARebuild();

            // ACT
            decideAfterABuild();
            decideAfterABuild();

            // ASSERT
            geometrySettingsSeam.verify(
                KmuOwnerMapGeometrySettings::getOwnerMapCellRadius,
                times(1));
        }

        // A settings change restyles every cell over the fixed geometry, so it owes a rebuild; the
        // cut reads the same values again and so owes none.
        @Test
        void owesTheContentAloneWhenOnlyTheSettingsRevisionMoves() {

            // ARRANGE
            var expectedStaleness = Staleness.CONTENT;

            decideAndRecordARebuild();
            moveTheSettingsRevision();

            // ACT
            var actualStaleness = decideAfterABuild().staleness();

            // ASSERT
            assertThat(actualStaleness)
                .isEqualTo(expectedStaleness);
        }

        // The view's revisions stand for whatever it samples, so a change to one rebuilds without
        // any setting moving.
        @Test
        void owesTheContentWhenTheViewsLiveInputsMove() {

            // ARRANGE
            var expectedStaleness = Staleness.CONTENT;

            decideAndRecordARebuild();
            viewFake.setContentRevisions(RevisionStamp.of(1));

            // ACT
            var actualStaleness = decideAfterABuild().staleness();

            // ASSERT
            assertThat(actualStaleness)
                .isEqualTo(expectedStaleness);
        }

        // What the map was built from is compared whole, the view's revisions included, so two
        // readings that differ rebuild however their values fold. These two fold to one int under
        // the Objects.hash polynomial.
        @Test
        void owesTheContentWhenTheViewsRevisionsDifferButFoldToOneInt() {

            // ARRANGE
            var expectedStaleness = Staleness.CONTENT;

            viewFake.setContentRevisions(FOLD_COLLIDING_VIEW_REVISIONS);
            decideAndRecordARebuild();
            viewFake.setContentRevisions(OTHER_FOLD_COLLIDING_VIEW_REVISIONS);

            // ACT
            var actualStaleness = decideAfterABuild().staleness();

            // ASSERT
            assertThat(actualStaleness)
                .isEqualTo(expectedStaleness);
        }

        // A pick is a different map but the same cells: the content key holds the sampled reading
        // and moves, while nothing the cells are cut from has.
        @Test
        void owesTheContentAloneWhenTheSpotlitOwnerMoves() {

            // ARRANGE
            var expectedStaleness = Staleness.CONTENT;

            decideAndRecordARebuild();
            spotlight(HEGEMONY_ID);

            // ACT
            var actualStaleness = decideAfterABuild().staleness();

            // ASSERT
            assertThat(actualStaleness)
                .isEqualTo(expectedStaleness);
        }

        // The spotlight decides who holds a cell, so the owners key holds it.
        @Test
        void movesTheOwnersKeyWhenTheSpotlitOwnerMoves() {

            // ARRANGE
            var rebuilt = decideAndRecordARebuild();

            spotlight(HEGEMONY_ID);

            // ACT
            var actualOwnersKey = decideAfterABuild().ownersKey();

            // ASSERT
            assertThat(actualOwnersKey)
                .isNotEqualTo(rebuilt.ownersKey());
        }

        // The name format reaches the paint and nothing before it, so the rebuild it owes is one
        // that may keep the owners it already read.
        @Test
        void owesTheContentButKeepsTheOwnersKeyWhenAStylePickMoves() {

            // ARRANGE
            var expectedStaleness = Staleness.CONTENT;

            var rebuilt = decideAndRecordARebuild();

            pickNameFormat(OwnerNameFormatChoice.SHORT);

            // ACT
            var actualHalves = decideAfterABuild();

            // ASSERT
            assertThat(actualHalves.staleness())
                .isEqualTo(expectedStaleness);
            assertThat(actualHalves.ownersKey())
                .isEqualTo(rebuilt.ownersKey());
        }

        // The ledger's entries advance only as the cache reports each stage complete, which is what lets a
        // rebuild that threw part way be asked for again rather than taken as done.
        @Test
        void goesOnOwingTheCutAndTheContentUntilAStageIsRecorded() {

            // ARRANGE
            var expectedStaleness = Staleness.CUT_AND_CONTENT;

            decideWithNothingBuilt();

            // ACT
            var actualStaleness = decideAfterABuild().staleness();

            // ASSERT
            assertThat(actualStaleness)
                .isEqualTo(expectedStaleness);
        }
    }

    @Nested
    class CanReuseStandingOwners {

        // The owners were resolved under the key this frame reads again, so they still say who owns
        // what.
        @Test
        void isTrueWhenNothingReachingTheResolveMoved() {

            // ARRANGE
            decideAndRecordARebuild();

            // ACT
            var actualCanReuse = ledger.canReuseStandingOwners(decideAfterABuild(), true, Set.of());

            // ASSERT
            assertThat(actualCanReuse)
                .isTrue();
        }

        // The case the owners key exists for: a rebuild is owed, and the owners it would read
        // afresh are the ones already standing.
        @Test
        void isTrueWhenOnlyAStylePickMoved() {

            // ARRANGE
            decideAndRecordARebuild();
            pickNameFormat(OwnerNameFormatChoice.SHORT);

            // ACT
            var actualCanReuse = ledger.canReuseStandingOwners(decideAfterABuild(), true, Set.of());

            // ASSERT
            assertThat(actualCanReuse)
                .isTrue();
        }

        // A cache's first rebuild has nothing to keep, whatever the keys say.
        @Test
        void isFalseWhileNothingIsStanding() {

            // ARRANGE
            decideAndRecordARebuild();

            // ACT
            var actualCanReuse = ledger.canReuseStandingOwners(decideAfterABuild(), false, Set.of());

            // ASSERT
            assertThat(actualCanReuse)
                .isFalse();
        }

        // A marked system is one the sector moved under, so owners read before it moved no longer
        // say who owns it.
        @Test
        void isFalseWhenASystemIsMarked() {

            // ARRANGE
            decideAndRecordARebuild();

            // ACT
            var actualCanReuse = ledger.canReuseStandingOwners(
                decideAfterABuild(),
                true,
                Set.of(buildCellKey("marked")));

            // ASSERT
            assertThat(actualCanReuse)
                .isFalse();
        }

        // A recut admits or drops systems, so the owners have to be read over the new set.
        @Test
        void isFalseWhenTheCellsAreRecut() {

            // ARRANGE
            decideAndRecordARebuild();
            raiseTheGeometrySignal();

            // ACT
            var actualCanReuse = ledger.canReuseStandingOwners(decideAfterABuild(), true, Set.of());

            // ASSERT
            assertThat(actualCanReuse)
                .isFalse();
        }

        // The spotlight decides who holds a cell rather than only how it is coloured, so the owners
        // key holds it and a moved pick reads the owners afresh.
        @Test
        void isFalseWhenAPickReachingTheResolveMoved() {

            // ARRANGE
            decideAndRecordARebuild();
            spotlight(HEGEMONY_ID);

            // ACT
            var actualCanReuse = ledger.canReuseStandingOwners(decideAfterABuild(), true, Set.of());

            // ASSERT
            assertThat(actualCanReuse)
                .isFalse();
        }
    }

    @Nested
    class DescribeCellCutTransition {

        // The never-cut state is a reading of its own rather than a zero one, so the first recut
        // says so instead of naming a set of inputs no player was ever under.
        @Test
        void namesTheNeverCutStateBeforeAnythingIsCut() {

            // ARRANGE
            var expectedPrefix = "from null to ";

            // ACT
            var actualTransition = ledger.describeCellCutTransition(decideWithNothingBuilt());

            // ASSERT
            assertThat(actualTransition)
                .startsWith(expectedPrefix);
        }

        // The other half of recording a cut: the reading a later recut moves away from is the one
        // that was actually cut, so a transition described after it names no absence.
        @Test
        void namesTheStandingReadingOnceACutIsRecorded() {

            // ARRANGE
            decideAndRecordARebuild();

            // ACT
            var actualTransition = ledger.describeCellCutTransition(decideAfterABuild());

            // ASSERT
            assertThat(actualTransition)
                .doesNotContain("null");
        }
    }

    @Nested
    class RecordCellsCut {

        // The cut and the clusters advance apart, so a rebuild that threw after the cut recuts
        // nothing on its retry.
        @Test
        void clearsTheCutAloneLeavingTheContentOwed() {

            // ARRANGE
            var expectedStaleness = Staleness.CONTENT;

            // ACT
            ledger.recordCellsCut(decideWithNothingBuilt());

            var actualStaleness = decideAfterABuild().staleness();

            // ASSERT
            assertThat(actualStaleness)
                .isEqualTo(expectedStaleness);
        }
    }

    @Nested
    class RecordContentRebuilt {

        // The clusters and the owners advance apart: a rebuild that painted over owners it never
        // recorded resolving has nothing a later rebuild may keep.
        @Test
        void clearsTheRebuildWithoutLettingTheOwnersBeKept() {

            // ARRANGE
            var expectedStaleness = Staleness.NOTHING;

            var staleHalves = decideWithNothingBuilt();

            ledger.recordCellsCut(staleHalves);

            // ACT
            ledger.recordContentRebuilt(staleHalves);

            var nextHalves = decideAfterABuild();

            // ASSERT
            assertThat(nextHalves.staleness())
                .isEqualTo(expectedStaleness);
            assertThat(ledger.canReuseStandingOwners(nextHalves, true, Set.of()))
                .isFalse();
        }

        // The owed rebuild is the one that read the dropped systems, so once it is recorded the map
        // is current again.
        @Test
        void paysAnOwedRebuild() {

            // ARRANGE
            var expectedStaleness = Staleness.NOTHING;

            decideAndRecordARebuild();
            ledger.recordRebuildOwed();

            // ACT
            ledger.recordContentRebuilt(decideAfterABuild());

            var actualStaleness = decideAfterABuild().staleness();

            // ASSERT
            assertThat(actualStaleness)
                .isEqualTo(expectedStaleness);
        }
    }

    @Nested
    class RecordOwnersResolved {

        // A rebuild that resolved the owners and then threw leaves the owners good to keep, so the
        // retry paints over it rather than reading the sector again.
        @Test
        void letsTheOwnersBeKeptWhileTheRebuildIsStillOwed() {

            // ARRANGE
            var expectedStaleness = Staleness.CONTENT;

            var staleHalves = decideWithNothingBuilt();

            ledger.recordCellsCut(staleHalves);

            // ACT
            ledger.recordOwnersResolved(staleHalves);

            var nextHalves = decideAfterABuild();

            // ASSERT
            assertThat(nextHalves.staleness())
                .isEqualTo(expectedStaleness);
            assertThat(ledger.canReuseStandingOwners(nextHalves, true, Set.of()))
                .isTrue();
        }
    }

    @Nested
    class RecordRebuildOwed {

        // A dropped batch is the only record that an owner changed, and no revision moved for it,
        // so the owed rebuild is what the next decision turns on. The cells stand: an owner change
        // seeds no cell.
        @Test
        void owesTheContentAloneWithNothingElseMoved() {

            // ARRANGE
            var expectedStaleness = Staleness.CONTENT;

            decideAndRecordARebuild();

            // ACT
            ledger.recordRebuildOwed();

            var actualStaleness = decideAfterABuild().staleness();

            // ASSERT
            assertThat(actualStaleness)
                .isEqualTo(expectedStaleness);
        }

        // A rebuild that threw before reporting itself leaves the change unread, so it is asked for
        // again.
        @Test
        void goesOnOwingTheRebuildUntilOneIsRecorded() {

            // ARRANGE
            var expectedStaleness = Staleness.CONTENT;

            decideAndRecordARebuild();
            ledger.recordRebuildOwed();
            decideAfterABuild();

            // ACT
            var actualStaleness = decideAfterABuild().staleness();

            // ASSERT
            assertThat(actualStaleness)
                .isEqualTo(expectedStaleness);
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

        announceSettingsRevision(settingsRevision);
    }

    // Stands in for the settings revision LunaLib's listener keeps, so no case reads the shared
    // static the rest of the test JVM may have moved.
    private void announceSettingsRevision(int announcedRevision) {

        lunaSettingsSeam
            .when(KmuLunaSettings::getSettingsRevision)
            .thenReturn(announcedRevision);
    }

    // Picks a name format on the sidebar, a style pick that reaches the paint alone.
    private void pickNameFormat(OwnerNameFormatChoice nameFormat) {

        when(nameFormatMock.getSelectedNameFormat(any()))
            .thenReturn(nameFormat);
    }

    // Raises the reachable-set signal on the board this ledger reads.
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

        return ledger.decideWhatIsStale(viewFake, SCREEN, true);
    }

    // A later frame's decision, once something stands to compare against.
    private StaleHalves decideAfterABuild() {

        return ledger.decideWhatIsStale(viewFake, SCREEN, false);
    }

    // One whole rebuild as the cache reports it: the first decision taken, and every stage of the
    // rebuild it owes recorded complete under it.
    private StaleHalves decideAndRecordARebuild() {

        var staleHalves = decideWithNothingBuilt();

        ledger.recordCellsCut(staleHalves);
        ledger.recordOwnersResolved(staleHalves);
        ledger.recordContentRebuilt(staleHalves);

        return staleHalves;
    }
}
