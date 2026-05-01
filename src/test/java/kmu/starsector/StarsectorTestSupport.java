package kmu.starsector;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;

import java.awt.Color;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

public final class StarsectorTestSupport {
    private StarsectorTestSupport() {
    }

    public static void installSettings() {
        Global.setSettings(settings());
    }

    public static void clearSettings() {
        Global.setSettings(null);
    }

    private static SettingsAPI settings() {
        return proxy(SettingsAPI.class, (proxy, method, args) -> {
            if ("getFloat".equals(method.getName())) {
                return 1f;
            }
            if ("getColor".equals(method.getName())) {
                return Color.WHITE;
            }
            if ("getString".equals(method.getName())) {
                return null;
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (boolean.class.equals(returnType)) {
            return false;
        }
        if (char.class.equals(returnType)) {
            return '\0';
        }
        if (byte.class.equals(returnType)) {
            return (byte) 0;
        }
        if (short.class.equals(returnType)) {
            return (short) 0;
        }
        if (int.class.equals(returnType)) {
            return 0;
        }
        if (long.class.equals(returnType)) {
            return 0L;
        }
        if (float.class.equals(returnType)) {
            return 0f;
        }
        if (double.class.equals(returnType)) {
            return 0d;
        }
        return null;
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
