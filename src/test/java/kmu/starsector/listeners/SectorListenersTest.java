package kmu.starsector.listeners;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static kmu.starsector.listeners.SectorListenerFixtures.buildSector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Pins the one registration shape and its counterpart: a class cleared before anything is added
 * under it, the addition made transient, and both halves surviving a sector that cannot answer.
 *
 * <p>Every installer's own suite asserts these calls happened for its own listener; what is fixed
 * here is what they mean, so a change to the shape fails once rather than in eight suites at
 * whatever depth each happened to assert.
 */
class SectorListenersTest {

    private static final class SomeListener {
    }

    private static final class AnotherListener {
    }

    @Nested
    class InstallListener {

        @Test
        void clearsTheClassBeforeAddingUnderIt() {
            // The order is the contract, not an implementation detail: adding first and clearing
            // after would take the listener straight back out again.
            var listenerManager = new RecordingListenerManager();
            var listener = new SomeListener();

            SectorListeners.installListener(
                buildSector(listenerManager), SomeListener.class, () -> listener);

            assertThat(listenerManager.getRemovedListenerClasses())
                .containsExactly(SomeListener.class);

            assertThat(listenerManager.getAddedListeners())
                .containsExactly(listener);
        }

        @Test
        void addsTheListenerTransient() {
            // Transient is what keeps it out of the save, which is why nothing here needs removing
            // across a load - only within the session that registered it.
            var listenerManager = new RecordingListenerManager();

            SectorListeners.installListener(
                buildSector(listenerManager), SomeListener.class, SomeListener::new);

            assertThat(listenerManager.getAddedTransientFlags())
                .containsExactly(true);
        }

        @Test
        void leavesExactlyOneWhenAskedTwice() {
            // Idempotent by clearing rather than by checking, and the clearing is what makes the
            // one that runs the fresh one rather than whatever was there before.
            var listenerManager = new RecordingListenerManager();
            var sector = buildSector(listenerManager);
            var secondListener = new SomeListener();

            SectorListeners.installListener(sector, SomeListener.class, SomeListener::new);
            SectorListeners.installListener(sector, SomeListener.class, () -> secondListener);

            assertThat(listenerManager.getRemovedListenerClasses())
                .containsExactly(SomeListener.class, SomeListener.class);

            assertThat(listenerManager.getAddedListeners())
                .last()
                .isSameAs(secondListener);
        }

        @Test
        void buildsNothingWhereThereIsNoManagerToAddTo() {
            // A load that cannot register must not construct the listener either: building one can
            // reach a live screen, and a start-up step is not the place to pay for that and discard
            // the result.
            var listenersBuilt = new ArrayList<String>();

            SectorListeners.installListener(
                buildSector(null),
                SomeListener.class,
                () -> {
                    listenersBuilt.add("built");
                    return new SomeListener();
                });

            assertThat(listenersBuilt)
                .isEmpty();
        }

        @Test
        void toleratesAMissingSector() {

            var installOnNullSector = (Runnable) () -> SectorListeners.installListener(
                null, SomeListener.class, SomeListener::new);

            assertThatCode(installOnNullSector::run)
                .doesNotThrowAnyException();
        }
    }

    @Nested
    class RemoveListener {

        @Test
        void clearsOnlyTheClassItWasAsked() {
            // A feature switched off takes its own listeners back and nobody else's.
            var listenerManager = new RecordingListenerManager();

            SectorListeners.removeListener(buildSector(listenerManager), AnotherListener.class);

            assertThat(listenerManager.getRemovedListenerClasses())
                .containsExactly(AnotherListener.class);

            assertThat(listenerManager.getAddedListeners())
                .isEmpty();
        }

        @Test
        void toleratesAMissingSector() {

            var removeOnNullSector = (Runnable) () ->
                SectorListeners.removeListener(null, SomeListener.class);

            assertThatCode(removeOnNullSector::run)
                .doesNotThrowAnyException();
        }

        @Test
        void toleratesAMissingListenerManager() {

            var removeOnNullManager = (Runnable) () ->
                SectorListeners.removeListener(buildSector(null), SomeListener.class);

            assertThatCode(removeOnNullManager::run)
                .doesNotThrowAnyException();
        }
    }
}
