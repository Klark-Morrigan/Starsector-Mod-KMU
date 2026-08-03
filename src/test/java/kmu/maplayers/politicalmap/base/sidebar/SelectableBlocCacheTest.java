package kmu.maplayers.politicalmap.base.sidebar;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.SelectableBloc;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins what the political map counts as a reason to rebuild its picker list: the view's own content
 * revision moves it and nothing else does, so a live list is served from the memo and a stale one is
 * re-walked. The memo mechanism itself is the framework's and is pinned there; what is pinned here is
 * the revision this layer feeds it. Each test uses a distinct mock sector, so the first call always
 * recomputes regardless of what a prior test left in the shared memo. The live settings revision
 * reads zero in a test JVM (no LunaLib listener runs), so the view's content revision is the only
 * moving part of the key here.
 */
final class SelectableBlocCacheTest {

    // The list the stubbed view returns; the memo forwards it verbatim, so its contents only stand in.
    private static final List<SelectableBloc> BLOCS =
        List.of(new SelectableBloc("hegemony", "Hegemony", null));

    @Nested
    class ResolveSelectableBlocs {

        @Test
        void resolveSelectableBlocsWalksTheViewOnceWhileItsRevisionHolds() {
            // Nothing the list depends on moved between the two calls, so the revision this layer
            // computes must come out the same and the second call read the memo.
            var sectorMock = mock(SectorAPI.class);
            var viewMock = mock(PoliticalMapView.class);

            when(viewMock.getId())
                .thenReturn("factions");
            when(viewMock.getContentRevision())
                .thenReturn(7);
            when(viewMock.resolveSelectableBlocs(sectorMock))
                .thenReturn(BLOCS);

            var first = SelectableBlocCache.resolveSelectableBlocs(viewMock, sectorMock);
            var second = SelectableBlocCache.resolveSelectableBlocs(viewMock, sectorMock);

            assertThat(first).isEqualTo(BLOCS);
            assertThat(second).isEqualTo(BLOCS);

            verify(viewMock, times(1))
                .resolveSelectableBlocs(sectorMock);
        }

        @Test
        void resolveSelectableBlocsRecomputesWhenTheViewsContentRevisionMoves() {
            var sectorMock = mock(SectorAPI.class);
            var viewMock = mock(PoliticalMapView.class);

            when(viewMock.getId())
                .thenReturn("alliances");

            // A membership change bumps the alliances view's content revision between the two calls,
            // so the memo is stale and must re-resolve.
            when(viewMock.getContentRevision())
                .thenReturn(0, 1);
            when(viewMock.resolveSelectableBlocs(sectorMock))
                .thenReturn(BLOCS);

            SelectableBlocCache.resolveSelectableBlocs(viewMock, sectorMock);
            SelectableBlocCache.resolveSelectableBlocs(viewMock, sectorMock);

            verify(viewMock, times(2))
                .resolveSelectableBlocs(sectorMock);
        }
    }
}
