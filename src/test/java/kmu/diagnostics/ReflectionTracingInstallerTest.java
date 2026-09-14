package kmu.diagnostics;

import kmlib.starsector.ui.map.probes.MapProbeWarnings;

import kmu.settings.KmuLoggingSettings;
import kmu.settings.KmuLunaSettings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/**
 * Pins that the probes' warnings are let speak again exactly when a reader arrives, and at no other
 * time.
 *
 * <p>Both halves matter. Never re-arming leaves a diagnostic switched on after a reach broke saying
 * nothing and explaining nothing; re-arming on every announcement turns an unrelated knob into a way
 * to unmute a warning nobody asked to hear, on a path read once a frame.
 */
class ReflectionTracingInstallerTest {

    // Runs the installer against a switch reading `enabledAtInstall`, then fires one settings change
    // with it reading `enabledAtChange`, and hands back the probe-warning mock to assert on. LunaLib
    // announces the settings rather than the setting, so a change is a bare callback with the live
    // value read behind it - which is the shape being tested.
    private static void installThenChangeSettings(
            boolean enabledAtInstall,
            boolean enabledAtChange,
            Consumer<MockedStatic<MapProbeWarnings>> assertOn) {

        try (var settingsMock = mockStatic(KmuLoggingSettings.class);
                var lunaMock = mockStatic(KmuLunaSettings.class);
                var warningsMock = mockStatic(MapProbeWarnings.class)) {

            var onSettingsChange = new Runnable[1];
            lunaMock.when(() -> KmuLunaSettings.runOnSettingsChange(any()))
                .thenAnswer(invocation -> {
                    onSettingsChange[0] = invocation.getArgument(0);
                    return null;
                });

            settingsMock.when(KmuLoggingSettings::areReflectionProbesEnabled)
                .thenReturn(enabledAtInstall);
            ReflectionTracingInstaller.installAll();

            settingsMock.when(KmuLoggingSettings::areReflectionProbesEnabled)
                .thenReturn(enabledAtChange);
            onSettingsChange[0].run();

            assertOn.accept(warningsMock);
        }
    }

    @Nested
    class InstallAll {

        @Test
        void installAllRearmsTheProbeWarningsWhenTheTracesAreSwitchedOn() {
            // The case the whole class exists for: a reach broke during ordinary play and spent its
            // one line, and the reader who then asks for the traces is owed that line again.
            installThenChangeSettings(false, true, warningsMock ->
                warningsMock.verify(MapProbeWarnings::rearmAllWarnings, times(1)));
        }

        @Test
        void installAllRearmsNothingWhileTheTracesStayOff() {

            installThenChangeSettings(false, false, warningsMock ->
                warningsMock.verify(MapProbeWarnings::rearmAllWarnings, never()));
        }

        @Test
        void installAllRearmsNothingOnAChangeThatLeavesTheTracesOn() {
            // The guard against unrelated knobs. Settings changes arrive for every field, and a
            // re-arm on each would unmute a warning on a path that runs once a frame.
            installThenChangeSettings(true, true, warningsMock ->
                warningsMock.verify(MapProbeWarnings::rearmAllWarnings, never()));
        }

        @Test
        void installAllRearmsNothingWhenTheTracesAreSwitchedOff() {
            // A reader who has just stopped listening is owed no line.
            installThenChangeSettings(true, false, warningsMock ->
                warningsMock.verify(MapProbeWarnings::rearmAllWarnings, never()));
        }
    }
}
