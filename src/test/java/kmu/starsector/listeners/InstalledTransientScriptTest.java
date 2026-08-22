package kmu.starsector.listeners;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.campaign.SectorAPI;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Pins that a script is taken off by the instance this slot put there, and never by its class.
 *
 * <p>That distinction is the slot's whole reason. The scripts held in one are KMLib's, which a
 * sibling mod may be running its own of over the same sector, and at least one hands a widget its
 * own state back as it goes - so a removal by class would reach into something this mod never
 * touched. Nothing about the by-class call looks wrong at a glance, which is why the rule wants
 * pinning rather than remembering.
 */
class InstalledTransientScriptTest {

    // A script of no behaviour: everything here is about which instance was handed to the sector,
    // so what it would do on a frame never comes into it.
    private static final class CountingScript implements EveryFrameScript {

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public boolean runWhilePaused() {
            return false;
        }

        @Override
        public void advance(float amount) {
        }
    }

    @Nested
    class InstallOn {

        @Test
        void addsTheBuiltScriptToTheSectorAsATransientOne() {

            var sectorMock = mock(SectorAPI.class);
            var script = new CountingScript();

            new InstalledTransientScript<CountingScript>().installOn(sectorMock, () -> script);

            verify(sectorMock)
                .addTransientScript(script);
            verify(sectorMock, never())
                .addScript(any());
        }

        @Test
        void buildsNothingWhereThereIsNoSectorToInstallOn() {
            // A load that cannot install must not construct the script either: building one can
            // reach a live screen, and there would be nothing to hand it to.
            var scriptsBuilt = new ArrayList<String>();

            new InstalledTransientScript<CountingScript>().installOn(
                null,
                () -> {
                    scriptsBuilt.add("built");
                    return new CountingScript();
                });

            assertThat(scriptsBuilt)
                .isEmpty();
        }
    }

    @Nested
    class RemoveFrom {

        @Test
        void takesOffTheHeldInstanceRatherThanEveryScriptOfItsClass() {

            var sectorMock = mock(SectorAPI.class);
            var script = new CountingScript();
            var slot = new InstalledTransientScript<CountingScript>();

            slot.installOn(sectorMock, () -> script);
            slot.removeFrom(sectorMock);

            verify(sectorMock)
                .removeTransientScript(script);
            verify(sectorMock, never())
                .removeTransientScriptsOfClass(any());
        }

        @Test
        void takesOffTheLatestInstallRatherThanAnEarlierOne() {
            // A slot holds one script, and the sector it belongs to is whichever load installed it
            // last. Reaching for the earlier one would ask a sector to drop a script it never had.
            var firstLoadSectorMock = mock(SectorAPI.class);
            var secondLoadSectorMock = mock(SectorAPI.class);
            var firstScript = new CountingScript();
            var secondScript = new CountingScript();
            var slot = new InstalledTransientScript<CountingScript>();

            slot.installOn(firstLoadSectorMock, () -> firstScript);
            slot.installOn(secondLoadSectorMock, () -> secondScript);
            slot.removeFrom(secondLoadSectorMock);

            verify(secondLoadSectorMock)
                .removeTransientScript(secondScript);
            verify(firstLoadSectorMock, never())
                .removeTransientScript(any());
        }

        @Test
        void takesOffNothingWhenAskedTwice() {
            // Cleared after the first removal, so a second cannot reach for a script belonging to a
            // sector this slot has since left.
            var sectorMock = mock(SectorAPI.class);
            var slot = new InstalledTransientScript<CountingScript>();

            slot.installOn(sectorMock, CountingScript::new);
            slot.removeFrom(sectorMock);
            slot.removeFrom(sectorMock);

            verify(sectorMock)
                .removeTransientScript(any());
        }

        @Test
        void takesOffNothingWhereNothingWasInstalled() {

            var sectorMock = mock(SectorAPI.class);

            new InstalledTransientScript<CountingScript>().removeFrom(sectorMock);

            verify(sectorMock, never())
                .removeTransientScript(any());
        }

        @Test
        void toleratesAMissingSector() {

            var removeOnNullSector = (Runnable) () ->
                new InstalledTransientScript<CountingScript>().removeFrom(null);

            assertThatCode(removeOnNullSector::run)
                .doesNotThrowAnyException();
        }
    }
}
