package kmu.starsector.listeners;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.campaign.SectorAPI;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Pins the one registration shape and its counterpart: the built script's own class cleared before
 * it is added, the addition made transient rather than saved, and both halves surviving a sector
 * that is not there.
 *
 * <p>Every installer's own suite asserts these calls happened for its own script; what is fixed here
 * is what they mean, so a change to the shape fails once rather than in each of those suites at
 * whatever depth it happened to be asserted.
 *
 * <p>That the class cleared is read off the built script rather than named beside it is pinned as a
 * case of its own, that being the one thing this does differently from {@link SectorListeners} and
 * the reason it can: a caller able to name the class is a caller able to name the wrong one, and
 * what that buys is one pass silently doubling while a sibling disappears.
 */
class SectorScriptsTest {

    // Two scripts of no behaviour: everything here is about which class was cleared and which
    // instance was handed over, so what either would do on a frame never comes into it.
    private static class SomeScript implements EveryFrameScript {

        @Override
        public void advance(float amount) {
        }

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public boolean runWhilePaused() {
            return false;
        }
    }

    private static final class AnotherScript extends SomeScript {
    }

    @Nested
    class InstallScript {

        @Test
        void clearsTheClassBeforeAddingUnderIt() {
            // The order is the contract, not an implementation detail: adding first and clearing
            // after would take the script straight back out again.
            var sectorMock = mock(SectorAPI.class);
            var script = new SomeScript();

            SectorScripts.installScript(sectorMock, () -> script);

            var registration = inOrder(sectorMock);

            registration.verify(sectorMock)
                .removeTransientScriptsOfClass(SomeScript.class);
            registration.verify(sectorMock)
                .addTransientScript(script);
        }

        @Test
        void addsTheScriptTransientRatherThanIntoTheSave() {
            // A script that entered the save would be restored beside the one each load adds, and
            // would bake its class name into the file besides.
            var sectorMock = mock(SectorAPI.class);

            SectorScripts.installScript(sectorMock, SomeScript::new);

            verify(sectorMock)
                .addTransientScript(any(SomeScript.class));
            verify(sectorMock, never())
                .addScript(any());
        }

        @Test
        void clearsTheClassOfTheScriptItActuallyBuilt() {
            // The case for reading the class off the script: a subclass registers and clears under
            // its own name, which is what a caller naming the class beside it could get wrong.
            var sectorMock = mock(SectorAPI.class);

            SectorScripts.installScript(sectorMock, AnotherScript::new);

            verify(sectorMock)
                .removeTransientScriptsOfClass(AnotherScript.class);
            verify(sectorMock, never())
                .removeTransientScriptsOfClass(SomeScript.class);
        }

        @Test
        void leavesExactlyOneWhenAskedTwice() {
            // Idempotent by clearing rather than by checking, and the clearing is what makes the one
            // that runs the fresh one rather than whatever was there before.
            var sectorMock = mock(SectorAPI.class);
            var secondScript = new SomeScript();

            SectorScripts.installScript(sectorMock, SomeScript::new);
            SectorScripts.installScript(sectorMock, () -> secondScript);

            var registration = inOrder(sectorMock);

            registration.verify(sectorMock)
                .removeTransientScriptsOfClass(SomeScript.class);
            registration.verify(sectorMock)
                .removeTransientScriptsOfClass(SomeScript.class);
            registration.verify(sectorMock)
                .addTransientScript(secondScript);
        }

        @Test
        void buildsNothingWhereThereIsNoSectorToAddTo() {
            // A load that cannot register must not construct the script either: building one can
            // reach this sector's installed machinery, and a start-up step is not the place to pay
            // for that and discard the result.
            var scriptsBuilt = new ArrayList<String>();

            SectorScripts.installScript(
                null,
                () -> {
                    scriptsBuilt.add("built");
                    return new SomeScript();
                });

            assertThat(scriptsBuilt)
                .isEmpty();
        }

        @Test
        void toleratesAMissingSector() {

            var installOnNullSector = (Runnable) () ->
                SectorScripts.installScript(null, SomeScript::new);

            assertThatCode(installOnNullSector::run)
                .doesNotThrowAnyException();
        }
    }

    @Nested
    class RemoveScript {

        @Test
        void clearsOnlyTheClassItWasAsked() {
            // A feature switched off takes its own passes back and nobody else's.
            var sectorMock = mock(SectorAPI.class);

            SectorScripts.removeScript(sectorMock, AnotherScript.class);

            verify(sectorMock)
                .removeTransientScriptsOfClass(AnotherScript.class);
            verify(sectorMock, never())
                .addTransientScript(any());
        }

        @Test
        void toleratesAMissingSector() {

            var removeOnNullSector = (Runnable) () ->
                SectorScripts.removeScript(null, SomeScript.class);

            assertThatCode(removeOnNullSector::run)
                .doesNotThrowAnyException();
        }
    }
}
