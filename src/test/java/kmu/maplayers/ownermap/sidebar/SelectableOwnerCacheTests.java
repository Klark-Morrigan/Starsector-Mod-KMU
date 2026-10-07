package kmu.maplayers.ownermap.sidebar;

import com.fs.starfarer.api.campaign.SectorAPI;

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
 * revision moves it and nothing else does, so a live picker is served from the memo and a stale one
 * is re-walked. The memo mechanism itself is the framework's and is pinned there; what is pinned
 * here is the revision this layer feeds it, and that the presence behind the rows is served off that
 * same read. Pins its lifetime beside that - one memo per machinery, so two sectors' sidebars
 * neither read one another's rows nor evict one another's entry, and a disposal leaving nothing of
 * the gone sector's behind. The live settings revision reads zero in a test JVM (no LunaLib listener
 * runs), so the view's content revision is the only moving part of the key here.
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

    @Nested
    class ResolveOwnerCacheIn {

        @Test
        void answersOneCachePerMachinery() {
            // The body build asking for rows and the pass asking where an owner was found resolve
            // separately, so both asks under one sector must land on the same memo or the second
            // pays for a whole economy walk of its own.
            var machinery = new SectorMapMachinery(mock(SectorAPI.class));

            assertThat(SelectableOwnerCache.resolveOwnerCacheIn(machinery, LAYER_ID))
                .isSameAs(SelectableOwnerCache.resolveOwnerCacheIn(machinery, LAYER_ID));
        }

        @Test
        void answersEachLayerAMemoOfItsOwn() {
            // The memo holds one entry, so two layers listing their pickers on one sector would evict
            // each other on every frame both are drawn and re-walk the economy each time.
            var machinery = new SectorMapMachinery(mock(SectorAPI.class));

            assertThat(SelectableOwnerCache.resolveOwnerCacheIn(machinery, LAYER_ID))
                .isNotSameAs(SelectableOwnerCache.resolveOwnerCacheIn(machinery, OTHER_LAYER_ID));
        }

        @Test
        void answersAFreshCacheAfterTheMachineryIsDisposed() {
            // A load disposes the machinery, which is what stands in for a discard of this memo's
            // own - the sector after it walks its own economy rather than reading the previous
            // sector's rows out of a revision that never moved.
            var machinery = new SectorMapMachinery(mock(SectorAPI.class));
            var cache = SelectableOwnerCache.resolveOwnerCacheIn(machinery, LAYER_ID);

            machinery.disposeMachinery();

            assertThat(SelectableOwnerCache.resolveOwnerCacheIn(machinery, LAYER_ID))
                .isNotSameAs(cache);
        }
    }

    @Nested
    class ResolveOwnerPickerRead {

        @Test
        void walksTheViewOnceWhileItsRevisionHolds() {
            // Nothing the picker depends on moved between the two calls, so the revision this layer
            // computes must come out the same and the second call read the memo.
            var sectorMock = mock(SectorAPI.class);
            var viewMock = stubViewAnswering(sectorMock, "owners");
            var cache = buildCacheOver(sectorMock);

            var first = cache.resolveOwnerPickerRead(viewMock);
            var second = cache.resolveOwnerPickerRead(viewMock);

            assertThat(first)
                .isEqualTo(READ);
            assertThat(second)
                .isEqualTo(READ);

            verify(viewMock, times(1))
                .resolveOwnerPickerRead(sectorMock);
        }

        @Test
        void recomputesWhenTheViewsContentRevisionMoves() {

            var sectorMock = mock(SectorAPI.class);
            var viewMock = stubViewAnswering(sectorMock, "groups");
            var cache = buildCacheOver(sectorMock);

            // A membership change bumps the grouped view's content revision between the two calls,
            // so the memo is stale and must re-resolve. The one case that states a revision at all.
            when(viewMock.getContentRevision(any()))
                .thenReturn(0, 1);

            cache.resolveOwnerPickerRead(viewMock);
            cache.resolveOwnerPickerRead(viewMock);

            verify(viewMock, times(2))
                .resolveOwnerPickerRead(sectorMock);
        }

        @Test
        void keysTheMemoOnItsOwnMachineryBoard() {
            // The key is the view's fold of a board, and the board it folds has to be this
            // machinery's: keyed on the running sector's instead, a list would never be re-walked
            // when its own sector's groups moved, and would be thrown away when another sector's
            // did. Every other case here stubs the fold against any board, so this is the one that
            // reads the argument back.
            var sectorMock = mock(SectorAPI.class);
            var viewMock = stubViewAnswering(sectorMock, "owners");
            var machinery = new SectorMapMachinery(sectorMock);

            new SelectableOwnerCache(machinery).resolveOwnerPickerRead(viewMock);

            verify(viewMock)
                .getContentRevision(machinery.resolveRefreshBoard());
        }

        @Test
        void servesEachMachineryItsOwnSectorsRows() {
            // The wrongness a shared memo produces is a wrong list, not a stale one: the revision is
            // the view's, so one sector's rows would answer under the other's ask with nothing about
            // the key saying they came from elsewhere.
            var sectorMock = mock(SectorAPI.class);
            var otherSectorMock = mock(SectorAPI.class);
            var viewMock = stubViewAnsweringPerSector(sectorMock, otherSectorMock);

            assertThat(buildCacheOver(sectorMock).resolveOwnerPickerRead(viewMock))
                .isEqualTo(READ);
            assertThat(buildCacheOver(otherSectorMock).resolveOwnerPickerRead(viewMock))
                .isEqualTo(OTHER_READ);
        }

        @Test
        void keepsOneMachineryListWhileTheOtherResolvesItsOwn() {
            // One memo between two sectors holds a single entry, so alternating asks evict each
            // other and re-walk the whole economy every call. A memo apiece is what leaves each
            // sector's list standing while the other resolves.
            var sectorMock = mock(SectorAPI.class);
            var otherSectorMock = mock(SectorAPI.class);
            var viewMock = stubViewAnsweringPerSector(sectorMock, otherSectorMock);
            var cache = buildCacheOver(sectorMock);
            var otherCache = buildCacheOver(otherSectorMock);

            cache.resolveOwnerPickerRead(viewMock);
            otherCache.resolveOwnerPickerRead(viewMock);
            cache.resolveOwnerPickerRead(viewMock);
            otherCache.resolveOwnerPickerRead(viewMock);

            verify(viewMock, times(1))
                .resolveOwnerPickerRead(sectorMock);
            verify(viewMock, times(1))
                .resolveOwnerPickerRead(otherSectorMock);
        }
    }

    @Nested
    class ReadPresentSystemIds {

        @Test
        void answersFromTheSameReadTheRowsCameFrom() {
            // The presence rides the picker's own memo, so a lookup answers the index the view built
            // beside its rows rather than a second walk's - which is what stops a lit set and a row
            // count from describing the sector at two different moments.
            var sectorMock = mock(SectorAPI.class);
            var viewMock = stubViewAnswering(sectorMock, "owners");

            assertThat(buildCacheOver(sectorMock).readPresentSystemKeys(viewMock, "hegemony"))
                .containsExactlyElementsOf(buildCellKeys("corvus", "askonia"));
        }

        @Test
        void walksTheViewOnceForTheRowsAndThePresenceTogether() {
            // The reason the two share a memo rather than a store apiece: asking for the rows and
            // then for an owner's systems is one resolve, so a hover costs no economy walk of its own.
            var sectorMock = mock(SectorAPI.class);
            var viewMock = stubViewAnswering(sectorMock, "owners");
            var cache = buildCacheOver(sectorMock);

            cache.resolveOwnerPickerRead(viewMock);
            cache.readPresentSystemKeys(viewMock, "hegemony");

            verify(viewMock, times(1))
                .resolveOwnerPickerRead(sectorMock);
        }

        @Test
        void answersEmptyForAnOwnerTheViewNeverSurfaced() {
            // A hover can outlive the row it started on (a rebuild between the report and the read),
            // so an unknown ID has to answer an empty set rather than a null the render side would
            // fall over on.
            var sectorMock = mock(SectorAPI.class);
            var viewMock = stubViewAnswering(sectorMock, "owners");

            assertThat(buildCacheOver(sectorMock).readPresentSystemKeys(viewMock, "vanished"))
                .isEmpty();
        }
    }

    @Nested
    class DisposeMachinery {

        @Test
        void dropsTheMemoisedRead() {
            // What a caller still holding a cache resolved before the disposal gets: a fresh walk,
            // rather than the rows the gone sector's economy last answered under a revision that has
            // no reason to have moved.
            var sectorMock = mock(SectorAPI.class);
            var viewMock = stubViewAnswering(sectorMock, "owners");
            var cache = buildCacheOver(sectorMock);

            cache.resolveOwnerPickerRead(viewMock);
            cache.disposeMachinery();
            cache.resolveOwnerPickerRead(viewMock);

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
    // before it can ask the cache anything. Its content revision is left unstubbed and so holds
    // constant, which is what every case but the staleness one wants; that case states its own
    // moving pair.
    //
    // The read is stubbed through doReturn because the seam answers a wildcarded read: a when() stub
    // would have to name the captured item type, which is exactly what the wildcard hides.
    private static OwnerPaintedView stubViewAnswering(SectorAPI sectorMock, String viewId) {

        var viewMock = mock(OwnerPaintedView.class);

        when(viewMock.getId())
            .thenReturn(viewId);

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
