package kmu.maplayers.ownermap.sidebar;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.cache.revisions.RevisionStamp;
import kmlib.starsector.systems.SystemKey;
import kmlib.starsector.ui.widgets.lists.ListPicker;

import kmu.maplayers.base.machinery.SectorMapMachinery;
import kmu.maplayers.ownermap.OwnerPaintedView;
import kmu.maplayers.ownermap.picker.OwnerMetricsFake;
import kmu.maplayers.ownermap.picker.OwnerPickerRead;
import kmu.maplayers.ownermap.picker.OwnerSortModesFake;
import kmu.maplayers.ownermap.picker.RankedOwner;
import kmu.maplayers.ownermap.picker.SelectableOwner;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKeys;
import static kmu.maplayers.ownermap.picker.OwnerPresenceIndexFixtures.buildIndexOf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins what an owner map counts as a reason to rebuild its picker: the view's own content
 * revisions and a system marked on its own board move it and nothing else does, so a live picker is
 * served from the memo and a stale one
 * is re-walked. The memo mechanism itself is the framework's and is pinned there; what is pinned
 * here is the revisions this layer feeds it, and that the presence behind the rows is served off that
 * same read. Pins its lifetime beside that - one memo per machinery, so two sectors' sidebars
 * neither read one another's rows nor evict one another's entry, and a disposal leaving nothing of
 * the gone sector's behind. The live settings revision reads zero in a test JVM (no LunaLib listener
 * runs), so the view's content revisions and the board's marks are the moving parts of the key here.
 */
final class SelectableOwnerCacheTests {

    // The read the stubbed view returns; the memo forwards it verbatim, so its contents only stand
    // in - what a view actually offers, and where it actually found an owner, are pinned on that view.
    private static final OwnerPickerRead<RankedOwner<OwnerMetricsFake>> READ =
        new OwnerPickerRead<>(
            new ListPicker<>(
                List.of(new RankedOwner<>(
                    new SelectableOwner("hegemony", "Hegemony", null),
                    OwnerMetricsFake.EMPTY_FAKE)),
                OwnerSortModesFake.MODES),
            buildIndexOf("hegemony", "corvus", "askonia"));

    // The system an owner change marks between two asks.
    private static final SystemKey MARKED_SYSTEM = buildCellKey("marked");

    // The layers a memo is resolved for, two of them for the case about their keeping apart.
    private static final String LAYER_ID = "test_layer";
    private static final String OTHER_LAYER_ID = "other_layer";

    // A second sector's read, so a cache answering the wrong sector's walk is visible as the wrong
    // rows rather than only as an extra walk.
    private static final OwnerPickerRead<RankedOwner<OwnerMetricsFake>> OTHER_READ =
        new OwnerPickerRead<>(
            new ListPicker<>(
                List.of(new RankedOwner<>(
                    new SelectableOwner("tritachyon", "Tri-Tachyon", null),
                    OwnerMetricsFake.EMPTY_FAKE)),
                OwnerSortModesFake.MODES),
            buildIndexOf("tritachyon", "eos"));

    // Two readings of a view's revisions that fold to one int under the Objects.hash polynomial:
    // 31 * (31 + 0) + 31 and 31 * (31 + 1) + 0 are both 992.
    private static final RevisionStamp FOLD_COLLIDING_VIEW_REVISIONS = RevisionStamp.of(0, 31);
    private static final RevisionStamp OTHER_FOLD_COLLIDING_VIEW_REVISIONS = RevisionStamp.of(1, 0);

    @Nested
    class ResolveOwnerCacheIn {

        // The body build asking for rows and the pass asking where an owner was found resolve
        // separately, so both asks under one sector must land on the same memo or the second pays
        // for a whole economy walk of its own.
        @Test
        void answersOneCachePerMachinery() {

            // ARRANGE
            var machinery = new SectorMapMachinery(mock(SectorAPI.class));

            // ACT
            var actualFirst = SelectableOwnerCache.resolveOwnerCacheIn(machinery, LAYER_ID);
            var actualSecond = SelectableOwnerCache.resolveOwnerCacheIn(machinery, LAYER_ID);

            // ASSERT
            assertThat(actualFirst)
                .isSameAs(actualSecond);
        }

        // The memo holds one entry, so two layers listing their pickers on one sector would evict
        // each other on every frame both are drawn and re-walk the economy each time.
        @Test
        void answersEachLayerAMemoOfItsOwn() {

            // ARRANGE
            var machinery = new SectorMapMachinery(mock(SectorAPI.class));

            // ACT
            var actualCache = SelectableOwnerCache.resolveOwnerCacheIn(machinery, LAYER_ID);
            var actualOtherCache = SelectableOwnerCache.resolveOwnerCacheIn(machinery, OTHER_LAYER_ID);

            // ASSERT
            assertThat(actualCache)
                .isNotSameAs(actualOtherCache);
        }

        // A load disposes the machinery, which is what stands in for a discard of this memo's own -
        // the sector after it walks its own economy rather than reading the previous sector's rows
        // out of a revision that never moved.
        @Test
        void answersAFreshCacheAfterTheMachineryIsDisposed() {

            // ARRANGE
            var machinery = new SectorMapMachinery(mock(SectorAPI.class));
            var disposedCache = SelectableOwnerCache.resolveOwnerCacheIn(machinery, LAYER_ID);

            machinery.disposeMachinery();

            // ACT
            var actualCache = SelectableOwnerCache.resolveOwnerCacheIn(machinery, LAYER_ID);

            // ASSERT
            assertThat(actualCache)
                .isNotSameAs(disposedCache);
        }
    }

    @Nested
    class ResolveOwnerPickerRead {

        // Nothing the picker depends on moved between the two calls, so the revisions this layer
        // reads must come out the same and the second call read the memo.
        @Test
        void walksTheViewOnceWhileItsRevisionsHold() {

            // ARRANGE
            var sectorMock = mock(SectorAPI.class);
            var viewMock = stubViewAnswering(sectorMock, "owners");
            var cache = buildCacheOver(sectorMock);

            // ACT
            var actualFirst = cache.resolveOwnerPickerRead(viewMock);
            var actualSecond = cache.resolveOwnerPickerRead(viewMock);

            // ASSERT
            assertThat(actualFirst)
                .isEqualTo(READ);
            assertThat(actualSecond)
                .isEqualTo(READ);

            verify(viewMock, times(1))
                .resolveOwnerPickerRead(sectorMock);
        }

        // A membership change moves the grouped view's content revisions between the two calls, so
        // the memo is stale and must re-resolve.
        @Test
        void recomputesWhenTheViewsContentRevisionsMove() {

            // ARRANGE
            var sectorMock = mock(SectorAPI.class);
            var viewMock = stubViewAnswering(sectorMock, "groups");
            var cache = buildCacheOver(sectorMock);

            when(viewMock.readContentRevisions(any()))
                .thenReturn(RevisionStamp.of(0), RevisionStamp.of(1));

            // ACT
            cache.resolveOwnerPickerRead(viewMock);
            cache.resolveOwnerPickerRead(viewMock);

            // ASSERT
            verify(viewMock, times(2))
                .resolveOwnerPickerRead(sectorMock);
        }

        // The memo compares the view's revisions whole, so two readings that differ re-walk the list
        // however their values fold. These two fold to one int under the Objects.hash polynomial.
        @Test
        void recomputesWhenTheViewsRevisionsDifferButFoldToOneInt() {

            // ARRANGE
            var sectorMock = mock(SectorAPI.class);
            var viewMock = stubViewAnswering(sectorMock, "groups");
            var cache = buildCacheOver(sectorMock);

            when(viewMock.readContentRevisions(any()))
                .thenReturn(FOLD_COLLIDING_VIEW_REVISIONS, OTHER_FOLD_COLLIDING_VIEW_REVISIONS);

            // ACT
            cache.resolveOwnerPickerRead(viewMock);
            cache.resolveOwnerPickerRead(viewMock);

            // ASSERT
            verify(viewMock, times(2))
                .resolveOwnerPickerRead(sectorMock);
        }

        // A system changing hands is marked and re-shapes the map, so the list is re-walked beside
        // it rather than lagging until something coarser moves.
        @Test
        void recomputesWhenASystemIsMarkedOnItsOwnBoard() {

            // ARRANGE
            var sectorMock = mock(SectorAPI.class);
            var viewMock = stubViewAnswering(sectorMock, "owners");
            var machinery = new SectorMapMachinery(sectorMock);
            var cache = new SelectableOwnerCache(machinery);

            // ACT
            cache.resolveOwnerPickerRead(viewMock);
            machinery.resolveRefreshBoard().markSystemGroupingStale(MARKED_SYSTEM);
            cache.resolveOwnerPickerRead(viewMock);

            // ASSERT
            verify(viewMock, times(2))
                .resolveOwnerPickerRead(sectorMock);
        }

        // Another sector's change says nothing about this sector's owners.
        @Test
        void walksTheViewOnceWhenASystemIsMarkedOnAnotherBoard() {

            // ARRANGE
            var sectorMock = mock(SectorAPI.class);
            var viewMock = stubViewAnswering(sectorMock, "owners");
            var otherMachinery = new SectorMapMachinery(mock(SectorAPI.class));
            var cache = buildCacheOver(sectorMock);

            // ACT
            cache.resolveOwnerPickerRead(viewMock);
            otherMachinery.resolveRefreshBoard().markSystemGroupingStale(MARKED_SYSTEM);
            cache.resolveOwnerPickerRead(viewMock);

            // ASSERT
            verify(viewMock, times(1))
                .resolveOwnerPickerRead(sectorMock);
        }

        // The key holds the view's revisions off a board, and that board has to be this machinery's:
        // keyed on the running sector's instead, a list would never be re-walked when its own
        // sector's groups moved, and would be thrown away when another sector's did. Every other
        // case here stubs the revisions against any board, so this is the one that reads the
        // argument back.
        @Test
        void keysTheMemoOnItsOwnMachineryBoard() {

            // ARRANGE
            var sectorMock = mock(SectorAPI.class);
            var viewMock = stubViewAnswering(sectorMock, "owners");
            var machinery = new SectorMapMachinery(sectorMock);

            // ACT
            new SelectableOwnerCache(machinery).resolveOwnerPickerRead(viewMock);

            // ASSERT
            verify(viewMock)
                .readContentRevisions(machinery.resolveRefreshBoard());
        }

        // The wrongness a shared memo produces is a wrong list, not a stale one: the revisions are
        // the view's, so one sector's rows would answer under the other's ask with nothing about the
        // key saying they came from elsewhere.
        @Test
        void servesEachMachineryItsOwnSectorsRows() {

            // ARRANGE
            var sectorMock = mock(SectorAPI.class);
            var otherSectorMock = mock(SectorAPI.class);
            var viewMock = stubViewAnsweringPerSector(sectorMock, otherSectorMock);

            // ACT
            var actualRead = buildCacheOver(sectorMock).resolveOwnerPickerRead(viewMock);
            var actualOtherRead = buildCacheOver(otherSectorMock).resolveOwnerPickerRead(viewMock);

            // ASSERT
            assertThat(actualRead)
                .isEqualTo(READ);
            assertThat(actualOtherRead)
                .isEqualTo(OTHER_READ);
        }

        // One memo between two sectors holds a single entry, so alternating asks evict each other
        // and re-walk the whole economy every call. A memo apiece is what leaves each sector's list
        // standing while the other resolves.
        @Test
        void keepsOneMachineryListWhileTheOtherResolvesItsOwn() {

            // ARRANGE
            var sectorMock = mock(SectorAPI.class);
            var otherSectorMock = mock(SectorAPI.class);
            var viewMock = stubViewAnsweringPerSector(sectorMock, otherSectorMock);
            var cache = buildCacheOver(sectorMock);
            var otherCache = buildCacheOver(otherSectorMock);

            // ACT
            cache.resolveOwnerPickerRead(viewMock);
            otherCache.resolveOwnerPickerRead(viewMock);
            cache.resolveOwnerPickerRead(viewMock);
            otherCache.resolveOwnerPickerRead(viewMock);

            // ASSERT
            verify(viewMock, times(1))
                .resolveOwnerPickerRead(sectorMock);
            verify(viewMock, times(1))
                .resolveOwnerPickerRead(otherSectorMock);
        }
    }

    @Nested
    class ReadPresentSystemIds {

        // The presence rides the picker's own memo, so a lookup answers the index the view built
        // beside its rows rather than a second walk's - which is what stops a lit set and a row count
        // from describing the sector at two different moments.
        @Test
        void answersFromTheSameReadTheRowsCameFrom() {

            // ARRANGE
            var sectorMock = mock(SectorAPI.class);
            var viewMock = stubViewAnswering(sectorMock, "owners");
            var expectedSystemKeys = buildCellKeys("corvus", "askonia");

            // ACT
            var actualSystemKeys = buildCacheOver(sectorMock).readPresentSystemKeys(viewMock, "hegemony");

            // ASSERT
            assertThat(actualSystemKeys)
                .containsExactlyElementsOf(expectedSystemKeys);
        }

        // The reason the two share a memo rather than a store apiece: asking for the rows and then
        // for an owner's systems is one resolve, so a hover costs no economy walk of its own.
        @Test
        void walksTheViewOnceForTheRowsAndThePresenceTogether() {

            // ARRANGE
            var sectorMock = mock(SectorAPI.class);
            var viewMock = stubViewAnswering(sectorMock, "owners");
            var cache = buildCacheOver(sectorMock);

            // ACT
            cache.resolveOwnerPickerRead(viewMock);
            cache.readPresentSystemKeys(viewMock, "hegemony");

            // ASSERT
            verify(viewMock, times(1))
                .resolveOwnerPickerRead(sectorMock);
        }

        // A hover can outlive the row it started on (a rebuild between the report and the read), so
        // an unknown ID has to answer an empty set rather than a null the render side would fall
        // over on.
        @Test
        void answersEmptyForAnOwnerTheViewNeverSurfaced() {

            // ARRANGE
            var sectorMock = mock(SectorAPI.class);
            var viewMock = stubViewAnswering(sectorMock, "owners");

            // ACT
            var actualSystemKeys = buildCacheOver(sectorMock).readPresentSystemKeys(viewMock, "vanished");

            // ASSERT
            assertThat(actualSystemKeys)
                .isEmpty();
        }
    }

    @Nested
    class DisposeMachinery {

        // What a caller still holding a cache resolved before the disposal gets: a fresh walk,
        // rather than the rows the gone sector's economy last answered under revisions that have no
        // reason to have moved.
        @Test
        void dropsTheMemoisedRead() {

            // ARRANGE
            var sectorMock = mock(SectorAPI.class);
            var viewMock = stubViewAnswering(sectorMock, "owners");
            var cache = buildCacheOver(sectorMock);

            cache.resolveOwnerPickerRead(viewMock);

            // ACT
            cache.disposeMachinery();
            cache.resolveOwnerPickerRead(viewMock);

            // ASSERT
            verify(viewMock, times(2))
                .resolveOwnerPickerRead(sectorMock);
        }
    }

    // A cache over one sector's machinery, which is the only way a cache exists - the machinery
    // is what supplies the sector every walk below is taken against.
    private static SelectableOwnerCache buildCacheOver(SectorAPI sectorMock) {

        return new SelectableOwnerCache(new SectorMapMachinery(sectorMock));
    }

    // A view answering READ for the stated sector under a stated ID, which every case needs standing
    // before it can ask the cache anything. Its content revisions are the empty stamp and so hold
    // still, which is what every case but the staleness ones wants; those cases state their own
    // moving pair.
    //
    // The read is stubbed through doReturn because the seam answers a wildcarded read: a when() stub
    // would have to name the captured item type, which is exactly what the wildcard hides.
    private static OwnerPaintedView stubViewAnswering(SectorAPI sectorMock, String viewId) {

        var viewMock = mock(OwnerPaintedView.class);

        when(viewMock.getId())
            .thenReturn(viewId);

        when(viewMock.readContentRevisions(any()))
            .thenReturn(RevisionStamp.EMPTY);

        doReturn(READ)
            .when(viewMock)
            .resolveOwnerPickerRead(sectorMock);

        return viewMock;
    }

    // The same view answering a second sector's walk with a read of its own, which is what lets a
    // two-sector case tell a cache reading the wrong sector's walk from one that merely walked twice.
    private static OwnerPaintedView stubViewAnsweringPerSector(
            SectorAPI sectorMock,
            SectorAPI otherSectorMock) {

        var viewMock = stubViewAnswering(sectorMock, "owners");

        doReturn(OTHER_READ)
            .when(viewMock)
            .resolveOwnerPickerRead(otherSectorMock);

        return viewMock;
    }
}
