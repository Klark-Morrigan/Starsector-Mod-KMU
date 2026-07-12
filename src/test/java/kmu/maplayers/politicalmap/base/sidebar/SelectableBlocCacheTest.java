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
 * Pins the picker's selectable-bloc memo: it walks the view once for a given sector and revision and
 * serves that list until one of them changes, so the per-frame sidebar draw does not rescan the
 * economy. Each test uses a distinct mock sector, so the first call always recomputes regardless of
 * what a prior test left in the static memo. The live settings revision reads zero in a test JVM (no
 * LunaLib listener runs), so the view's content revision is the only moving part of the key here.
 */
final class SelectableBlocCacheTest {

    // The list the stubbed view returns; the memo forwards it verbatim, so its contents only stand in.
    private static final List<SelectableBloc> BLOCS =
            List.of(new SelectableBloc("hegemony", "Hegemony", null));

    private static PoliticalMapView viewReturning(String id, int contentRevision,
            List<SelectableBloc> blocs, SectorAPI sector) {
        var viewMock = mock(PoliticalMapView.class);
        when(viewMock.getId()).thenReturn(id);
        when(viewMock.getContentRevision()).thenReturn(contentRevision);
        when(viewMock.resolveSelectableBlocs(sector)).thenReturn(blocs);
        return viewMock;
    }

    @Nested
    class ResolveSelectableBlocs {

        @Test
        void resolveSelectableBlocsWalksTheViewOnceThenServesTheMemoForTheSameInputs() {
            var sectorMock = mock(SectorAPI.class);
            var viewMock = viewReturning("factions", 7, BLOCS, sectorMock);

            var first = SelectableBlocCache.resolveSelectableBlocs(viewMock, sectorMock);
            var second = SelectableBlocCache.resolveSelectableBlocs(viewMock, sectorMock);

            assertThat(first).isEqualTo(BLOCS);
            assertThat(second).isEqualTo(BLOCS);
            // One walk feeds both calls: the second reads the memo rather than re-resolving.
            verify(viewMock, times(1)).resolveSelectableBlocs(sectorMock);
        }

        @Test
        void resolveSelectableBlocsRecomputesWhenTheViewsContentRevisionMoves() {
            var sectorMock = mock(SectorAPI.class);
            var viewMock = mock(PoliticalMapView.class);
            when(viewMock.getId()).thenReturn("alliances");
            // A membership change bumps the alliances view's content revision between the two calls,
            // so the memo is stale and must re-resolve.
            when(viewMock.getContentRevision()).thenReturn(0, 1);
            when(viewMock.resolveSelectableBlocs(sectorMock)).thenReturn(BLOCS);

            SelectableBlocCache.resolveSelectableBlocs(viewMock, sectorMock);
            SelectableBlocCache.resolveSelectableBlocs(viewMock, sectorMock);

            verify(viewMock, times(2)).resolveSelectableBlocs(sectorMock);
        }

        @Test
        void resolveSelectableBlocsRecomputesForADifferentSector() {
            // A save reloaded in the same session is a fresh sector under the same revision, so the
            // memo must recompute against it rather than serve the previous save's blocs.
            var firstSectorMock = mock(SectorAPI.class);
            var secondSectorMock = mock(SectorAPI.class);
            var viewMock = mock(PoliticalMapView.class);
            when(viewMock.getId()).thenReturn("factions");
            when(viewMock.getContentRevision()).thenReturn(0);
            when(viewMock.resolveSelectableBlocs(firstSectorMock)).thenReturn(BLOCS);
            when(viewMock.resolveSelectableBlocs(secondSectorMock)).thenReturn(BLOCS);

            SelectableBlocCache.resolveSelectableBlocs(viewMock, firstSectorMock);
            SelectableBlocCache.resolveSelectableBlocs(viewMock, secondSectorMock);

            verify(viewMock, times(1)).resolveSelectableBlocs(firstSectorMock);
            verify(viewMock, times(1)).resolveSelectableBlocs(secondSectorMock);
        }
    }
}
