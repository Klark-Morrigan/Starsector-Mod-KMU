package kmu;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import kmu.ui.context.StarsectorMarketUiContextTracker;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KMU_ModPluginTest {
    @Test
    void exposesStableModIdentityConstants() {
        assertThat(KMU_ModPlugin.MOD_ID).isEqualTo("klark_morrigans_utilities");
        assertThat(KMU_ModPlugin.MOD_NAME).isEqualTo("Klark Morrigan's Utilities");
    }

    @Test
    void extendsStarsectorBaseModPlugin() {
        assertThat(new KMU_ModPlugin()).isInstanceOf(BaseModPlugin.class);
    }

    @Test
    void installsMarketUiContextTrackerWhenMissing() {
        RecordingListenerManager listenerManager = new RecordingListenerManager(false);

        KMU_ModPlugin.installMarketUiContextTracker(sector(listenerManager));

        assertThat(listenerManager.addedListener).isInstanceOf(StarsectorMarketUiContextTracker.class);
        assertThat(listenerManager.addedAsPermanent).isTrue();
    }

    @Test
    void doesNotInstallDuplicateMarketUiContextTracker() {
        RecordingListenerManager listenerManager = new RecordingListenerManager(true);

        KMU_ModPlugin.installMarketUiContextTracker(sector(listenerManager));

        assertThat(listenerManager.addedListener).isNull();
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
        private Object addedListener;
        private boolean addedAsPermanent;

        private RecordingListenerManager(boolean hasTracker) {
            this.hasTracker = hasTracker;
        }

        @Override
        public void addListener(Object listener) {
            addedListener = listener;
            addedAsPermanent = false;
        }

        @Override
        public void addListener(Object listener, boolean permanent) {
            addedListener = listener;
            addedAsPermanent = permanent;
        }

        @Override
        public void removeListener(Object listener) {
        }

        @Override
        public void removeListenerOfClass(Class<?> listenerClass) {
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
