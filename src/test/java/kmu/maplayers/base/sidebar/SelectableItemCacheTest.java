package kmu.maplayers.base.sidebar;

import com.fs.starfarer.api.campaign.SectorAPI;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the picker memo: it walks the caller's supplier once for a given sector, scope, and revision
 * and serves that list until one of them changes, so the per-frame sidebar draw does not re-resolve
 * the list. Run over the foreign {@link Hazard} item, since the memo names nothing about what it
 * holds. Each test uses its own cache instance, so the first call always recomputes.
 */
final class SelectableItemCacheTest {

    // The list the stubbed supplier returns; the memo forwards it verbatim, so its contents only
    // stand in.
    private static final List<Hazard> HAZARDS = List.of(new Hazard("Storm", 9, 8));

    // The scope every single-scope test resolves under; a switch between scopes is its own case.
    private static final String SCOPE_ID = "hazards";

    private final SelectableItemCache<Hazard> cache = new SelectableItemCache<>();

    // A stubbed walk of the list, so a test can count how often the memo actually reached for one.
    // Generic mocks cannot be created without an unchecked cast, so it is made once here rather than
    // suppressed at every call site.
    @SuppressWarnings("unchecked")
    private static Supplier<List<Hazard>> supplierReturning(List<Hazard> items) {
        Supplier<List<Hazard>> supplierMock = mock(Supplier.class);
        when(supplierMock.get()).thenReturn(items);
        return supplierMock;
    }

    @Nested
    class ResolveItems {

        @Test
        void resolveItemsWalksTheSupplierOnceThenServesTheMemoForTheSameInputs() {
            var sectorMock = mock(SectorAPI.class);
            var supplierMock = supplierReturning(HAZARDS);

            var first = cache.resolveItems(sectorMock, SCOPE_ID, 7, supplierMock);
            var second = cache.resolveItems(sectorMock, SCOPE_ID, 7, supplierMock);

            assertThat(first).isEqualTo(HAZARDS);
            assertThat(second).isEqualTo(HAZARDS);

            // One walk feeds both calls: the second reads the memo rather than re-resolving.
            verify(
                supplierMock,
                times(1)).get();
        }

        @Test
        void resolveItemsRecomputesWhenTheRevisionMoves() {
            // A moved revision is the caller saying something the list depends on changed, so the
            // memo is stale and must re-resolve.
            var sectorMock = mock(SectorAPI.class);
            var supplierMock = supplierReturning(HAZARDS);

            cache.resolveItems(sectorMock, SCOPE_ID, 0, supplierMock);
            cache.resolveItems(sectorMock, SCOPE_ID, 1, supplierMock);

            verify(
                supplierMock,
                times(2)).get();
        }

        @Test
        void resolveItemsRecomputesForADifferentScope() {
            // Two scopes hold different lists, so a switch between them must re-resolve rather than
            // serve the scope the memo happens to hold.
            var sectorMock = mock(SectorAPI.class);
            var supplierMock = supplierReturning(HAZARDS);

            cache.resolveItems(sectorMock, SCOPE_ID, 0, supplierMock);
            cache.resolveItems(sectorMock, "other", 0, supplierMock);

            verify(
                supplierMock,
                times(2)).get();
        }

        @Test
        void resolveItemsRecomputesForADifferentSector() {
            // A save reloaded in the same session is a fresh sector under the same revision, so the
            // memo must recompute against it rather than serve the previous save's items.
            var firstSectorMock = mock(SectorAPI.class);
            var secondSectorMock = mock(SectorAPI.class);
            var supplierMock = supplierReturning(HAZARDS);

            cache.resolveItems(firstSectorMock, SCOPE_ID, 0, supplierMock);
            cache.resolveItems(secondSectorMock, SCOPE_ID, 0, supplierMock);

            verify(
                supplierMock,
                times(2)).get();
        }
    }
}
