package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.colonies.ColonyVisibility;
import kmlib.starsector.colonies.RevelationGate;

import kmu.maplayers.base.visibility.MapVisibility;
import kmu.maplayers.base.visibility.MapVisibilityOverrides;
import kmu.settings.KmuMapLayerSettings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the one thing this seam exists to guarantee: that the political map's two inhabitation
 * reads - the whole-sector scan a rebuild takes and the per-system read a refresh takes - bind the
 * player's reveal to the same rule.
 *
 * <p>They answer the same question at different scopes and are taken minutes apart, the scan where
 * a rebuild began and the read when a colony event marks a system. One of them sampling the reveal
 * differently would leave a single system in the standing set answering under a toggle none of its
 * neighbours did, which shows as one cell drawn as backdrop in a settled system - and nothing
 * rebuilds it, since a colony event marks a system once.
 *
 * <p>The rule itself is {@link MapVisibility}'s and pinned by its own suite, so it is a seam here:
 * what each case reads is which rule was reached and what reveal was handed to it.
 */
final class PoliticalMapInhabitationTest {

    // What the live read hands down when the player has touched nothing: the fog alone, with
    // every gate the rule knows of held. Not MapVisibilityOverrides.NONE - that names the value
    // a caller with nothing to override passes, and the shipped state is not that value, because
    // holding a gate is something the read actively does.
    //
    // Stated against the enum's own constants so a gate added later has to reach this read
    // rather than quietly stopping at it.
    private static final MapVisibilityOverrides UNDER_THE_SHIPPED_SETTINGS =
        new MapVisibilityOverrides(
            new ColonyVisibility(false, EnumSet.allOf(RevelationGate.class)),
            false);

    // The same, with the "show all factions" reveal on - the one toggle each case below raises.
    private static final MapVisibilityOverrides UNDER_THE_REVEAL =
        new MapVisibilityOverrides(
            new ColonyVisibility(true, EnumSet.allOf(RevelationGate.class)),
            false);

    private static final String SETTLED_SYSTEM = "settled";

    private MockedStatic<MapVisibility> visibilityMock;
    private MockedStatic<KmuMapLayerSettings> settingsMock;

    private SectorAPI sectorMock;
    private StarSystemAPI systemMock;

    @BeforeEach
    void openSeams() {

        sectorMock = mock(SectorAPI.class);
        systemMock = mock(StarSystemAPI.class);

        visibilityMock = mockStatic(MapVisibility.class);

        // The reveal is read off LunaLib, which no test JVM has: the seam's own false is the
        // player's normal state, and the case about the dev toggle turns it on.
        settingsMock = mockStatic(KmuMapLayerSettings.class);
    }

    @AfterEach
    void closeSeams() {
        settingsMock.close();
        visibilityMock.close();
    }

    @Nested
    class ReadInhabitedSystemIds {

        @Test
        void readInhabitedSystemIdsScansTheGivenSectorUnderTheLiveReveal() {

            visibilityMock
                .when(() -> MapVisibility.findInhabitedSystemIds(
                    same(sectorMock),
                    eq(UNDER_THE_SHIPPED_SETTINGS)))
                .thenReturn(Set.of(SETTLED_SYSTEM));

            assertThat(PoliticalMapInhabitation.readInhabitedSystemIds(sectorMock))
                .containsExactly(SETTLED_SYSTEM);
        }

        @Test
        void readInhabitedSystemIdsWidensTheScanUnderTheDevReveal() {
            // The dev reveal admits undiscovered colonies, and it has to reach the scan rather than
            // being applied over its result: a system left out of the scan has no cell to reveal.
            settingsMock
                .when(KmuMapLayerSettings::shouldShowUndiscoveredMarkets)
                .thenReturn(true);

            PoliticalMapInhabitation.readInhabitedSystemIds(sectorMock);

            visibilityMock.verify(() -> MapVisibility.findInhabitedSystemIds(
                any(),
                eq(UNDER_THE_REVEAL)));
        }
    }

    @Nested
    class IsSystemInhabited {

        @Test
        void isSystemInhabitedAsksTheRuleAboutTheGivenSystem() {

            visibilityMock
                .when(() -> MapVisibility.isInhabited(
                    same(sectorMock),
                    same(systemMock),
                    eq(UNDER_THE_SHIPPED_SETTINGS)))
                .thenReturn(true);

            assertThat(PoliticalMapInhabitation.isSystemInhabited(sectorMock, systemMock))
                .isTrue();
        }

        @Test
        void isSystemInhabitedWidensUnderTheSameRevealTheScanReads() {
            // The claim this class exists for. Both arms sample the reveal themselves, so a refresh
            // re-deriving one system answers under the toggle the rebuild's scan would have read
            // had it run at that moment - rather than under whatever was in force when it did run.
            settingsMock
                .when(KmuMapLayerSettings::shouldShowUndiscoveredMarkets)
                .thenReturn(true);

            PoliticalMapInhabitation.isSystemInhabited(sectorMock, systemMock);

            // The sector-taking form is named explicitly: the rule is also asked off a colony set
            // a caller already holds, and a bare matcher cannot say which of the two is meant.
            visibilityMock.verify(() -> MapVisibility.isInhabited(
                any(SectorAPI.class),
                any(StarSystemAPI.class),
                eq(UNDER_THE_REVEAL)));
        }
    }
}
