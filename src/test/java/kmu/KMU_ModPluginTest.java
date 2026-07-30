package kmu;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainPlugin;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;

import kmu.maplayers.base.refresh.MovingSystems;
import kmu.maplayers.base.refresh.PoliticalMapSectorWatcher;
import kmu.maplayers.base.render.SectorMapLayerStarscapeTerrainPlugin;
import kmu.maplayers.base.render.SectorMapLayerTerrainPlugin;
import kmu.maplayers.base.sidebar.runtime.SidebarInput;
import kmu.maplayers.base.sidebar.runtime.SidebarRenderer;
import kmu.maplayers.base.tooltip.MapLayerCellTooltip;
import kmu.ui.context.StarsectorMarketUiContextTracker;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KMU_ModPluginTest {
    // The live terrain type id, pinned as a literal: it is written into every save, so a rename
    // must break this test rather than ship and quietly strand the entity existing saves hold.
    private static final String CURRENT_TERRAIN_TYPE = "kmu_sector_map_layer_terrain";

    // The id shipped saves were written under before the rename. Pinned for the same reason from
    // the other side: this is the string the load-time sweep has to recognise as stale.
    private static final String LEGACY_TERRAIN_TYPE = "kmu_political_terrain";

    // Every fully-qualified name the terrain plugin has been saved under. Restated here as literals
    // rather than read from the production list: a constant read from the class under test would be
    // renamed alongside it and go on agreeing with itself, while these are what a save file on disk
    // actually holds and so cannot be allowed to move.
    private static final List<String> FORMER_TERRAIN_PLUGIN_CLASSES = List.of(
            "kmu.politicalmap.render.PoliticalMapTerrainPlugin",
            "kmu.politicalmap.render.FactionsPoliticalMapTerrainPlugin",
            "kmu.maplayers.politicalmap.factions.render.FactionsPoliticalMapTerrainPlugin",
            "kmu.maplayers.politicalmap.base.render.PoliticalMapTerrainPlugin");

    @Nested
    class ModIdentity {

        @Test
        void exposesStableModIdentityConstants() {
            assertThat(KMU_ModPlugin.MOD_ID).isEqualTo("kmu");
            assertThat(KMU_ModPlugin.MOD_NAME).isEqualTo("Klark Morrigan's Utilities");
        }

        @Test
        void extendsStarsectorBaseModPlugin() {
            assertThat(new KMU_ModPlugin()).isInstanceOf(BaseModPlugin.class);
        }
    }

    // What the mod registers on the engine's XStream is checked against a stand-in rather than a
    // real one: constructing an XStream fails outright on a modern JVM, its TreeMapConverter
    // reflecting into java.util internals that are no longer open, and the game supplies the
    // instance anyway - the mod only ever configures one it is handed. XStream is fully qualified
    // for the reason the production call site fully qualifies it: com.thoughtworks belongs to no
    // import group the checkstyle order recognises.
    @Nested
    class ConfigureXStream {

        @Test
        void configureXStreamAliasesEveryFormerTerrainPluginNameToTheLiveClass() {
            // Each of these is a class name a shipped save may still hold. Without its alias the
            // save does not load at all - XStream fails the whole read with
            // CannotResolveClassException - so an alias dropped by a later edit is a save-breaking
            // regression that nothing else would catch until a player reported it.
            var xstreamMock = mock(com.thoughtworks.xstream.XStream.class);

            new KMU_ModPlugin().configureXStream(xstreamMock);

            for (var formerClass : FORMER_TERRAIN_PLUGIN_CLASSES) {
                verify(xstreamMock).alias(formerClass, SectorMapLayerTerrainPlugin.class);
            }
        }

        @Test
        void configureXStreamAliasesTheLiveClassNameLastSoResavedGamesShedTheFormerNames() {
            // Order is the whole contract here: XStream keeps one name per class for writing, so
            // whichever alias is registered last decides what a re-saved game is written under.
            // Registered after the former names, the self-alias means a save sheds them; registered
            // before, every re-save would silently pin a dead class name back into the file.
            var xstreamMock = mock(com.thoughtworks.xstream.XStream.class);

            new KMU_ModPlugin().configureXStream(xstreamMock);

            var aliasOrder = inOrder(xstreamMock);
            aliasOrder.verify(xstreamMock).alias(
                    FORMER_TERRAIN_PLUGIN_CLASSES.get(FORMER_TERRAIN_PLUGIN_CLASSES.size() - 1),
                    SectorMapLayerTerrainPlugin.class);
            aliasOrder.verify(xstreamMock).alias(
                    SectorMapLayerTerrainPlugin.class.getName(), SectorMapLayerTerrainPlugin.class);
        }
    }

    @Nested
    class InstallMarketUiContextTracker {

        @Test
        void installsMarketUiContextTrackerWhenMissing() {
            var listenerManager = new RecordingListenerManager(false);

            KMU_ModPlugin.installMarketUiContextTracker(sector(listenerManager));

            assertThat(listenerManager.addedListeners)
                    .singleElement()
                    .isInstanceOf(StarsectorMarketUiContextTracker.class);
            assertThat(listenerManager.addedTransientFlags).containsExactly(true);
        }

        @Test
        void doesNotInstallDuplicateMarketUiContextTracker() {
            var listenerManager = new RecordingListenerManager(true);

            KMU_ModPlugin.installMarketUiContextTracker(sector(listenerManager));

            assertThat(listenerManager.addedListeners).isEmpty();
        }
    }

    @Nested
    class InstallPoliticalMapSidebar {

        @Test
        void reinstallsEverySidebarListenerFreshAsTransient() {
            var listenerManager = new RecordingListenerManager(false);

            KMU_ModPlugin.installPoliticalMapSidebar(sector(listenerManager));

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
            assertThat(listenerManager.addedTransientFlags).containsExactly(true, true, true, true);
        }

        @Test
        void toleratesAMissingListenerManager() {
            var sidebarInstallOnNullManager = (Runnable) () ->
                    KMU_ModPlugin.installPoliticalMapSidebar(sector(null));

            assertThatCode(sidebarInstallOnNullManager::run).doesNotThrowAnyException();
        }
    }

    @Nested
    class InstallMapLayerHoverTooltip {

        @Test
        void reinstallsTheHoverTooltipDispatcherFreshAsTransient() {
            // Remove-then-add, transient: the dispatcher caches GL text, which must never enter a save,
            // and a registration an older save carried has to be cleared or two would draw the same box.
            var listenerManager = new RecordingListenerManager(false);

            KMU_ModPlugin.installMapLayerHoverTooltip(sector(listenerManager));

            assertThat(listenerManager.removedListenerClasses)
                    .containsExactly(MapLayerCellTooltip.class);
            assertThat(listenerManager.addedListeners)
                    .singleElement()
                    .isInstanceOf(MapLayerCellTooltip.class);
            assertThat(listenerManager.addedTransientFlags).containsExactly(true);
        }

        @Test
        void toleratesAMissingListenerManager() {
            var tooltipInstallOnNullManager = (Runnable) () ->
                    KMU_ModPlugin.installMapLayerHoverTooltip(sector(null));

            assertThatCode(tooltipInstallOnNullManager::run).doesNotThrowAnyException();
        }
    }

    @Nested
    class InstallPoliticalMapSectorWatcher {

        @Test
        void installsTheWatcherAsATransientScript() {
            // addTransientScript, never addScript: an EveryFrameScript that entered the save
            // would be restored alongside the one added on load, so every reload would stack
            // another poller - and would bake this class's name into the file.
            var sectorMock = mock(SectorAPI.class);

            KMU_ModPlugin.installPoliticalMapSectorWatcher(sectorMock);

            verify(sectorMock).addTransientScript(any(PoliticalMapSectorWatcher.class));
            verify(sectorMock, never()).addScript(any());
        }

        @Test
        void installsAFreshWatcherPerLoadSoItsBaselinesStartEmpty() {
            // The watcher's staleness source diffs each poll against its own last read, so a
            // source carried across loads would compare the newly loaded sector against the
            // one just left and mark every system that differs stale on the first poll.
            var firstLoadSectorMock = mock(SectorAPI.class);
            var secondLoadSectorMock = mock(SectorAPI.class);

            KMU_ModPlugin.installPoliticalMapSectorWatcher(firstLoadSectorMock);
            KMU_ModPlugin.installPoliticalMapSectorWatcher(secondLoadSectorMock);

            var firstWatcher = ArgumentCaptor.forClass(PoliticalMapSectorWatcher.class);
            var secondWatcher = ArgumentCaptor.forClass(PoliticalMapSectorWatcher.class);
            verify(firstLoadSectorMock).addTransientScript(firstWatcher.capture());
            verify(secondLoadSectorMock).addTransientScript(secondWatcher.capture());
            assertThat(secondWatcher.getValue()).isNotSameAs(firstWatcher.getValue());
        }

        @Test
        void flushesTheSharedMovingTrackerBeforeInstalling() {
            // The tracker is a process-lifetime singleton, so without this flush a system id
            // reused by the next save is measured against the previous save's last-seen
            // position and reads as having teleported.
            try (MockedStatic<MovingSystems> movingStaticMock = mockStatic(MovingSystems.class)) {
                var movingSystemsMock = mock(MovingSystems.class);
                movingStaticMock.when(MovingSystems::getInstance).thenReturn(movingSystemsMock);

                KMU_ModPlugin.installPoliticalMapSectorWatcher(mock(SectorAPI.class));

                verify(movingSystemsMock).reset();
            }
        }

        @Test
        void toleratesANullSector() {
            var watcherInstallOnNullSector = (Runnable) () ->
                    KMU_ModPlugin.installPoliticalMapSectorWatcher(null);

            assertThatCode(watcherInstallOnNullSector::run).doesNotThrowAnyException();
        }
    }

    @Nested
    class InstallSectorMapLayerTerrain {

        @Test
        void installsTheTerrainWhenHyperspaceCarriesNone() {
            var hyperspaceMock = hyperspaceCarrying();

            KMU_ModPlugin.installSectorMapLayerTerrain(sectorWithHyperspace(hyperspaceMock));

            verify(hyperspaceMock).addTerrain(CURRENT_TERRAIN_TYPE, null);
        }

        @Test
        void doesNotStackASecondTerrainOnAReloadedSave() {
            // Terrain persists, so a reloaded save already carries it; adding another would paint
            // the same overlay twice and double the alpha of every fill.
            var hyperspaceMock = hyperspaceCarrying(
                    terrainMock(CURRENT_TERRAIN_TYPE, new SectorMapLayerTerrainPlugin()));

            KMU_ModPlugin.installSectorMapLayerTerrain(sectorWithHyperspace(hyperspaceMock));

            verify(hyperspaceMock, never()).addTerrain(any(), any());
            verify(hyperspaceMock, never()).removeEntity(any());
        }

        @Test
        void retiresTerrainLeftUnderAFormerTypeIdAndInstallsTheCurrentOne() {
            // The type id is serialised, so a save written before the rename holds an entity under
            // the old id whose spec no longer resolves. It has to go, or the save ends up with the
            // stale entity plus the freshly added one.
            var staleTerrainMock = terrainMock(LEGACY_TERRAIN_TYPE, new SectorMapLayerTerrainPlugin());
            var hyperspaceMock = hyperspaceCarrying(staleTerrainMock);

            KMU_ModPlugin.installSectorMapLayerTerrain(sectorWithHyperspace(hyperspaceMock));

            verify(hyperspaceMock).removeEntity(staleTerrainMock);
            verify(hyperspaceMock).addTerrain(CURRENT_TERRAIN_TYPE, null);
        }

        @Test
        void leavesTerrainBelongingToAnotherModAlone() {
            // The sweep identifies its own by plugin class, so a third-party terrain is neither
            // retired nor counted as the map layer already being present.
            var otherModTerrainMock =
                    terrainMock("some_other_terrain", mock(CampaignTerrainPlugin.class));
            var hyperspaceMock = hyperspaceCarrying(otherModTerrainMock);

            KMU_ModPlugin.installSectorMapLayerTerrain(sectorWithHyperspace(hyperspaceMock));

            verify(hyperspaceMock, never()).removeEntity(any());
            verify(hyperspaceMock).addTerrain(CURRENT_TERRAIN_TYPE, null);
        }

        @Test
        void retiresTheStaleTerrainWithoutTouchingTheStarscapeHalf() {
            // The starscape plugin subclasses the base one, so an instanceof match here would let
            // this sweep retire the other half's entity - which reports a type id this one never
            // installs, and so looks stale to any test that is not exact about the class.
            var starscapeTerrainMock =
                    terrainMock("slipstream", new SectorMapLayerStarscapeTerrainPlugin());
            var hyperspaceMock = hyperspaceCarrying(starscapeTerrainMock);

            KMU_ModPlugin.installSectorMapLayerTerrain(sectorWithHyperspace(hyperspaceMock));

            verify(hyperspaceMock, never()).removeEntity(any());
        }
    }

    private static LocationAPI hyperspaceCarrying(CampaignTerrainAPI... terrain) {
        var hyperspaceMock = mock(LocationAPI.class);
        when(hyperspaceMock.getTerrainCopy()).thenReturn(List.of(terrain));
        return hyperspaceMock;
    }

    private static CampaignTerrainAPI terrainMock(String type, CampaignTerrainPlugin plugin) {
        var terrainMock = mock(CampaignTerrainAPI.class);
        when(terrainMock.getType()).thenReturn(type);
        when(terrainMock.getPlugin()).thenReturn(plugin);
        return terrainMock;
    }

    private static SectorAPI sectorWithHyperspace(LocationAPI hyperspace) {
        var sectorMock = mock(SectorAPI.class);
        when(sectorMock.getHyperspace()).thenReturn(hyperspace);
        return sectorMock;
    }

    private static SectorAPI sector(ListenerManagerAPI listenerManager) {
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
