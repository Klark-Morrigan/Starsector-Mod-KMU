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
 * Pins the soft-dependency gate on the side a Nex-free install takes. The other side cannot be
 * driven here at all - it resolves the holder that names {@code exerelin.*}, which is absent from
 * the test classpath by the same arrangement that keeps it off a Nex-free install's - so what is
 * held is that the gate answers before touching it.
 *
 * <p>Worth holding because the answer is a visibility rule's input: a gate that threw, or that
 * invented a partnership, would decide what an install without Nexerelin is told about a concealed
 * base.
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
