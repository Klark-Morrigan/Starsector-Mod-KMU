package kmu.maplayers.base.visibility.colonies;

import kmlib.testfixtures.starsector.listeners.RecordingListenerManager;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static kmlib.testfixtures.starsector.listeners.SectorListenerFixtures.buildSector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Pins the sighting recorder's registration shape - replaced outright on every load, never merely
 * added beside whatever a save carried back - and the boundary each wiring step runs behind, which
 * is the whole of what installing the two together adds over either alone.
 */
class ColonySightingInstallerTest {

    @Nested
    class InstallAll {

        @Test
        void installsTheSightingRecorderThoughTheReconciliationFails() {
            // The reason each step carries its own failure boundary. The posed sector answers for
            // its listener manager and refuses every other question, so the reconciliation throws
            // on the first thing it asks of the save - and the recorder still goes up, which is
            // what keeps a damaged register from costing the player every sighting made from here
            // on as well.
            var listenerManager = new RecordingListenerManager();

            ColonySightingInstaller.installAll(buildSector(listenerManager));

            assertThat(listenerManager.getAddedListeners())
                .singleElement()
                .isInstanceOf(ColonySightingRecorder.class);
        }

        @Test
        void toleratesASectorThatIsNotThere() {
            // Wiring runs where a load may have gone wrong before it, so an absent sector leaves
            // the register untouched rather than taking the rest of the wiring list down with it.
            var installOnNoSector = (Runnable) () -> ColonySightingInstaller.installAll(null);

            assertThatCode(installOnNoSector::run)
                .doesNotThrowAnyException();
        }
    }

    @Nested
    class InstallColonySightingRecorder {

        @Test
        void reinstallsTheSightingRecorderFreshAsTransient() {
            // Remove-then-add rather than a has-check, because the recorder holds the sector it
            // writes into: a kept registration would be the one built against whatever came
            // before, going on recording there while the loaded sector learned nothing.
            var listenerManager = new RecordingListenerManager();

            ColonySightingInstaller.installColonySightingRecorder(buildSector(listenerManager));

            assertThat(listenerManager.getRemovedListenerClasses())
                .containsExactly(ColonySightingRecorder.class);

            assertThat(listenerManager.getAddedListeners())
                .singleElement()
                .isInstanceOf(ColonySightingRecorder.class);

            assertThat(listenerManager.getAddedTransientFlags())
                .containsExactly(true);
        }

        @Test
        void toleratesAMissingListenerManager() {

            var recorderInstallOnNullManager = (Runnable) () ->
                ColonySightingInstaller.installColonySightingRecorder(buildSector(null));

            assertThatCode(recorderInstallOnNullManager::run)
                .doesNotThrowAnyException();
        }
    }
}
