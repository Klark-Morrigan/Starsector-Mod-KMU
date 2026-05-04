package kmu.starsector;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.characters.RelationshipAPI;
import kmu.starsector.StarsectorPlayerRelationshipFormatter.RelationshipSummary;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;

class StarsectorPlayerRelationshipFormatterTest {
    private static final Color RED = new Color(200, 50, 50);
    private final StarsectorPlayerRelationshipFormatter formatter =
            new StarsectorPlayerRelationshipFormatter();

    @Test
    void returnsEmptySummaryForNullFaction() {
        RelationshipSummary result = formatter.formatPlayerRelationship(null);

        assertThat(result.getDescription()).isNull();
        assertThat(result.getColor()).isNull();
    }

    @Test
    void formatsDescriptionAndColorViaRelationshipApiPath() {
        FactionAPI faction = factionWithRelationshipApi(relationship(RepLevel.VENGEFUL, -100, RED));

        RelationshipSummary result = formatter.formatPlayerRelationship(faction);

        assertThat(result.getDescription()).isEqualTo("Vengeful (-100 / 100)");
        assertThat(result.getColor()).isEqualTo(RED);
    }

    @Test
    void fallsBackToFactionRelationshipWhenRelToPlayerIsNull() {
        // Proxy returns null for getRelToPlayer, then 0.0f (neutral) for getRelationship
        FactionAPI faction = factionWithFallbackRelationship(0.0f, RED);

        RelationshipSummary result = formatter.formatPlayerRelationship(faction);

        // Level name varies by Starsector version; verify only the numeric format
        assertThat(result.getDescription()).isNotNull().contains("/ 100");
    }

    @Test
    void returnsEmptySummaryWhenBothRelationshipPathsAreUnreadable() {
        // getRelToPlayer() returns null; getRelationship() throws, caught by readValueOrNull
        FactionAPI faction = factionWithRelationshipApi(null);

        RelationshipSummary result = formatter.formatPlayerRelationship(faction);

        assertThat(result.getDescription()).isNull();
        assertThat(result.getColor()).isNull();
    }

    private static FactionAPI factionWithRelationshipApi(RelationshipAPI relationship) {
        return proxy(FactionAPI.class, (p, method, args) -> {
            switch (method.getName()) {
                case "getRelToPlayer": return relationship;
                default: return handleObjectMethodOrThrow(p, method, args);
            }
        });
    }

    private static FactionAPI factionWithFallbackRelationship(float rel, Color relColor) {
        return proxy(FactionAPI.class, (p, method, args) -> {
            switch (method.getName()) {
                case "getRelToPlayer": return null;
                case "getRelationship": return rel;
                case "getRelColor": return relColor;
                default: return handleObjectMethodOrThrow(p, method, args);
            }
        });
    }

    private static RelationshipAPI relationship(RepLevel level, int repInt, Color color) {
        return proxy(RelationshipAPI.class, (p, method, args) -> {
            switch (method.getName()) {
                case "getLevel": return level;
                case "getRepInt": return repInt;
                case "getRelColor": return color;
                default: return handleObjectMethodOrThrow(p, method, args);
            }
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
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
}
