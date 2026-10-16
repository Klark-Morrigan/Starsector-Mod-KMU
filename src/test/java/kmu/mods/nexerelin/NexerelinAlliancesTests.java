package kmu.mods.nexerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModManagerAPI;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.mods.nexerelin.NexerelinAllianceSource;
import kmlib.starsector.factions.alliances.AllianceRecord;
import kmlib.starsector.factions.alliances.FactionAlliances;
import kmlib.testfixtures.starsector.StubbedGlobalLogger;

import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Pins the two folds KMU takes over the live alliance set, on both sides of the soft-dependency
 * gate.
 *
 * <p>On the side a Nex-free install takes, the gate answers before anything naming
 * {@code exerelin.*} is resolved, so a classloader that has no such class to find is never asked to
 * find one - and each fold answers as the install without alliances it is: nobody allied, and the
 * shared identity grouping. Worth holding because those answers feed a visibility rule and the map's
 * blocs alike: a gate that threw, or that invented a partnership, would decide what such an install
 * is told.
 *
 * <p>The side with alliances is driven through the library's source rather than through Nexerelin
 * itself: its alliance manager reads its own configuration off {@code Global.getSettings()} in a
 * static initialiser, so naming that class outside a running game fails to initialise it whatever is
 * on the classpath. What the folds do with records in detail is pinned on hand-built ones by the
 * suites beside each fold; what is pinned here is that each fold reads the source at all.
 */
class NexerelinAlliancesTests {

    private static final String NEXERELIN_MOD_ID = "nexerelin";

    private static final String ALLIANCE_ID = "alliance-1";

    @Nested
    class ResolveFactionAlliances {

        @Test
        void readsNobodyAsAlliedWhereNexerelinIsAbsent() {

            try (var globalMock = StubbedGlobalLogger.openGlobalAnsweringLoggers()) {

                stubNexEnabled(globalMock, false);

                assertThat(NexerelinAlliances.resolveFactionAlliances())
                    .isEqualTo(FactionAlliances.NONE);
            }
        }
    }

    @Nested
    class ResolveFactionAlliancesIn {

        @Test
        void answersTheRunningGamesAlliancesForTheSectorHanded() {
            // Nexerelin offers no read over another sector, so the handed one changes nothing.
            try (var allianceSourceMock = mockStatic(NexerelinAllianceSource.class)) {

                stubAllianceRecords(allianceSourceMock, List.of(buildAlliedPowers()));

                assertThat(NexerelinAlliances.resolveFactionAlliancesIn(mock(SectorAPI.class))
                        .areFactionsAllied("hegemony", "astral_armada"))
                    .isTrue();
            }
        }
    }

    @Nested
    class ResolveGrouping {

        @Test
        void handsBackTheSharedIdentityGroupingWhereNexerelinIsAbsent() {
            // The answer every rebuild on such an install takes, so it is the shared grouping itself
            // rather than a fold rebuilt to look like it.
            try (var globalMock = StubbedGlobalLogger.openGlobalAnsweringLoggers()) {

                stubNexEnabled(globalMock, false);

                assertThat(NexerelinAlliances.resolveGrouping())
                    .isSameAs(HolderGrouping.identity());
            }
        }

        @Test
        void handsBackTheSharedIdentityGroupingWhereNoAllianceStands() {
            // The mod present with nothing formed yet reads exactly as the mod absent.
            try (var allianceSourceMock = mockStatic(NexerelinAllianceSource.class)) {

                stubAllianceRecords(allianceSourceMock, List.of());

                assertThat(NexerelinAlliances.resolveGrouping())
                    .isSameAs(HolderGrouping.identity());
            }
        }

        @Test
        void foldsTheLiveAlliancesIntoBlocs() {

            try (var allianceSourceMock = mockStatic(NexerelinAllianceSource.class)) {

                stubAllianceRecords(allianceSourceMock, List.of(buildAlliedPowers()));

                var grouping = NexerelinAlliances.resolveGrouping();

                assertThat(grouping.resolveBlocId("astral_armada"))
                    .isEqualTo(ALLIANCE_ID);
                assertThat(grouping.isGroupedBloc(ALLIANCE_ID))
                    .isTrue();
            }
        }
    }

    // Two members ranked hegemony-first, the ordinary shape of a live alliance.
    private static AllianceRecord buildAlliedPowers() {
        return new AllianceRecord(
            ALLIANCE_ID,
            "Allied Powers",
            List.of("hegemony", "astral_armada"));
    }

    private static void stubAllianceRecords(
            MockedStatic<NexerelinAllianceSource> allianceSourceMock,
            List<AllianceRecord> allianceRecords) {

        allianceSourceMock
            .when(NexerelinAllianceSource::readAllianceRecords)
            .thenReturn(allianceRecords);
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
