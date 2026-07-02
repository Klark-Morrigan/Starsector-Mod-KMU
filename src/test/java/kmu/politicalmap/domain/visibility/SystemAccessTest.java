package kmu.politicalmap.domain.visibility;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModManagerAPI;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.GateEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Tags;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import assortment_of_things.abyss.entities.hyper.AbyssalFracture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Pins the access rule {@link SystemAccess}: a system wired into hyperspace by a
 * jump point has access; a transverse-only system (no jump point, reachable only
 * via a nascent gravity well) does not, even with a present-but-inactive gate; a
 * cut-off system has access only through an active gate; and a RAT Abyssal
 * Fracture grants access even to a cut-off system, but only while RAT is enabled.
 */
final class SystemAccessTest {

    @Nested
    class HasMapAccess {

        @Test
        void hasMapAccessIsTrueForSystemWithAJumpPoint() {
            assertThat(SystemAccess.hasMapAccess(systemNotCutOff("a"))).isTrue();
        }

        @Test
        void hasMapAccessIsFalseForTransverseOnlySystemWithAnInactiveGate() {
            // The JDP hidden-system case: not cut off (the engine never tags a
            // nascent-well system), no jump point, only an unlit gate.
            var system = transverseOnlySystem("a", gateWithPlugin(gatePlugin(false)));

            assertThat(SystemAccess.hasMapAccess(system)).isFalse();
        }

        @Test
        void hasMapAccessIsFalseForCutOffSystemWithNoGate() {
            assertThat(SystemAccess.hasMapAccess(cutOffSystem("a"))).isFalse();
        }

        @Test
        void hasMapAccessIsFalseForCutOffSystemWithOnlyAnInactiveGate() {
            var system = cutOffSystem("a", gateWithPlugin(gatePlugin(false)));

            assertThat(SystemAccess.hasMapAccess(system)).isFalse();
        }

        @Test
        void hasMapAccessIsTrueForCutOffSystemWithAnActiveGate() {
            var system = cutOffSystem("a", gateWithPlugin(gatePlugin(true)));

            assertThat(SystemAccess.hasMapAccess(system)).isTrue();
        }

        @Test
        void hasMapAccessIsTrueForCutOffSystemWithAFractureWhenRatEnabled() {
            // A fracture ferries fleets in past the disabled jump points, so it
            // overrides the cut-off flag the way an active gate does.
            var system = cutOffSystemWithEntities("a", fractureEntity());
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                stubRatEnabled(globalMock, true);

                assertThat(SystemAccess.hasMapAccess(system)).isTrue();
            }
        }

        @Test
        void hasMapAccessIsFalseForCutOffSystemWithAFractureWhenRatDisabled() {
            // The optional dependency is off, so the matcher cannot see the
            // fracture and the system reads as the cut-off system it is.
            var system = cutOffSystemWithEntities("a", fractureEntity());
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                stubRatEnabled(globalMock, false);

                assertThat(SystemAccess.hasMapAccess(system)).isFalse();
            }
        }
    }

    private static StarSystemAPI systemNotCutOff(String id) {
        // Not cut off (hasTag defaults to false) and wired into hyperspace by a
        // jump point - a normally reachable system.
        var systemMock = mock(StarSystemAPI.class);
        when(systemMock.getId()).thenReturn(id);
        when(systemMock.getJumpPoints()).thenReturn(List.of(mock(SectorEntityToken.class)));
        return systemMock;
    }

    private static StarSystemAPI transverseOnlySystem(String id, SectorEntityToken... gates) {
        // Not cut off and holds no jump point - reachable only by transverse jump
        // to a nascent gravity well. Any passed gates stand in for present-but-
        // inactive gates that must not confer access.
        var systemMock = mock(StarSystemAPI.class);
        when(systemMock.getId()).thenReturn(id);
        when(systemMock.getEntitiesWithTag(Tags.GATE)).thenReturn(List.of(gates));
        when(systemMock.getJumpPoints()).thenReturn(List.of());
        return systemMock;
    }

    private static StarSystemAPI cutOffSystem(String id, SectorEntityToken... gates) {
        var systemMock = mock(StarSystemAPI.class);
        when(systemMock.getId()).thenReturn(id);
        when(systemMock.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER)).thenReturn(true);
        when(systemMock.getEntitiesWithTag(Tags.GATE)).thenReturn(List.of(gates));
        return systemMock;
    }

    private static SectorEntityToken gateWithPlugin(GateEntityPlugin plugin) {
        var gateMock = mock(SectorEntityToken.class);
        when(gateMock.getCustomPlugin()).thenReturn(plugin);
        return gateMock;
    }

    private static GateEntityPlugin gatePlugin(boolean isActive) {
        var pluginMock = mock(GateEntityPlugin.class);
        when(pluginMock.isActive()).thenReturn(isActive);
        return pluginMock;
    }

    private static StarSystemAPI cutOffSystemWithEntities(String id, SectorEntityToken... entities) {
        // Cut off and holding no jump point or gate - access can come only from
        // one of the passed entities (a fracture in these cases).
        var systemMock = mock(StarSystemAPI.class);
        when(systemMock.getId()).thenReturn(id);
        when(systemMock.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER)).thenReturn(true);
        when(systemMock.getAllEntities()).thenReturn(List.of(entities));
        return systemMock;
    }

    private static SectorEntityToken fractureEntity() {
        var entityMock = mock(SectorEntityToken.class);
        when(entityMock.getCustomPlugin()).thenReturn(mock(AbyssalFracture.class));
        return entityMock;
    }

    private static void stubRatEnabled(MockedStatic<Global> globalMock, boolean isEnabled) {
        var settingsMock = mock(SettingsAPI.class);
        var modManagerMock = mock(ModManagerAPI.class);
        globalMock.when(Global::getSettings).thenReturn(settingsMock);
        when(settingsMock.getModManager()).thenReturn(modManagerMock);
        when(modManagerMock.isModEnabled("assortment_of_things")).thenReturn(isEnabled);
    }
}
