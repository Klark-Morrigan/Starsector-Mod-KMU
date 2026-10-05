package kmu.mods.nexerelin;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;

import kmlib.testfixtures.logging.LogAppenderFake;
import kmlib.testfixtures.starsector.compatibility.CompatibilityFailureFixture;
import kmlib.testfixtures.starsector.settings.ModStateScopes;
import kmlib.testfixtures.starsector.settings.StarsectorSettingsFake;

import kmu.KmuWiringSteps;
import kmu.starsector.listeners.MarketTransferListener;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static kmlib.testfixtures.starsector.settings.StubbedModIds.NEXERELIN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins {@link NexerelinInvasionListenerInstaller}: when Nexerelin is enabled the transfer relay is
 * (re)installed fresh and transient (not persisted), and removed again on the way out; when it is
 * disabled nothing is touched either way. Transience matters because the relay implements a
 * Nexerelin interface - persisting it would fail to load if Nexerelin were removed. The mod-enabled
 * gate is what makes the dependency optional, so the disabled cases are pinned alongside the
 * enabled ones.
 *
 * <p>And that the relay is built against the sector it is being installed on: it tells that
 * sector's own listeners about a conquest, so an installer handing over anything else would leave
 * it telling whichever sector the player happens to have loaded.
 *
 * <p>And that a Nexerelin release the relay no longer links against costs the relay alone. Its
 * install and removal each run behind KMU's guard, and the failure is read off the guard's log line
 * rather than the record: the record's latch outlives a case, so whichever case recorded second
 * would find nothing. The record is still drained on both sides, so nothing left in it reaches
 * another suite.
 */
final class NexerelinInvasionListenerInstallerTests {

    // A Nexerelin release that moved or renamed the interface the relay implements, as the JVM
    // reports it where the relay is first built.
    private static final NoClassDefFoundError MISSING_INTERFACE =
        new NoClassDefFoundError("exerelin/utilities/InvasionListener");

    private final ListenerManagerAPI listenerManagerMock = mock(ListenerManagerAPI.class);
    private final SectorAPI sectorMock = buildSectorWith(listenerManagerMock);

    @BeforeEach
    void drainTheSessionRecordBefore() {

        CompatibilityFailureFixture.drainSessionRecord();
    }

    @AfterEach
    void drainTheSessionRecordAfter() {

        CompatibilityFailureFixture.drainSessionRecord();
    }

    @Nested
    class DescribeColonyTransfersIntegration {

        @AfterEach
        void clearSettings() {

            StarsectorSettingsFake.clearSettings();
        }

        @Test
        void namesNexerelinAndTheColonyTransfersFeatureTheRelayCosts() {

            StarsectorSettingsFake.installSettings((category, key) -> "the sentence for " + key);

            var integration = NexerelinInvasionListenerInstaller.describeColonyTransfersIntegration();

            assertThat(integration.subjectModId())
                .isEqualTo(NEXERELIN);
            assertThat(integration.subjectModName())
                .isEqualTo("Nexerelin");
            assertThat(integration.consumer().consumerKey())
                .isEqualTo("kmu:nexerelin-colony-transfers");
            assertThat(integration.consumer().lostFeature())
                .isEqualTo("the sentence for compatibility_lost_nexerelin_colony_transfers");
            assertThat(integration.consumer().unaffectedFeature())
                .isEqualTo("the sentence for compatibility_unaffected_nexerelin_colony_transfers");
        }
    }

    @Nested
    class InstallIfPresent {

        @Test
        void reinstallsAFreshTransientRelayWhenNexerelinIsEnabled() {

            ModStateScopes.runWithModEnabled(NEXERELIN, true, () ->
                NexerelinInvasionListenerInstaller.installIfPresent(sectorMock));

            // Remove-then-add: clears any copy an older build persisted into the save, then adds
            // the relay transiently (true) so it never enters the save - it implements a Nexerelin
            // interface and would fail to load if Nexerelin were removed.
            verify(listenerManagerMock)
                .removeListenerOfClass(NexerelinMarketTransferRelay.class);
            verify(listenerManagerMock)
                .addListener(any(NexerelinMarketTransferRelay.class), eq(true));
        }

        @Test
        void buildsTheRelayAgainstTheSectorItIsInstalledOn() {
            // Read by driving the registered relay and looking for the call on a listener of the
            // installed sector: what the wiring is for is where a conquest lands, and a relay
            // holding the right sector while telling elsewhere would pass a check on the field.
            var transferListenerMock = mock(MarketTransferListener.class);
            var marketMock = mock(MarketAPI.class);

            when(listenerManagerMock.getListeners(MarketTransferListener.class))
                .thenReturn(List.of(transferListenerMock));

            ModStateScopes.runWithModEnabled(NEXERELIN, true, () ->
                NexerelinInvasionListenerInstaller.installIfPresent(sectorMock));

            var installedRelay = ArgumentCaptor.forClass(NexerelinMarketTransferRelay.class);

            verify(listenerManagerMock)
                .addListener(installedRelay.capture(), eq(true));

            installedRelay.getValue().reportMarketTransfered(
                marketMock, null, null, true, true, List.of(), 1.0f);

            verify(transferListenerMock)
                .reportMarketTransferred(marketMock, null, null, true);
        }

        @Test
        void addsNothingWhenNexerelinIsDisabled() {

            ModStateScopes.runWithModEnabled(NEXERELIN, false, () ->
                NexerelinInvasionListenerInstaller.installIfPresent(sectorMock));

            verifyNoInteractions(listenerManagerMock);
        }

        @Test
        void addsNothingToASectorWithoutAListenerManager() {

            var sectorWithoutManager = buildSectorWith(null);

            ModStateScopes.runWithModEnabled(NEXERELIN, true, () ->
                assertThatCode(() -> NexerelinInvasionListenerInstaller.installIfPresent(sectorWithoutManager))
                    .doesNotThrowAnyException());
        }

        @Test
        void ignoresANullSector() {

            ModStateScopes.runWithModEnabled(NEXERELIN, true, () ->
                assertThatCode(() -> NexerelinInvasionListenerInstaller.installIfPresent(null))
                    .doesNotThrowAnyException());
        }

        @Test
        void logsARelayThatNoLongerLinksRatherThanFailingTheLoad() {

            doThrow(MISSING_INTERFACE)
                .when(listenerManagerMock).addListener(any(), eq(true));

            var capturedLog = LogAppenderFake.captureLogOf(
                KmuWiringSteps.class,
                () -> ModStateScopes.runWithModEnabled(NEXERELIN, true, () ->
                    assertThatCode(() -> NexerelinInvasionListenerInstaller.installIfPresent(sectorMock))
                        .doesNotThrowAnyException()));

            assertThat(capturedLog.getMessages())
                .anySatisfy(message -> assertThat(message)
                    .startsWith("Failed to install the KMU relay for Nexerelin's colony transfers"));
        }
    }

    @Nested
    class UninstallIfPresent {

        @Test
        void removesTheRelayWhenNexerelinIsEnabled() {

            ModStateScopes.runWithModEnabled(NEXERELIN, true, () ->
                NexerelinInvasionListenerInstaller.uninstallIfPresent(sectorMock));

            verify(listenerManagerMock)
                .removeListenerOfClass(NexerelinMarketTransferRelay.class);
        }

        @Test
        void removesNothingWhenNexerelinIsDisabled() {
            // Naming the relay's class to remove it would load it, and with it the Nexerelin
            // interface it implements - which an install without Nexerelin does not have.
            ModStateScopes.runWithModEnabled(NEXERELIN, false, () ->
                NexerelinInvasionListenerInstaller.uninstallIfPresent(sectorMock));

            verifyNoInteractions(listenerManagerMock);
        }

        @Test
        void removesNothingFromASectorWithoutAListenerManager() {

            var sectorWithoutManager = buildSectorWith(null);

            ModStateScopes.runWithModEnabled(NEXERELIN, true, () ->
                assertThatCode(() -> NexerelinInvasionListenerInstaller.uninstallIfPresent(sectorWithoutManager))
                    .doesNotThrowAnyException());
        }

        @Test
        void ignoresANullSector() {

            ModStateScopes.runWithModEnabled(NEXERELIN, true, () ->
                assertThatCode(() -> NexerelinInvasionListenerInstaller.uninstallIfPresent(null))
                    .doesNotThrowAnyException());
        }

        @Test
        void logsARelayThatNoLongerLinksRatherThanFailingTheSwitch() {

            doThrow(MISSING_INTERFACE)
                .when(listenerManagerMock).removeListenerOfClass(any());

            var capturedLog = LogAppenderFake.captureLogOf(
                KmuWiringSteps.class,
                () -> ModStateScopes.runWithModEnabled(NEXERELIN, true, () ->
                    assertThatCode(() -> NexerelinInvasionListenerInstaller.uninstallIfPresent(sectorMock))
                        .doesNotThrowAnyException()));

            assertThat(capturedLog.getMessages())
                .anySatisfy(message -> assertThat(message)
                    .startsWith("Failed to remove the KMU relay for Nexerelin's colony transfers"));
        }
    }

    private static SectorAPI buildSectorWith(ListenerManagerAPI listenerManager) {

        var sectorMock = mock(SectorAPI.class);

        when(sectorMock.getListenerManager())
            .thenReturn(listenerManager);

        return sectorMock;
    }
}
