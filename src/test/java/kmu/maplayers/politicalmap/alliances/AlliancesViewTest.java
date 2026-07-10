package kmu.maplayers.politicalmap.alliances;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModManagerAPI;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmu.maplayers.politicalmap.base.BlocStyleAdjustment;
import kmu.maplayers.politicalmap.base.RecedePreferences;
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
 * alliance name, while a non-allied faction keeps its own faction style until Desaturate makes it
 * adopt the independent style bundle; Mute only dims the active style via the opacity modifier. A
 * non-allied faction is named exactly as the faction view would name that lone faction. The grouping
 * is sampled from Nexerelin,
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
    class GetContentRevision {

        @Test
        void getContentRevisionShiftsWhenTheAllianceRevisionMoves() {
            // The alliance set is one of the view's live inputs, so a membership change must shift
            // the revision - that is what folds it into the content token and repaints the view
            // without a reload.
            var before = AlliancesView.INSTANCE.getContentRevision();
            PoliticalMapRefresh.requestAllianceRefresh();

            assertThat(AlliancesView.INSTANCE.getContentRevision()).isNotEqualTo(before);
        }

        @Test
        void getContentRevisionShiftsWhenTheRecedeStyleRevisionMoves() {
            // A Mute/Desaturate flip is the view's other live input: those sidebar-only toggles
            // never move settingsRevision, so the recede-style revision must fold in here for a flip
            // to repaint the overlay live.
            var before = AlliancesView.INSTANCE.getContentRevision();
            PoliticalMapRefresh.requestRecedeStyleRefresh();

            assertThat(AlliancesView.INSTANCE.getContentRevision()).isNotEqualTo(before);
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
        void shouldUseIndependentStyleIsTrueForIndependentSpace() {
            // Genuine independent space always takes the independent style, exactly as the faction
            // view classifies it - short-circuiting before the Desaturate toggle is even read.
            assertThat(AlliancesView.INSTANCE.shouldUseIndependentStyle(
                    Factions.INDEPENDENT, ALLIANCE_GROUPING)).isTrue();
        }

        @Test
        void shouldUseIndependentStyleIsFalseForAnAllianceBlocEvenWhenDesaturated() {
            // An alliance always paints in the full faction style, no matter the Desaturate toggle.
            try (MockedStatic<RecedePreferences> preferencesMock =
                    mockStatic(RecedePreferences.class)) {
                preferencesMock.when(RecedePreferences::isDesaturated).thenReturn(true);

                assertThat(AlliancesView.INSTANCE.shouldUseIndependentStyle(
                        "rebel_pact", ALLIANCE_GROUPING)).isFalse();
            }
        }

        @Test
        void shouldUseIndependentStyleIsFalseForALoneFactionWhenNotDesaturated() {
            // With Desaturate off a non-allied faction keeps its own faction style, so it reads
            // exactly as the faction view draws it; muting only dims that style, never swaps the
            // bundle, so only the allied factions differ across the two views.
            try (MockedStatic<RecedePreferences> preferencesMock =
                    mockStatic(RecedePreferences.class)) {
                preferencesMock.when(RecedePreferences::isDesaturated).thenReturn(false);

                assertThat(AlliancesView.INSTANCE.shouldUseIndependentStyle(
                        "hegemony", ALLIANCE_GROUPING)).isFalse();
            }
        }

        @Test
        void shouldUseIndependentStyleIsTrueForALoneFactionWhenDesaturated() {
            // Desaturate makes a non-allied faction adopt the whole independent style - its
            // independent opacities and widths, not just an independent recolour over faction ones.
            try (MockedStatic<RecedePreferences> preferencesMock =
                    mockStatic(RecedePreferences.class)) {
                preferencesMock.when(RecedePreferences::isDesaturated).thenReturn(true);

                assertThat(AlliancesView.INSTANCE.shouldUseIndependentStyle(
                        "hegemony", ALLIANCE_GROUPING)).isTrue();
            }
        }
    }

    @Nested
    class ResolveBlocStyleAdjustment {

        // A distinctive adjustment the shared recede is stubbed to return, so a test proves the view
        // passes it straight through rather than composing its own.
        private static final BlocStyleAdjustment RECEDED = new BlocStyleAdjustment(0.3, true);

        @Test
        void resolveBlocStyleAdjustmentIsNoneForAnAllianceBlocEvenWhenGroundRecedes() {
            // An alliance keeps its full colour: the view gates it to NONE before the shared recede
            // is consulted, so recede can never dim or desaturate an alliance.
            try (MockedStatic<RecedePreferences> preferencesMock =
                    mockStatic(RecedePreferences.class)) {
                preferencesMock.when(RecedePreferences::resolveRecedeAdjustment).thenReturn(RECEDED);

                assertThat(AlliancesView.INSTANCE.resolveBlocStyleAdjustment(
                        "rebel_pact", ALLIANCE_GROUPING)).isEqualTo(BlocStyleAdjustment.NONE);
            }
        }

        @Test
        void resolveBlocStyleAdjustmentTakesTheSharedRecedeForANonAllianceBloc() {
            // A non-allied faction is background ground, so the view returns exactly what the shared
            // recede resolves - the same adjustment every receding context applies, composed once.
            try (MockedStatic<RecedePreferences> preferencesMock =
                    mockStatic(RecedePreferences.class)) {
                preferencesMock.when(RecedePreferences::resolveRecedeAdjustment).thenReturn(RECEDED);

                assertThat(AlliancesView.INSTANCE.resolveBlocStyleAdjustment(
                        "hegemony", ALLIANCE_GROUPING)).isEqualTo(RECEDED);
            }
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
