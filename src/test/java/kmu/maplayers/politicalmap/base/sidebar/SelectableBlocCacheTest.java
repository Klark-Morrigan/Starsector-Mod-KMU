package kmu.maplayers.politicalmap.base.sidebar;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.ui.widgets.lists.ListPicker;

import kmu.maplayers.politicalmap.base.BlocPickerRead;
import kmu.maplayers.politicalmap.base.DominanceSortMode;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.RankedBloc;
import kmu.maplayers.politicalmap.base.SelectableBloc;
import kmu.maplayers.politicalmap.base.politics.BlocPresenceIndex;
import kmu.maplayers.politicalmap.base.politics.DominanceStats;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
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
 * same read. Each test uses a distinct mock sector, so the first call
 * always recomputes regardless of what a prior test left in the shared memo. The live settings
 * revision reads zero in a test JVM (no LunaLib listener runs), so the view's content revision is
 * the only moving part of the key here.
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
            new BlocPresenceIndex(Map.of("hegemony", Set.of("corvus", "askonia"))));

    @Nested
    class ResolveBlocPicker {

        @Test
        void resolveBlocPickerWalksTheViewOnceWhileItsRevisionHolds() {
            // Nothing the picker depends on moved between the two calls, so the revision this layer
            // computes must come out the same and the second call read the memo.
            var sectorMock = mock(SectorAPI.class);
            var viewMock = mock(PoliticalMapView.class);

            when(viewMock.getId())
                .thenReturn("factions");
            when(viewMock.getContentRevision())
                .thenReturn(7);

            // Stubbed through doReturn because the seam answers a wildcarded read: a when() stub
            // would have to name the captured item type, which is exactly what the wildcard hides.
            doReturn(READ)
                .when(viewMock)
                .resolveBlocPicker(sectorMock);

            var first = SelectableBlocCache.resolveBlocPicker(viewMock, sectorMock);
            var second = SelectableBlocCache.resolveBlocPicker(viewMock, sectorMock);

            assertThat(first)
                .isEqualTo(READ);
            assertThat(second)
                .isEqualTo(READ);

            verify(viewMock, times(1))
                .resolveBlocPicker(sectorMock);
        }

        @Test
        void resolveBlocPickerRecomputesWhenTheViewsContentRevisionMoves() {

            var sectorMock = mock(SectorAPI.class);
            var viewMock = mock(PoliticalMapView.class);

            when(viewMock.getId())
                .thenReturn("alliances");

            // A membership change bumps the alliances view's content revision between the two calls,
            // so the memo is stale and must re-resolve.
            when(viewMock.getContentRevision())
                .thenReturn(0, 1);

            doReturn(READ)
                .when(viewMock)
                .resolveBlocPicker(sectorMock);

            SelectableBlocCache.resolveBlocPicker(viewMock, sectorMock);
            SelectableBlocCache.resolveBlocPicker(viewMock, sectorMock);

            verify(viewMock, times(2))
                .resolveBlocPicker(sectorMock);
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
            var viewMock = mock(PoliticalMapView.class);

            when(viewMock.getId())
                .thenReturn("factions");
            when(viewMock.getContentRevision())
                .thenReturn(3);

            doReturn(READ)
                .when(viewMock)
                .resolveBlocPicker(sectorMock);

            assertThat(SelectableBlocCache.readPresentSystemIds(viewMock, sectorMock, "hegemony"))
                .containsExactly("corvus", "askonia");
        }

        @Test
        void readPresentSystemIdsWalksTheViewOnceForTheRowsAndThePresenceTogether() {
            // The reason the two share a memo rather than a store apiece: asking for the rows and
            // then for a bloc's systems is one resolve, so a hover costs no economy walk of its own.
            var sectorMock = mock(SectorAPI.class);
            var viewMock = mock(PoliticalMapView.class);

            when(viewMock.getId())
                .thenReturn("factions");
            when(viewMock.getContentRevision())
                .thenReturn(4);

            doReturn(READ)
                .when(viewMock)
                .resolveBlocPicker(sectorMock);

            SelectableBlocCache.resolveBlocPicker(viewMock, sectorMock);
            SelectableBlocCache.readPresentSystemIds(viewMock, sectorMock, "hegemony");

            verify(viewMock, times(1))
                .resolveBlocPicker(sectorMock);
        }

        @Test
        void readPresentSystemIdsAnswersEmptyForABlocTheViewNeverSurfaced() {
            // A hover can outlive the row it started on (a rebuild between the report and the read),
            // so an unknown id has to answer an empty set rather than a null the render side would
            // fall over on.
            var sectorMock = mock(SectorAPI.class);
            var viewMock = mock(PoliticalMapView.class);

            when(viewMock.getId())
                .thenReturn("factions");
            when(viewMock.getContentRevision())
                .thenReturn(5);

            doReturn(READ)
                .when(viewMock)
                .resolveBlocPicker(sectorMock);

            assertThat(SelectableBlocCache.readPresentSystemIds(viewMock, sectorMock, "vanished"))
                .isEmpty();
        }
    }
}
