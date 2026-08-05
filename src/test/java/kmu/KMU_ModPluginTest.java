package kmu;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;

import kmlib.starsector.ui.map.icons.MapIconReseater;

import kmu.maplayers.base.refresh.MapLayerSectorWatcher;
import kmu.maplayers.base.refresh.MovingSystems;
import kmu.maplayers.base.render.MapLayerTerrainInstaller;
import kmu.maplayers.base.sidebar.runtime.SidebarInput;
import kmu.maplayers.base.sidebar.runtime.SidebarRenderer;
import kmu.maplayers.base.tooltip.HoverTooltipDetailModeInput;
import kmu.maplayers.base.tooltip.MapLayerCellTooltip;
import kmu.ui.context.StarsectorMarketUiContextTracker;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class KMU_ModPluginTest {

    @Nested
    class ModIdentity {

        @Test
        void extendsStarsectorBaseModPlugin() {
            assertThat(new KMU_ModPlugin())
                .isInstanceOf(BaseModPlugin.class);
        }
    }

    @Nested
    class ConfigureXStream {

        @Test
        void handsTheEngineXStreamToTheTerrainPluginsAliasLineage() {
            // What the aliases are is pinned where they live; what is pinned here is that the mod
            // plugin still hands them the instance the engine supplies. Drop this call and every
            // save written under a former plugin name stops loading, with no other test failing.
            //
            // XStream is fully qualified for the reason the production call site fully qualifies it:
            // com.thoughtworks belongs to no import group the checkstyle order recognises. The
            // instance is a stand-in because constructing a real one fails outright on a modern JVM,
            // its TreeMapConverter reflecting into java.util internals that are no longer open.
            try (var installerStaticMock = mockStatic(MapLayerTerrainInstaller.class)) {
                        
                var xstreamMock = mock(com.thoughtworks.xstream.XStream.class);

                new KMU_ModPlugin().configureXStream(xstreamMock);

                installerStaticMock.verify(
                    () -> MapLayerTerrainInstaller.registerSaveAliases(xstreamMock));
            }
        }
    }

    @Nested
    class InstallMarketUiContextTracker {

        @Test
        void installsMarketUiContextTrackerWhenMissing() {

            var listenerManager = new RecordingListenerManager(false);

            KMU_ModPlugin.installMarketUiContextTracker(buildSector(listenerManager));

            assertThat(listenerManager.addedListeners)
                .singleElement()
                .isInstanceOf(StarsectorMarketUiContextTracker.class);

            assertThat(listenerManager.addedTransientFlags)
                .containsExactly(true);
        }

        @Test
        void doesNotInstallDuplicateMarketUiContextTracker() {

            var listenerManager = new RecordingListenerManager(true);

            KMU_ModPlugin.installMarketUiContextTracker(buildSector(listenerManager));

            assertThat(listenerManager.addedListeners)
                .isEmpty();
        }
    }

    @Nested
    class InstallPoliticalMapSidebar {

        @Test
        void reinstallsEverySidebarListenerFreshAsTransient() {

            var listenerManager = new RecordingListenerManager(false);

            KMU_ModPlugin.installPoliticalMapSidebar(buildSector(listenerManager));

            // Remove-then-add the sidebar's render and input listeners: one remove per class clears any
            // registration an older save carried, then a fresh render+input instance is added for each of
            // the two hosts (sector map and intel screen) transiently, so none enters the save and exactly
            // one of each renders per screen.
            assertThat(listenerManager.removedListenerClasses)
                .containsExactly(SidebarRenderer.class, SidebarInput.class);

            assertThat(listenerManager.addedListeners)
                .hasSize(4)
                .hasAtLeastOneElementOfType(SidebarRenderer.class)
                .hasAtLeastOneElementOfType(SidebarInput.class);

            assertThat(listenerManager.addedTransientFlags)
                .containsExactly(true, true, true, true);
        }

        @Test
        void toleratesAMissingListenerManager() {
            var sidebarInstallOnNullManager = (Runnable) () ->
                KMU_ModPlugin.installPoliticalMapSidebar(buildSector(null));

            assertThatCode(sidebarInstallOnNullManager::run)
                .doesNotThrowAnyException();
        }
    }

    @Nested
    class InstallMapLayerHoverTooltip {

        @Test
        void reinstallsTheHoverTooltipDispatcherFreshAsTransient() {
            // Remove-then-add, transient: the dispatcher caches GL text, which must never enter a save,
            // and a registration an older save carried has to be cleared or two would draw the same box.
            var listenerManager = new RecordingListenerManager(false);

            KMU_ModPlugin.installMapLayerHoverTooltip(buildSector(listenerManager));

            assertThat(listenerManager.removedListenerClasses)
                .containsExactly(MapLayerCellTooltip.class);

            assertThat(listenerManager.addedListeners)
                .singleElement()
                .isInstanceOf(MapLayerCellTooltip.class);

            assertThat(listenerManager.addedTransientFlags)
                .containsExactly(true);
        }

        @Test
        void toleratesAMissingListenerManager() {
            var tooltipInstallOnNullManager = (Runnable) () ->
                KMU_ModPlugin.installMapLayerHoverTooltip(buildSector(null));

            assertThatCode(tooltipInstallOnNullManager::run)
                .doesNotThrowAnyException();
        }
    }

    @Nested
    class InstallHoverTooltipDetailModeInput {

        @Test
        void reinstallsTheDetailModeInputListenerFreshAsTransient() {
            // Remove-then-add, transient: the toggle is a live view preference that enters no save,
            // and a registration an older save carried would flip the mode twice per press - leaving
            // it exactly where it started, so the key would look dead.
            var listenerManager = new RecordingListenerManager(false);

            KMU_ModPlugin.installHoverTooltipDetailModeInput(buildSector(listenerManager));

            assertThat(listenerManager.removedListenerClasses)
                .containsExactly(HoverTooltipDetailModeInput.class);

            assertThat(listenerManager.addedListeners)
                .singleElement()
                .isInstanceOf(HoverTooltipDetailModeInput.class);

            assertThat(listenerManager.addedTransientFlags)
                .containsExactly(true);
        }

        @Test
        void toleratesAMissingListenerManager() {

            var detailModeInputInstallOnNullManager = (Runnable) () ->
                KMU_ModPlugin.installHoverTooltipDetailModeInput(buildSector(null));

            assertThatCode(detailModeInputInstallOnNullManager::run)
                .doesNotThrowAnyException();
        }
    }

    @Nested
    class InstallStarscapeTerrainReseater {

        @Test
        void installsTheReseaterAsATransientScript() {
            // addTransientScript, never addScript: this script takes the starscape terrain out of
            // hyperspace for one advance, so one restored from a save alongside the one added on
            // load would have two latches racing to move and put back the same entity - and would
            // bake a library class's name into the file.
            var sectorMock = mock(SectorAPI.class);

            KMU_ModPlugin.installStarscapeTerrainReseater(sectorMock);

            verify(sectorMock)
                .addTransientScript(any(MapIconReseater.class));
            verify(sectorMock, never())
                .addScript(any());
        }

        @Test
        void installsAFreshReseaterPerLoadSoTheFirstMapOpenIsStillReseated() {
            // The latch arms on the edge into "a starscape map is showing", so a script carried
            // across loads would come back believing that edge had already passed and skip the
            // reseat the newly loaded sector's first map open is owed.
            var firstLoadSectorMock = mock(SectorAPI.class);
            var secondLoadSectorMock = mock(SectorAPI.class);

            KMU_ModPlugin.installStarscapeTerrainReseater(firstLoadSectorMock);
            KMU_ModPlugin.installStarscapeTerrainReseater(secondLoadSectorMock);

            var firstReseater = ArgumentCaptor.forClass(MapIconReseater.class);
            var secondReseater = ArgumentCaptor.forClass(MapIconReseater.class);

            verify(firstLoadSectorMock)
                .addTransientScript(firstReseater.capture());
            verify(secondLoadSectorMock)
                .addTransientScript(secondReseater.capture());

            assertThat(secondReseater.getValue())
                .isNotSameAs(firstReseater.getValue());
        }

        @Test
        void toleratesANullSector() {

            var reseaterInstallOnNullSector = (Runnable) () ->
                KMU_ModPlugin.installStarscapeTerrainReseater(null);

            assertThatCode(reseaterInstallOnNullSector::run)
                .doesNotThrowAnyException();
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

            KMU_ModPlugin.installMapLayerSectorWatcher(sectorMock);

            verify(sectorMock)
                .addTransientScript(any(MapLayerSectorWatcher.class));
            verify(sectorMock, never())
                .addScript(any());
        }

        @Test
        void installsAFreshWatcherPerLoadSoItsBaselinesStartEmpty() {
            // The watcher's staleness source diffs each poll against its own last read, so a
            // source carried across loads would compare the newly loaded sector against the
            // one just left and mark every system that differs stale on the first poll.
            var firstLoadSectorMock = mock(SectorAPI.class);
            var secondLoadSectorMock = mock(SectorAPI.class);

            KMU_ModPlugin.installMapLayerSectorWatcher(firstLoadSectorMock);
            KMU_ModPlugin.installMapLayerSectorWatcher(secondLoadSectorMock);

            var firstWatcher = ArgumentCaptor.forClass(MapLayerSectorWatcher.class);
            var secondWatcher = ArgumentCaptor.forClass(MapLayerSectorWatcher.class);

            verify(firstLoadSectorMock)
                .addTransientScript(firstWatcher.capture());
            verify(secondLoadSectorMock)
                .addTransientScript(secondWatcher.capture());

            assertThat(secondWatcher.getValue())
                .isNotSameAs(firstWatcher.getValue());
        }

        @Test
        void flushesTheSharedMovingTrackerBeforeInstalling() {
            // The tracker is a process-lifetime singleton, so without this flush a system id
            // reused by the next save is measured against the previous save's last-seen
            // position and reads as having teleported.
            try (var movingStaticMock = mockStatic(MovingSystems.class)) {

                var movingSystemsMock = mock(MovingSystems.class);

                movingStaticMock
                    .when(MovingSystems::getInstance)
                    .thenReturn(movingSystemsMock);

                KMU_ModPlugin.installMapLayerSectorWatcher(mock(SectorAPI.class));

                verify(movingSystemsMock)
                    .reset();
            }
        }

        @Test
        void toleratesANullSector() {
            var watcherInstallOnNullSector = (Runnable) () ->
                KMU_ModPlugin.installMapLayerSectorWatcher(null);

            assertThatCode(watcherInstallOnNullSector::run)
                .doesNotThrowAnyException();
        }
    }

    private static SectorAPI buildSector(ListenerManagerAPI listenerManager) {
        return proxy(SectorAPI.class, (proxy, method, args) -> {
            if (method.getName().equals("getListenerManager")) {
                return listenerManager;
            }
            return handleObjectMethodOrThrow(proxy, method, args);
        });
    }

    private static Object handleObjectMethodOrThrow(Object proxy, Method method, Object[] args) {
        if (method.getDeclaringClass().equals(Object.class)) {
            switch (method.getName()) {
                case "toString":
                    return proxy.getClass().getInterfaces()[0].getSimpleName() + "Proxy";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    break;
            }
        }
        throw new UnsupportedOperationException(method.toString());
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[]{type},
            handler);
    }

    private static final class RecordingListenerManager implements ListenerManagerAPI {

        private final boolean hasTracker;
        private final List<Object> addedListeners = new ArrayList<>();

        // Parallel to addedListeners: the engine's second addListener parameter means
        // transient (true keeps the listener out of the save), so the recording mirrors that
        // vocabulary. One install can add several listeners, so these are lists, not scalars.
        private final List<Boolean> addedTransientFlags = new ArrayList<>();
        private final List<Class<?>> removedListenerClasses = new ArrayList<>();

        private RecordingListenerManager(boolean hasTracker) {
            this.hasTracker = hasTracker;
        }

        @Override
        public void addListener(Object listener) {
            addedListeners.add(listener);
            addedTransientFlags.add(false);
        }

        @Override
        public void addListener(Object listener, boolean isTransient) {
            addedListeners.add(listener);
            addedTransientFlags.add(isTransient);
        }

        @Override
        public void removeListener(Object listener) {
        }

        @Override
        public void removeListenerOfClass(Class<?> listenerClass) {
            removedListenerClasses.add(listenerClass);
        }

        @Override
        public boolean hasListener(Object listener) {
            return false;
        }

        @Override
        public boolean hasListenerOfClass(Class<?> listenerClass) {
            return hasTracker && listenerClass.equals(StarsectorMarketUiContextTracker.class);
        }

        @Override
        public <T> List<T> getListeners(Class<T> listenerClass) {
            return List.of();
        }
    }
}
