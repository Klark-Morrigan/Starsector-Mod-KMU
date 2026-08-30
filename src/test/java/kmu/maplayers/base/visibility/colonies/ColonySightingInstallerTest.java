package kmu.maplayers.base.visibility.colonies;

import kmu.starsector.listeners.RecordingListenerManager;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static kmu.starsector.listeners.SectorListenerFixtures.buildSector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Pins the sighting recorder's registration shape: replaced outright on every load, never merely
 * added beside whatever a save carried back.
 */
class ColonySightingInstallerTest {

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
