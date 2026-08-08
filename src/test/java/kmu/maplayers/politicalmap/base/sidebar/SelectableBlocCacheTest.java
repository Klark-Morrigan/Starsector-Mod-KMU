package kmu.maplayers.politicalmap.base.sidebar;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.ui.widgets.lists.ListPicker;

import kmu.maplayers.politicalmap.base.DominanceSortMode;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.RankedBloc;
import kmu.maplayers.politicalmap.base.SelectableBloc;
import kmu.maplayers.politicalmap.base.politics.DominanceStats;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

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
 * here is the revision this layer feeds it. Each test uses a distinct mock sector, so the first call
 * always recomputes regardless of what a prior test left in the shared memo. The live settings
 * revision reads zero in a test JVM (no LunaLib listener runs), so the view's content revision is
 * the only moving part of the key here.
 */
final class SelectableBlocCacheTest {

    // The picker the stubbed view returns; the memo forwards it verbatim, so its contents only stand
    // in - what a view actually offers is pinned on that view.
    private static final ListPicker<RankedBloc<DominanceStats>> PICKER =
        new ListPicker<>(
            List.of(new RankedBloc<>(
                new SelectableBloc("hegemony", "Hegemony", null),
                DominanceStats.EMPTY)),
            DominanceSortMode.MODES);

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

            // Stubbed through doReturn because the seam answers a wildcarded picker: a when() stub
            // would have to name the captured item type, which is exactly what the wildcard hides.
            doReturn(PICKER)
                .when(viewMock)
                .resolveBlocPicker(sectorMock);

            var first = SelectableBlocCache.resolveBlocPicker(viewMock, sectorMock);
            var second = SelectableBlocCache.resolveBlocPicker(viewMock, sectorMock);

            assertThat(first).isEqualTo(PICKER);
            assertThat(second).isEqualTo(PICKER);

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

            doReturn(PICKER)
                .when(viewMock)
                .resolveBlocPicker(sectorMock);

            SelectableBlocCache.resolveBlocPicker(viewMock, sectorMock);
            SelectableBlocCache.resolveBlocPicker(viewMock, sectorMock);

            verify(viewMock, times(2))
                .resolveBlocPicker(sectorMock);
        }
    }
}
