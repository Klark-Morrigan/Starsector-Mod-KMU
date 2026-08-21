package kmu.starsector.listeners;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * The sector an installer suite poses: one that answers for its listener manager and refuses every
 * other question.
 *
 * <p>A proxy rather than a mock, and refusing rather than answering null, because the point is the
 * narrowness: an installer under test may ask for the listener manager and nothing else, so a call
 * it was not meant to make fails loudly instead of resolving to a null a later assertion has to
 * catch.
 *
 * <p>Separate from {@link RecordingListenerManager}, which records what an install did. This poses
 * where the install happens; that one poses what happened.
 */
public final class SectorListenerFixtures {

    private SectorListenerFixtures() {
        // fixture of static wiring, no instances.
    }

    /**
     * A sector whose only answer is its listener manager.
     *
     * @param listenerManager what the sector hands back; null poses the sector whose manager is not
     *                        up yet, which every installer has to tolerate
     * @return the posed sector
     */
    public static SectorAPI buildSector(ListenerManagerAPI listenerManager) {

        return proxy(SectorAPI.class, (proxy, method, args) -> {

            if (method.getName().equals("getListenerManager")) {
                return listenerManager;
            }
            return handleObjectMethodOrThrow(proxy, method, args);
        });
    }

    // Object's own methods still have to work for the proxy to be usable at all - printed in a
    // failure message, put in a collection, compared against itself - so they are answered here and
    // everything else is refused.
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
}
