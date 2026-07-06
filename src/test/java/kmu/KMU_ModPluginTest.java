package kmu;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;

import kmu.politicalmap.ui.PoliticalMapSidebar;
import kmu.politicalmap.ui.PoliticalMapSidebarInput;
import kmu.ui.context.StarsectorMarketUiContextTracker;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class KMU_ModPluginTest {

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
        void reinstallsBothBarListenersFreshAsTransient() {
            var listenerManager = new RecordingListenerManager(false);

            KMU_ModPlugin.installPoliticalMapSidebar(sector(listenerManager));

            // Remove-then-add each of the bar's two listeners - the render listener and its
            // input listener: clears any registration an older save carried, then adds the
            // fresh instances transiently so neither enters the save.
            assertThat(listenerManager.removedListenerClasses)
                    .containsExactly(PoliticalMapSidebar.class, PoliticalMapSidebarInput.class);
            assertThat(listenerManager.addedListeners)
                    .hasSize(2)
                    .hasAtLeastOneElementOfType(PoliticalMapSidebar.class)
                    .hasAtLeastOneElementOfType(PoliticalMapSidebarInput.class);
            assertThat(listenerManager.addedTransientFlags).containsExactly(true, true);
        }

        @Test
        void toleratesAMissingListenerManager() {
            var sidebarInstallOnNullManager = (Runnable) () ->
                    KMU_ModPlugin.installPoliticalMapSidebar(sector(null));

            assertThatCode(sidebarInstallOnNullManager::run).doesNotThrowAnyException();
        }
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
