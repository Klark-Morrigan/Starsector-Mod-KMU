package kmu.mods.nexerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModManagerAPI;
import com.fs.starfarer.api.SettingsAPI;

import kmlib.mods.nexerelin.NexerelinAllianceSource;
import kmlib.starsector.factions.alliances.AllianceRecord;
import kmlib.starsector.factions.alliances.FactionAlliances;

import kmu.maplayers.ownermap.holding.HolderGrouping;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Pins the three folds KMU takes over the live alliance set, on both sides of the soft-dependency
 * gate.
 *
 * <p>On the side a Nex-free install takes, the gate answers before anything naming
 * {@code exerelin.*} is resolved, so a classloader that has no such class to find is never asked to
 * find one - and each fold answers as the install without alliances it is: nobody allied, the shared
 * identity grouping, a steady fingerprint. Worth holding because those answers feed a visibility rule,
 * the map's blocs and the watcher's revision alike: a gate that threw, or that invented a partnership,
 * would decide what such an install is told.
 *
 * <p>The side with alliances is driven through the library's source rather than through Nexerelin
 * itself: its alliance manager reads its own configuration off {@code Global.getSettings()} in a
 * static initialiser, so naming that class outside a running game fails to initialise it whatever is
 * on the classpath. What the folds do with records in detail is pinned on hand-built ones by the
 * suites beside each fold; what is pinned here is that each fold reads the source at all.
 */
class NexerelinAlliancesTest {

    private static final String NEXERELIN_MOD_ID = "nexerelin";

    // The token no alliances fold to: the positional hash of an empty list of canonical lines. Stated
    // as the number it is, since what the watcher relies on is that it never moves.
    private static final int NO_ALLIANCES_FINGERPRINT = 1;

    private static final String ALLIANCE_ID = "alliance-1";

    @Nested
    class ResolveFactionAlliances {

        @Test
        void resolveFactionAlliancesReadsNobodyAsAlliedWhereNexerelinIsAbsent() {

            try (var globalMock = mockStatic(Global.class)) {

                stubNexEnabled(globalMock, false);

                assertThat(NexerelinAlliances.resolveFactionAlliances())
                    .isEqualTo(FactionAlliances.NONE);
            }
        }
    }

    @Nested
    class ResolveGrouping {

        @Test
        void resolveGroupingHandsBackTheSharedIdentityGroupingWhereNexerelinIsAbsent() {
            // The answer every rebuild on such an install takes, so it is the shared grouping itself
            // rather than a fold rebuilt to look like it.
            try (var globalMock = mockStatic(Global.class)) {

                stubNexEnabled(globalMock, false);

                assertThat(NexerelinAlliances.resolveGrouping())
                    .isSameAs(HolderGrouping.identity());
            }
        }

        @Test
        void resolveGroupingHandsBackTheSharedIdentityGroupingWhereNoAllianceStands() {
            // The mod present with nothing formed yet reads exactly as the mod absent.
            try (var allianceSourceMock = mockStatic(NexerelinAllianceSource.class)) {

                stubAllianceRecords(allianceSourceMock, List.of());

                assertThat(NexerelinAlliances.resolveGrouping())
                    .isSameAs(HolderGrouping.identity());
            }
        }

        @Test
        void resolveGroupingFoldsTheLiveAlliancesIntoBlocs() {

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

    @Nested
    class ComputeAllianceFingerprint {

        @Test
        void computeAllianceFingerprintPollsTheSteadyTokenWhereNexerelinIsAbsent() {
            // What keeps the alliance revision still on such an install: the watcher polls this every
            // pass, and a token that moved would rebuild a map nothing about has changed.
            try (var globalMock = mockStatic(Global.class)) {

                stubNexEnabled(globalMock, false);

                assertThat(NexerelinAlliances.computeAllianceFingerprint())
                    .isEqualTo(NO_ALLIANCES_FINGERPRINT);
            }
        }

        @Test
        void computeAllianceFingerprintMovesOffTheSteadyTokenOnceAnAllianceStands() {
            // The one change the watcher exists to notice, read through the live source rather than
            // off records handed in.
            try (var allianceSourceMock = mockStatic(NexerelinAllianceSource.class)) {

                stubAllianceRecords(allianceSourceMock, List.of(buildAlliedPowers()));

                assertThat(NexerelinAlliances.computeAllianceFingerprint())
                    .isNotEqualTo(NO_ALLIANCES_FINGERPRINT);
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
