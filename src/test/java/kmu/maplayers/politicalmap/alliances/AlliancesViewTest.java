package kmu.maplayers.politicalmap.alliances;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModManagerAPI;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.politicalmap.base.politics.OwnershipGrouping;
import kmu.maplayers.politicalmap.base.refresh.PoliticalMapRefresh;
import kmu.settings.FactionNameFormatChoice;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Pins the alliances view's render rules: an alliance bloc paints in full colour and reads its
 * alliance name, while every non-alliance bloc recedes to the muted independent style and is named
 * exactly as the faction view would name that lone faction. The grouping is sampled from Nexerelin,
 * so its resolution is pinned only for the Nex-absent fallback here; a live alliance set is the
 * in-game test's concern.
 */
final class AlliancesViewTest {
    private static final String NEXERELIN_MOD_ID = "nexerelin";

    // An alliance grouping with one alliance bloc "rebel_pact" fusing two members, coloured off the
    // sorted-first member and named "Rebel Pact"; a faction not in the map stays its own lone bloc.
    private static final OwnershipGrouping ALLIANCE_GROUPING = new OwnershipGrouping(
            Map.of("rebels", "rebel_pact", "pirates", "rebel_pact"),
            Map.of("rebel_pact", "rebels"),
            Map.of("rebel_pact", "Rebel Pact"));

    @Nested
    class GetId {

        @Test
        void getIdIsTheFrozenAlliancesId() {
            // Frozen: renaming it silently resets any save that selected this view to the default.
            assertThat(AlliancesView.INSTANCE.getId()).isEqualTo("alliances");
        }
    }

    @Nested
    class GetGroupingRevision {

        @Test
        void getGroupingRevisionTracksTheAllianceRevision() {
            // The alliances view's live data is the alliance set, so its contribution must
            // move with the shared alliance revision - that is what folds a membership change
            // into the content token and repaints the view without a reload.
            var before = AlliancesView.INSTANCE.getGroupingRevision();
            PoliticalMapRefresh.requestAllianceRefresh();

            assertThat(AlliancesView.INSTANCE.getGroupingRevision()).isEqualTo(before + 1);
        }
    }

    @Nested
    class ResolveGrouping {

        @Test
        void resolveGroupingFallsBackToIdentityWhenNexIsAbsent() {
            // Without Nex the gate returns the identity grouping, so the view degrades to the faction
            // grouping rather than touching any exerelin class.
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                stubNexEnabled(globalMock, false);

                assertThat(AlliancesView.INSTANCE.resolveGrouping())
                        .isSameAs(OwnershipGrouping.identity());
            }
        }
    }

    @Nested
    class ShouldUseIndependentStyle {

        @Test
        void shouldUseIndependentStyleIsFalseForAnAllianceBloc() {
            // Alliances keep the full faction style so they stand out.
            assertThat(AlliancesView.INSTANCE.shouldUseIndependentStyle(
                    "rebel_pact", ALLIANCE_GROUPING)).isFalse();
        }

        @Test
        void shouldUseIndependentStyleIsTrueForALoneFaction() {
            // Every non-alliance bloc recedes to the muted independent style.
            assertThat(AlliancesView.INSTANCE.shouldUseIndependentStyle(
                    "hegemony", ALLIANCE_GROUPING)).isTrue();
        }
    }

    @Nested
    class ResolveName {

        @Test
        void resolveNameReadsTheAllianceNameForAnAllianceBloc() {
            // An alliance bloc id is not a faction id, so its label comes from the grouping, not the
            // sector - which is therefore never consulted.
            var sectorMock = mock(SectorAPI.class);

            assertThat(AlliancesView.INSTANCE.resolveName("rebel_pact", ALLIANCE_GROUPING, sectorMock,
                    FactionNameFormatChoice.FULL)).isEqualTo("Rebel Pact");
        }

        @Test
        void resolveNameReadsTheFactionNameForALoneFaction() {
            // A non-alliance bloc is a lone faction, named as the faction view names it.
            var sectorMock = mock(SectorAPI.class);
            var factionMock = mock(FactionAPI.class);
            when(sectorMock.getFaction("hegemony")).thenReturn(factionMock);
            when(factionMock.getDisplayNameLong()).thenReturn("The Hegemony");

            assertThat(AlliancesView.INSTANCE.resolveName("hegemony", ALLIANCE_GROUPING, sectorMock,
                    FactionNameFormatChoice.FULL)).isEqualTo("The Hegemony");
        }
    }

    private static void stubNexEnabled(MockedStatic<Global> globalMock, boolean isEnabled) {
        var settingsMock = mock(SettingsAPI.class);
        var modManagerMock = mock(ModManagerAPI.class);
        globalMock.when(Global::getSettings).thenReturn(settingsMock);
        when(settingsMock.getModManager()).thenReturn(modManagerMock);
        when(modManagerMock.isModEnabled(NEXERELIN_MOD_ID)).thenReturn(isEnabled);
    }
}
