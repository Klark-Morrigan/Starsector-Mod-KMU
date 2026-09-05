package kmu.maplayers.base.layer;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins what keeps the store off the frame path: the file behind it is opened once, and what the player
 * arranges afterwards is answered without going back to it.
 *
 * <p>Both halves are what make the arrangement affordable at all. The bar asks on every frame it draws and
 * every key it routes, so a read that reached the disk would be a file opened sixty times a second; and a
 * write that did not move what is held would leave the player looking at their old bar until the next
 * start, having just arranged a new one.
 */
final class SessionHeldMapLayerArrangementTest {

    private final MapLayerArrangementSelection storedArrangementMock =
        mock(MapLayerArrangementSelection.class);

    private final SessionHeldMapLayerArrangement heldArrangement =
        new SessionHeldMapLayerArrangement(storedArrangementMock);

    @Nested
    class ReadArrangement {

        @Test
        void readArrangementAnswersWhatTheStoreHolds() {

            var arrangement = new MapLayerArrangement(List.of("gamma"), List.of("beta"));

            when(storedArrangementMock.readArrangement())
                .thenReturn(arrangement);

            assertThat(heldArrangement.readArrangement())
                .isEqualTo(arrangement);
        }

        @Test
        void readArrangementOpensTheStoreOnceHoweverOftenItIsAsked() {

            when(storedArrangementMock.readArrangement())
                .thenReturn(new MapLayerArrangement(List.of("gamma"), List.of()));

            heldArrangement.readArrangement();
            heldArrangement.readArrangement();
            heldArrangement.readArrangement();

            verify(storedArrangementMock, times(1))
                .readArrangement();
        }

        @Test
        void readArrangementHoldsTheUnarrangedAnswerToo() {
            // The install every player starts on. Read again each frame it would be the same answer at
            // the cost of a missing-file check per frame, so nothing having been arranged is a held
            // answer like any other.
            when(storedArrangementMock.readArrangement())
                .thenReturn(MapLayerArrangement.UNARRANGED);

            heldArrangement.readArrangement();
            heldArrangement.readArrangement();

            verify(storedArrangementMock, times(1))
                .readArrangement();
        }
    }

    @Nested
    class RecordArrangement {

        @Test
        void recordArrangementWritesThroughToTheStore() {

            var arrangement = new MapLayerArrangement(List.of("alpha"), List.of());

            heldArrangement.recordArrangement(arrangement);

            verify(storedArrangementMock)
                .recordArrangement(arrangement);
        }

        @Test
        void recordArrangementAnswersTheNewArrangementWithoutReopeningTheStore() {
            // What the dialog leaves behind: the bar the player just arranged, on the next frame, from
            // the value they arranged rather than from a file just written and read back.
            var arrangement = new MapLayerArrangement(List.of("alpha"), List.of("gamma"));

            heldArrangement.recordArrangement(arrangement);

            assertThat(heldArrangement.readArrangement())
                .isEqualTo(arrangement);

            verify(storedArrangementMock, never())
                .readArrangement();
        }
    }
}
