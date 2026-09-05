package kmu.maplayers.base.layer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the two readings the bar depends on before anything else is true: an install with no store bound
 * answers the unarranged row rather than failing, and a bound store is asked afresh every time.
 *
 * <p>The first is what lets the framework draw a bar in an install whose composition root never named a
 * store - a foreign one, or KMU's own before the wiring runs. The second is what keeps the answer honest
 * over a roster nothing settles: whatever holds a read for the session holds it where it is bound, so this
 * holder must not hold one of its own on top of it.
 */
final class LiveMapLayerArrangementTest {

    @AfterEach
    void unbindTheStoreThisCaseBound() {
        // The holder is static, so a store bound here would otherwise answer every later read in the JVM.
        MapLayerArrangements.forgetTheArrangement();
    }

    @Nested
    class ResolveArrangement {

        @Test
        void resolveArrangementAnswersUnarrangedWithNoStoreBound() {
            // The reading before any composition root has run, and the one an install that names no
            // store keeps: the row is exactly what registration built.
            assertThat(LiveMapLayerArrangement.resolveArrangement())
                .isEqualTo(MapLayerArrangement.UNARRANGED);
        }

        @Test
        void resolveArrangementAnswersWhatTheBoundStoreReads() {

            MapLayerArrangements.arrangeBarWith(List.of("gamma"), List.of("beta"));

            assertThat(LiveMapLayerArrangement.resolveArrangement())
                .isEqualTo(new MapLayerArrangement(List.of("gamma"), List.of("beta")));
        }

        @Test
        void resolveArrangementAsksTheBoundStoreOnEveryRead() {
            // Nothing is settled here. An arrangement made mid-session moves the bar on the next frame
            // because the store is asked again, and holding one here would instead pin the row to
            // whatever it said the first time anything drew.
            var arrangementSelectionMock = mock(MapLayerArrangementSelection.class);

            when(arrangementSelectionMock.readArrangement())
                .thenReturn(MapLayerArrangement.UNARRANGED);

            LiveMapLayerArrangement.registerArrangementSelection(arrangementSelectionMock);

            LiveMapLayerArrangement.resolveArrangement();
            LiveMapLayerArrangement.resolveArrangement();

            verify(arrangementSelectionMock, times(2))
                .readArrangement();
        }
    }
}
