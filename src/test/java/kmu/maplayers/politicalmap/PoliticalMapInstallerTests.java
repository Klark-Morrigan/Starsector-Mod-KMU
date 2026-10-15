package kmu.maplayers.politicalmap;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.factions.alliances.AllianceSource;
import kmlib.testfixtures.starsector.listeners.RecordingListenerManager;

import kmu.maplayers.base.machinery.SectorMapMachineryIndex;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.base.refresh.MapLayerSectorWatcher;
import kmu.maplayers.base.refresh.MapSubstrateSectorWatcher;
import kmu.maplayers.ownermap.FilterSelectionHeal;
import kmu.maplayers.ownermap.MapLayerViewRegistry;
import kmu.maplayers.politicalmap.refresh.PoliticalMapSectorWatcher;
import kmu.maplayers.politicalmap.refresh.listeners.PoliticalMapColonisationListener;
import kmu.maplayers.politicalmap.refresh.listeners.PoliticalMapColonySizeListener;
import kmu.maplayers.politicalmap.refresh.listeners.PoliticalMapDecivListener;
import kmu.maplayers.politicalmap.refresh.listeners.PoliticalMapDiscoveryListener;
import kmu.maplayers.politicalmap.refresh.listeners.PoliticalMapMarketTransferListener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.function.Consumer;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.politicalmap.refresh.MarketRefreshFixtures
    .mockEntityWithMarketInSystem;
import static kmu.maplayers.politicalmap.refresh.MarketRefreshFixtures.mockMarketInSystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the save heal running ahead of every registration, each step of the install surviving another's
 * failure, the political map's per-frame watcher as a transient script built fresh per load, and the
 * taking back of every live repaint when the layers are switched off.
 *
 * <p>And, for each listener install, that the listener is built against the sector it is being
 * installed on. Every one of them reports onto that sector's own refresh board, so an installer
 * handing over anything else - or nothing - would leave the listener marking whichever sector the
 * player happens to have loaded. The listener suites cannot see that: each builds its own listener,
 * so the one thing they never exercise is the argument the installer passes.
 *
 * <p>Read by driving the registered listener and looking for the mark on the installed sector's
 * board, rather than by reaching into the listener for the sector it kept. What the wiring is for
 * is where the mark lands, and a listener that held the right sector and marked elsewhere would
 * pass an assertion about the field.
 */
class PoliticalMapInstallerTests {

    @Nested
    class InstallAll {

        private final RecordingListenerManager listenerManager = new RecordingListenerManager();
        private final SectorAPI sectorMock = mock(SectorAPI.class);
        private final MapLayerViewRegistry viewRegistryMock = mock(MapLayerViewRegistry.class);

        @BeforeEach
        void answerTheListenerManager() {
            when(sectorMock.getListenerManager())
                .thenReturn(listenerManager);
        }

        @Test
        void healsTheSpotlightBeforeAnythingIsRegistered() {
            // The terrain reads the spotlight the heal repairs, so a repaint registered ahead of it
            // could act on a selection the active view does not offer. Counted at the moment the heal
            // runs: nothing may have touched the sector yet.
            var sectorCallCountsSeenByTheHeal = new ArrayList<Integer>();

            try (var healMock = mockStatic(FilterSelectionHeal.class)) {

                healMock
                    .when(() -> FilterSelectionHeal.healStaleSelectionAgainstActiveView(
                        sectorMock,
                        viewRegistryMock))
                    .thenAnswer(call -> {

                        sectorCallCountsSeenByTheHeal.add(mockingDetails(sectorMock).getInvocations().size());
                        return null;
                    });

                PoliticalMapInstaller.installAll(sectorMock, viewRegistryMock, AllianceSource.NO_ALLIANCES);
            }

            assertThat(sectorCallCountsSeenByTheHeal)
                .containsExactly(0);
            assertThat(listenerManager.getAddedListeners())
                .hasSize(5);
        }

        @Test
        void installsEveryRepaintPastAHealThatFailed() {
            // The heal reads the save, which is the step most likely to meet something it did not
            // expect. A spotlight left dangling is a smaller loss than a map that stops updating live.
            try (var healMock = mockStatic(FilterSelectionHeal.class)) {

                healMock
                    .when(() -> FilterSelectionHeal.healStaleSelectionAgainstActiveView(any(), any()))
                    .thenThrow(new IllegalStateException("A save the heal cannot read"));

                PoliticalMapInstaller.installAll(sectorMock, viewRegistryMock, AllianceSource.NO_ALLIANCES);
            }

            assertThat(listenerManager.getAddedListeners())
                .extracting(Object::getClass)
                .containsExactly(
                    PoliticalMapDiscoveryListener.class,
                    PoliticalMapColonySizeListener.class,
                    PoliticalMapDecivListener.class,
                    PoliticalMapColonisationListener.class,
                    PoliticalMapMarketTransferListener.class);

            verify(sectorMock)
                .addTransientScript(any(PoliticalMapSectorWatcher.class));
        }

        @Test
        void installsTheListenersOnEitherSideOfAWatcherThatFailed() {
            // The watcher sits between the first listener and the other four, so a failure there that
            // escaped its own step would leave the map deaf to every colony that resizes, decivilises,
            // is founded or changes hands.
            doThrow(new IllegalStateException("A sector refusing the script"))
                .when(sectorMock)
                .addTransientScript(any());

            try (var healMock = mockStatic(FilterSelectionHeal.class)) {

                PoliticalMapInstaller.installAll(sectorMock, viewRegistryMock, AllianceSource.NO_ALLIANCES);
            }

            assertThat(listenerManager.getAddedListeners())
                .extracting(Object::getClass)
                .containsExactly(
                    PoliticalMapDiscoveryListener.class,
                    PoliticalMapColonySizeListener.class,
                    PoliticalMapDecivListener.class,
                    PoliticalMapColonisationListener.class,
                    PoliticalMapMarketTransferListener.class);
        }
    }

    @Nested
    class InstallMapLayerSectorWatcher {

        @Test
        void installsTheWatcherAsATransientScript() {
            // addTransientScript, never addScript: an EveryFrameScript that entered the save
            // would be restored alongside the one added on load, so every reload would stack
            // another poller - and would bake this class's name into the file.
            var sectorMock = mock(SectorAPI.class);

            PoliticalMapInstaller.installMapLayerSectorWatcher(sectorMock, AllianceSource.NO_ALLIANCES);

            verify(sectorMock)
                .addTransientScript(any(PoliticalMapSectorWatcher.class));
            verify(sectorMock, never())
                .addScript(any());
        }

        @Test
        void clearsOnlyThisLayersOwnWatcherClass() {
            // The engine clears transient scripts by exact class, so clearing under the framework's
            // shared watcher class would take every other layer's poll down with this one's.
            var sectorMock = mock(SectorAPI.class);

            PoliticalMapInstaller.installMapLayerSectorWatcher(sectorMock, AllianceSource.NO_ALLIANCES);

            verify(sectorMock)
                .removeTransientScriptsOfClass(PoliticalMapSectorWatcher.class);
            verify(sectorMock, never())
                .removeTransientScriptsOfClass(MapLayerSectorWatcher.class);
        }

        @Test
        void installsAFreshWatcherPerLoadSoItsBaselinesStartEmpty() {
            // The watcher's staleness source diffs each poll against its own last read, so a
            // source carried across loads would compare the newly loaded sector against the
            // one just left and mark every system that differs stale on the first poll.
            var firstLoadSectorMock = mock(SectorAPI.class);
            var secondLoadSectorMock = mock(SectorAPI.class);

            PoliticalMapInstaller.installMapLayerSectorWatcher(firstLoadSectorMock, AllianceSource.NO_ALLIANCES);
            PoliticalMapInstaller.installMapLayerSectorWatcher(secondLoadSectorMock, AllianceSource.NO_ALLIANCES);

            var firstWatcher = ArgumentCaptor.forClass(PoliticalMapSectorWatcher.class);
            var secondWatcher = ArgumentCaptor.forClass(PoliticalMapSectorWatcher.class);

            verify(firstLoadSectorMock)
                .addTransientScript(firstWatcher.capture());
            verify(secondLoadSectorMock)
                .addTransientScript(secondWatcher.capture());

            assertThat(secondWatcher.getValue())
                .isNotSameAs(firstWatcher.getValue());
        }

        @Test
        void toleratesANullSector() {

            var watcherInstallOnNullSector = (Runnable) () ->
                PoliticalMapInstaller.installMapLayerSectorWatcher(null, AllianceSource.NO_ALLIANCES);

            assertThatCode(watcherInstallOnNullSector::run)
                .doesNotThrowAnyException();
        }
    }

    @Nested
    class InstallPoliticalMapDiscoveryListener {

        @Test
        void buildsTheListenerAgainstTheSectorItIsInstalledOn() {

            var installed = installListenerOn(
                PoliticalMapDiscoveryListener.class,
                PoliticalMapInstaller::installPoliticalMapDiscoveryListener);

            installed.listener().reportEntityDiscovered(mockEntityWithMarketInSystem("sys"));

            assertThat(installed.refreshBoard().drainStaleGroupingSystemKeys())
                .containsExactly(buildCellKey("sys"));
        }
    }

    @Nested
    class InstallPoliticalMapColonySizeListener {

        @Test
        void buildsTheListenerAgainstTheSectorItIsInstalledOn() {

            var installed = installListenerOn(
                PoliticalMapColonySizeListener.class,
                PoliticalMapInstaller::installPoliticalMapColonySizeListener);

            installed.listener().reportColonySizeChanged(mockMarketInSystem("sys"), 3);

            assertThat(installed.refreshBoard().drainStaleGroupingSystemKeys())
                .containsExactly(buildCellKey("sys"));
        }
    }

    @Nested
    class InstallPoliticalMapDecivListener {

        @Test
        void buildsTheListenerAgainstTheSectorItIsInstalledOn() {

            var installed = installListenerOn(
                PoliticalMapDecivListener.class,
                PoliticalMapInstaller::installPoliticalMapDecivListener);

            installed.listener().reportColonyDecivilized(mockMarketInSystem("sys"), false);

            assertThat(installed.refreshBoard().drainStaleGroupingSystemKeys())
                .containsExactly(buildCellKey("sys"));
        }
    }

    @Nested
    class InstallPoliticalMapColonisationListener {

        @Test
        void buildsTheListenerAgainstTheSectorItIsInstalledOn() {

            var installed = installListenerOn(
                PoliticalMapColonisationListener.class,
                PoliticalMapInstaller::installPoliticalMapColonisationListener);

            // Abandonment rather than founding, since it names the market directly: the founding
            // callback carries a planet the market has to be hung on first, and which of the two
            // provoked the mark is not what this case is about.
            installed.listener().reportPlayerAbandonedColony(mockMarketInSystem("sys"));

            assertThat(installed.refreshBoard().drainStaleGroupingSystemKeys())
                .containsExactly(buildCellKey("sys"));
        }
    }

    @Nested
    class InstallPoliticalMapMarketTransferListener {

        @Test
        void buildsTheListenerAgainstTheSectorItIsInstalledOn() {

            var installed = installListenerOn(
                PoliticalMapMarketTransferListener.class,
                PoliticalMapInstaller::installPoliticalMapMarketTransferListener);

            installed.listener().reportMarketTransferred(mockMarketInSystem("sys"), null, null, true);

            assertThat(installed.refreshBoard().drainStaleGroupingSystemKeys())
                .containsExactly(buildCellKey("sys"));
        }
    }

    @Nested
    class UninstallAll {

        @Test
        void clearsEveryLiveRepaintItRegisters() {
            // All five listeners and the watcher, since each would otherwise go on marking an
            // overlay stale that nothing is drawing - work paid for a feature switched off.
            var listenerManager = new RecordingListenerManager();
            var sectorMock = mock(SectorAPI.class);

            when(sectorMock.getListenerManager())
                .thenReturn(listenerManager);

            PoliticalMapInstaller.uninstallAll(sectorMock);

            assertThat(listenerManager.getRemovedListenerClasses())
                .containsExactly(
                    PoliticalMapDiscoveryListener.class,
                    PoliticalMapColonySizeListener.class,
                    PoliticalMapDecivListener.class,
                    PoliticalMapColonisationListener.class,
                    PoliticalMapMarketTransferListener.class);

            assertThat(listenerManager.getAddedListeners())
                .isEmpty();

            verify(sectorMock)
                .removeTransientScriptsOfClass(PoliticalMapSectorWatcher.class);
        }

        @Test
        void leavesTheSubstratesSweepStanding() {
            // What this layer takes back is its own. The sweep that records what a system's
            // inhabitants can see keeps a register several map families read, so a layer switching
            // off must not stop it accruing - the gap would surface cycles later, as a colony
            // dropping off a map the player had since put back.
            var sectorMock = mock(SectorAPI.class);

            when(sectorMock.getListenerManager())
                .thenReturn(new RecordingListenerManager());

            PoliticalMapInstaller.uninstallAll(sectorMock);

            verify(sectorMock, never())
                .removeTransientScriptsOfClass(MapSubstrateSectorWatcher.class);
        }

        @Test
        void toleratesANullSector() {

            var politicalMapUninstallOnNullSector = (Runnable) () ->
                PoliticalMapInstaller.uninstallAll(null);

            assertThatCode(politicalMapUninstallOnNullSector::run)
                .doesNotThrowAnyException();
        }
    }

    @Nested
    class RemoveMapLayerSectorWatcher {

        @Test
        void stopsTheWatcherByClassSinceThisLayerOwnsIt() {
            // By class rather than by instance, which is safe only because the class is this
            // layer's own: no other layer's poll, and no sibling mod's, runs under it.
            var sectorMock = mock(SectorAPI.class);

            PoliticalMapInstaller.removeMapLayerSectorWatcher(sectorMock);

            verify(sectorMock)
                .removeTransientScriptsOfClass(PoliticalMapSectorWatcher.class);
        }

        @Test
        void toleratesANullSector() {

            var watcherRemovalOnNullSector = (Runnable) () ->
                PoliticalMapInstaller.removeMapLayerSectorWatcher(null);

            assertThatCode(watcherRemovalOnNullSector::run)
                .doesNotThrowAnyException();
        }
    }

    // Stands one listener up the way a load does - on a sector that has the map machinery
    // installed - and hands back the registered listener beside the board it is supposed to report
    // on.
    //
    // The sector itself is deliberately not handed back. What a case reads is whether the mark
    // found its way to that sector's board, and a case holding the sector could reach the board a
    // second way and assert against whichever one the production happened to pick.
    private static <T> InstalledListener<T> installListenerOn(
            Class<T> listenerClass,
            Consumer<SectorAPI> installListener) {

        var listenerManager = new RecordingListenerManager();
        var sectorMock = mock(SectorAPI.class);

        when(sectorMock.getListenerManager())
            .thenReturn(listenerManager);

        var refreshBoard = SectorMapMachineryIndex
            .installMachineryOn(sectorMock)
            .resolveRefreshBoard();

        installListener.accept(sectorMock);

        return new InstalledListener<>(
            listenerClass.cast(listenerManager.getAddedListeners().get(0)),
            refreshBoard);
    }

    // One installed listener and the board of the sector it went onto, which is the pair every
    // case here drives: the event goes into the first and the mark is looked for in the second.
    private record InstalledListener<T>(T listener, MapLayerRefreshBoard refreshBoard) {
    }
}
