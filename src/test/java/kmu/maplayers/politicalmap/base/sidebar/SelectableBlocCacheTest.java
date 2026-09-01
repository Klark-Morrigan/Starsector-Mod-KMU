package kmu.maplayers.politicalmap.base.sidebar;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.ui.widgets.lists.ListPicker;

import kmu.maplayers.base.installation.MapLayerInstallation;
import kmu.maplayers.politicalmap.base.BlocPickerRead;
import kmu.maplayers.politicalmap.base.DominanceSortMode;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.RankedBloc;
import kmu.maplayers.politicalmap.base.SelectableBloc;
import kmu.maplayers.politicalmap.base.politics.DominanceStats;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static kmu.maplayers.politicalmap.base.politics.BlocPresenceIndexFixtures.buildIndexOf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins what the political map counts as a reason to rebuild its picker: the view's own content
 * revision moves it and nothing else does, so a live picker is served from the memo and a stale one
 * is re-walked. The memo mechanism itself is the framework's and is pinned there; what is pinned
 * here is the revision this layer feeds it, and that the presence behind the rows is served off that
 * same read. Pins its lifetime beside that - one memo per installation, so two sectors' sidebars
 * neither read one another's rows nor evict one another's entry, and a disposal leaving nothing of
 * the gone sector's behind. The live settings revision reads zero in a test JVM (no LunaLib listener
 * runs), so the view's content revision is the only moving part of the key here.
 */
final class SelectableBlocCacheTest {

    // The read the stubbed view returns; the memo forwards it verbatim, so its contents only stand
    // in - what a view actually offers, and where it actually found a bloc, are pinned on that view.
    private static final BlocPickerRead<RankedBloc<DominanceStats>> READ =
        new BlocPickerRead<>(
            new ListPicker<>(
                List.of(new RankedBloc<>(
                    new SelectableBloc("hegemony", "Hegemony", null),
                    DominanceStats.EMPTY)),
                DominanceSortMode.MODES),
            buildIndexOf("hegemony", "corvus", "askonia"));

    // A second sector's read, so a cache answering the wrong sector's walk is visible as the wrong
    // rows rather than only as an extra walk.
    private static final BlocPickerRead<RankedBloc<DominanceStats>> OTHER_READ =
        new BlocPickerRead<>(
            new ListPicker<>(
                List.of(new RankedBloc<>(
                    new SelectableBloc("tritachyon", "Tri-Tachyon", null),
                    DominanceStats.EMPTY)),
                DominanceSortMode.MODES),
            buildIndexOf("tritachyon", "eos"));

    @Nested
    class ResolveBlocCacheIn {

        @Test
        void resolveBlocCacheInAnswersOneCachePerInstallation() {
            // The body build asking for rows and the pass asking where a bloc was found resolve
            // separately, so both asks under one sector must land on the same memo or the second
            // pays for a whole economy walk of its own.
            var installation = new MapLayerInstallation(mock(SectorAPI.class));

            assertThat(SelectableBlocCache.resolveBlocCacheIn(installation))
                .isSameAs(SelectableBlocCache.resolveBlocCacheIn(installation));
        }

        @Test
        void resolveBlocCacheInAnswersAFreshCacheAfterTheInstallationIsDisposed() {
            // A load disposes the installation, which is what stands in for a discard of this memo's
            // own - the sector after it walks its own economy rather than reading the previous
            // sector's rows out of a revision that never moved.
            var installation = new MapLayerInstallation(mock(SectorAPI.class));
            var cache = SelectableBlocCache.resolveBlocCacheIn(installation);

            installation.disposeMachinery();

            assertThat(SelectableBlocCache.resolveBlocCacheIn(installation))
                .isNotSameAs(cache);
        }
    }

    @Nested
    class ResolveBlocPickerRead {

        @Test
        void resolveBlocPickerReadWalksTheViewOnceWhileItsRevisionHolds() {
            // Nothing the picker depends on moved between the two calls, so the revision this layer
            // computes must come out the same and the second call read the memo.
            var sectorMock = mock(SectorAPI.class);
            var viewMock = stubViewAnswering(sectorMock, "factions");
            var cache = buildCacheOver(sectorMock);

            var first = cache.resolveBlocPickerRead(viewMock);
            var second = cache.resolveBlocPickerRead(viewMock);

            assertThat(first)
                .isEqualTo(READ);
            assertThat(second)
                .isEqualTo(READ);

            verify(viewMock, times(1))
                .resolveBlocPickerRead(sectorMock);
        }

        @Test
        void resolveBlocPickerReadRecomputesWhenTheViewsContentRevisionMoves() {
            var sectorMock = mock(SectorAPI.class);
            var viewMock = stubViewAnswering(sectorMock, "alliances");
            var cache = buildCacheOver(sectorMock);

            // A membership change bumps the alliances view's content revision between the two calls,
            // so the memo is stale and must re-resolve. The one case that states a revision at all.
            when(viewMock.getContentRevision(any()))
                .thenReturn(0, 1);

            cache.resolveBlocPickerRead(viewMock);
            cache.resolveBlocPickerRead(viewMock);

            verify(viewMock, times(2))
                .resolveBlocPickerRead(sectorMock);
        }

        @Test
        void resolveBlocPickerReadKeysTheMemoOnItsOwnInstallationsBoard() {
            // The key is the view's fold of a board, and the board it folds has to be this
            // installation's: keyed on the running sector's instead, a list would never be re-walked
            // when its own sector's alliances moved, and would be thrown away when another sector's
            // did. Every other case here stubs the fold against any board, so this is the one that
            // reads the argument back.
            var sectorMock = mock(SectorAPI.class);
            var viewMock = stubViewAnswering(sectorMock, "factions");
            var installation = new MapLayerInstallation(sectorMock);

            new SelectableBlocCache(installation).resolveBlocPickerRead(viewMock);

            verify(viewMock)
                .getContentRevision(installation.resolveRefreshBoard());
        }

        @Test
        void resolveBlocPickerReadServesEachInstallationItsOwnSectorsRows() {
            // The wrongness a shared memo produces is a wrong list, not a stale one: the revision is
            // the view's, so one sector's rows would answer under the other's ask with nothing about
            // the key saying they came from elsewhere.
            var sectorMock = mock(SectorAPI.class);
            var otherSectorMock = mock(SectorAPI.class);
            var viewMock = stubViewAnsweringPerSector(sectorMock, otherSectorMock);

            assertThat(buildCacheOver(sectorMock).resolveBlocPickerRead(viewMock))
                .isEqualTo(READ);
            assertThat(buildCacheOver(otherSectorMock).resolveBlocPickerRead(viewMock))
                .isEqualTo(OTHER_READ);
        }

        @Test
        void resolveBlocPickerReadKeepsOneInstallationsListWhileTheOtherResolvesItsOwn() {
            // One memo between two sectors holds a single entry, so alternating asks evict each
            // other and re-walk the whole economy every call. A memo apiece is what leaves each
            // sector's list standing while the other resolves.
            var sectorMock = mock(SectorAPI.class);
            var otherSectorMock = mock(SectorAPI.class);
            var viewMock = stubViewAnsweringPerSector(sectorMock, otherSectorMock);
            var cache = buildCacheOver(sectorMock);
            var otherCache = buildCacheOver(otherSectorMock);

            cache.resolveBlocPickerRead(viewMock);
            otherCache.resolveBlocPickerRead(viewMock);
            cache.resolveBlocPickerRead(viewMock);
            otherCache.resolveBlocPickerRead(viewMock);

            verify(viewMock, times(1))
                .resolveBlocPickerRead(sectorMock);
            verify(viewMock, times(1))
                .resolveBlocPickerRead(otherSectorMock);
        }
    }

    @Nested
    class ReadPresentSystemIds {

        @Test
        void readPresentSystemIdsAnswersFromTheSameReadTheRowsCameFrom() {
            // The presence rides the picker's own memo, so a lookup answers the index the view built
            // beside its rows rather than a second walk's - which is what stops a lit set and a row
            // count from describing the sector at two different moments.
            var sectorMock = mock(SectorAPI.class);
            var viewMock = stubViewAnswering(sectorMock, "factions");

            assertThat(buildCacheOver(sectorMock).readPresentSystemIds(viewMock, "hegemony"))
                .containsExactly("corvus", "askonia");
        }

        @Test
        void readPresentSystemIdsWalksTheViewOnceForTheRowsAndThePresenceTogether() {
            // The reason the two share a memo rather than a store apiece: asking for the rows and
            // then for a bloc's systems is one resolve, so a hover costs no economy walk of its own.
            var sectorMock = mock(SectorAPI.class);
            var viewMock = stubViewAnswering(sectorMock, "factions");
            var cache = buildCacheOver(sectorMock);

            cache.resolveBlocPickerRead(viewMock);
            cache.readPresentSystemIds(viewMock, "hegemony");

            verify(viewMock, times(1))
                .resolveBlocPickerRead(sectorMock);
        }

        @Test
        void readPresentSystemIdsAnswersEmptyForABlocTheViewNeverSurfaced() {
            // A hover can outlive the row it started on (a rebuild between the report and the read),
            // so an unknown id has to answer an empty set rather than a null the render side would
            // fall over on.
            var sectorMock = mock(SectorAPI.class);
            var viewMock = stubViewAnswering(sectorMock, "factions");

            assertThat(buildCacheOver(sectorMock).readPresentSystemIds(viewMock, "vanished"))
                .isEmpty();
        }
    }

    @Nested
    class DisposeMachinery {

        @Test
        void disposeMachineryDropsTheMemoisedRead() {
            // What a caller still holding a cache resolved before the disposal gets: a fresh walk,
            // rather than the rows the gone sector's economy last answered under a revision that has
            // no reason to have moved.
            var sectorMock = mock(SectorAPI.class);
            var viewMock = stubViewAnswering(sectorMock, "factions");
            var cache = buildCacheOver(sectorMock);

            cache.resolveBlocPickerRead(viewMock);
            cache.disposeMachinery();
            cache.resolveBlocPickerRead(viewMock);

            verify(viewMock, times(2))
                .resolveBlocPickerRead(sectorMock);
        }
    }

    // A cache over one sector's machinery, which is the only way a cache exists - the installation
    // is what supplies the sector every walk below is taken against.
    private static SelectableBlocCache buildCacheOver(SectorAPI sectorMock) {
        return new SelectableBlocCache(new MapLayerInstallation(sectorMock));
    }

    // A view answering READ for the stated sector under a stated id, which every case needs standing
    // before it can ask the cache anything. Its content revision is left unstubbed and so holds
    // constant, which is what every case but the staleness one wants; that case states its own
    // moving pair.
    //
    // The read is stubbed through doReturn because the seam answers a wildcarded read: a when() stub
    // would have to name the captured item type, which is exactly what the wildcard hides.
    private static PoliticalMapView stubViewAnswering(SectorAPI sectorMock, String viewId) {
        var viewMock = mock(PoliticalMapView.class);

        when(viewMock.getId())
            .thenReturn(viewId);

        doReturn(READ)
            .when(viewMock)
            .resolveBlocPickerRead(sectorMock);

        return viewMock;
    }

    // The same view answering a second sector's walk with a read of its own, which is what lets a
    // two-sector case tell a cache reading the wrong sector's walk from one that merely walked twice.
    private static PoliticalMapView stubViewAnsweringPerSector(
            SectorAPI sectorMock,
            SectorAPI otherSectorMock) {

        var viewMock = stubViewAnswering(sectorMock, "factions");

        doReturn(OTHER_READ)
            .when(viewMock)
            .resolveBlocPickerRead(otherSectorMock);

        return viewMock;
    }
}
