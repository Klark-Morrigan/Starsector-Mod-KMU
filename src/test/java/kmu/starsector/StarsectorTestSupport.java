package kmu.starsector;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import kmu.util.KmuLocalisation;

import java.awt.Color;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;

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
        Map<String, String> stringsByKey = stringsByKey();
        return proxy(SettingsAPI.class, (proxy, method, args) -> {
            if ("getFloat".equals(method.getName())) {
                return 1f;
            }
            if ("getColor".equals(method.getName())) {
                return Color.WHITE;
            }
            if ("getString".equals(method.getName())) {
                if (args != null
                        && args.length == 2
                        && KmuLocalisation.CATEGORY.equals(args[0])) {
                    return stringsByKey.get(args[1]);
                }
                return null;
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static Map<String, String> stringsByKey() {
        Map<String, String> stringsByKey = new LinkedHashMap<>();
        stringsByKey.put(KmuLocalisation.CONDITION_MANAGER_LOCATION, "Location:");
        stringsByKey.put(KmuLocalisation.CONDITION_MANAGER_LOCATION_UNKNOWN, "Unknown");
        stringsByKey.put(KmuLocalisation.CONDITION_MANAGER_SUMMARY, "Conditions:");
        stringsByKey.put(KmuLocalisation.CONDITION_MANAGER_SUMMARY_VISIBLE, "%d visible");
        stringsByKey.put(KmuLocalisation.CONDITION_MANAGER_SUMMARY_SUPPRESSED, "%d suppressed");
        stringsByKey.put(KmuLocalisation.CONDITION_MANAGER_SUMMARY_PRESENT, "%d present");
        stringsByKey.put(KmuLocalisation.CONDITION_MANAGER_SUMMARY_HIDDEN, "%d hidden");
        stringsByKey.put(KmuLocalisation.CONDITION_MANAGER_SUMMARY_AVAILABLE, "%d available");
        stringsByKey.put(KmuLocalisation.CONDITION_MANAGER_SUMMARY_TOTAL, "%d total.");
        stringsByKey.put(KmuLocalisation.CONDITION_MANAGER_EMPTY, "No market condition specs are available.");
        stringsByKey.put(KmuLocalisation.CONDITION_MANAGER_TOOLTIP_SUPPRESSED_TITLE, "Suppressed");
        stringsByKey.put(KmuLocalisation.CONDITION_MANAGER_TOOLTIP_SUPPRESSED_BODY, "This condition is present on the market, but it's suppressed and has no effect.");
        stringsByKey.put(KmuLocalisation.CONDITION_MANAGER_TOOLTIP_HIDDEN_TITLE, "Hidden");
        stringsByKey.put(KmuLocalisation.CONDITION_MANAGER_TOOLTIP_HIDDEN_BODY, "This condition is present on the market, but it's hidden and still applies its effects.");
        stringsByKey.put(KmuLocalisation.DIALOG_CLOSE, "Close");
        return stringsByKey;
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
