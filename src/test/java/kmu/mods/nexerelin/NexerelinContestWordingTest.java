package kmu.mods.nexerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModManagerAPI;
import com.fs.starfarer.api.SettingsAPI;

import kmu.maplayers.politicalmap.tooltip.ContestWording;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Pins the gate behind how a political box words presence beside a system's holder, which is the one
 * place that answer is made.
 *
 * <p>Worth holding because it is the whole of the difference a player sees: every box is built the
 * same way on both installs, and what parts a heading reporting a contest from one reporting presence
 * is this gate alone. Both sides are driven here - unlike the alliance gate beside it, this reads the
 * mod set and names no {@code exerelin.*} class, so there is nothing that fails to initialise outside
 * a running game.
 */
class NexerelinContestWordingTest {

    private static final String NEXERELIN_MOD_ID = "nexerelin";

    @Nested
    class ResolveWording {

        @Test
        void wordsTheBlockAsAContestWhereNexerelinIsPresent() {

            try (var globalMock = mockStatic(Global.class)) {

                stubNexEnabled(globalMock, true);

                assertThat(NexerelinContestWording.resolveWording())
                    .isEqualTo(ContestWording.CONTESTED);
            }
        }

        @Test
        void wordsTheBlockAsPlainPresenceWhereNexerelinIsAbsent() {

            try (var globalMock = mockStatic(Global.class)) {

                stubNexEnabled(globalMock, false);

                assertThat(NexerelinContestWording.resolveWording())
                    .isEqualTo(ContestWording.PRESENT);
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
