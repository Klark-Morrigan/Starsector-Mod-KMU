package kmu.mods.nexerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModManagerAPI;
import com.fs.starfarer.api.SettingsAPI;

import kmu.maplayers.base.visibility.colonies.FactionAlliances;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Pins the soft-dependency gate on the side a Nex-free install takes: the gate answers before
 * anything naming {@code exerelin.*} is resolved, so a classloader that has no such class to find
 * is never asked to find one.
 *
 * <p>Worth holding because the answer is a visibility rule's input: a gate that threw, or that
 * invented a partnership, would decide what an install without Nexerelin is told about a concealed
 * base.
 *
 * <p>The other side cannot be driven here at all, and not for want of the jar: Nexerelin's alliance
 * manager reads its own configuration off {@code Global.getSettings()} in a static initialiser, so
 * naming that class outside a running game fails to initialise it whatever is on the classpath.
 * What the far side does with alliances once it has them is
 * {@link NexAllianceSourceTest}'s, over the flattening that needs no manager.
 */
class NexerelinAlliancesTest {

    private static final String NEXERELIN_MOD_ID = "nexerelin";

    @Nested
    class ResolveFactionAlliances {

        @Test
        void readsNobodyAsAlliedWhereNexerelinIsAbsent() {

            try (var globalMock = mockStatic(Global.class)) {

                stubNexEnabled(globalMock, false);

                assertThat(NexerelinAlliances.resolveFactionAlliances())
                    .isEqualTo(FactionAlliances.NONE);
            }
        }
    }

    private static void stubNexEnabled(MockedStatic<Global> globalMock, boolean isEnabled) {

        var settingsMock = mock(SettingsAPI.class);
        var modManagerMock = mock(ModManagerAPI.class);

        globalMock
            .when(Global::getSettings)
            .thenReturn(settingsMock);

        when(settingsMock.getModManager())
            .thenReturn(modManagerMock);

        when(modManagerMock.isModEnabled(NEXERELIN_MOD_ID))
            .thenReturn(isEnabled);
    }
}
