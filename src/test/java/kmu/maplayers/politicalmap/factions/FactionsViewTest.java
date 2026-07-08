package kmu.maplayers.politicalmap.factions;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.politics.OwnershipGrouping;
import kmu.settings.FactionNameFormatChoice;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the faction view's render rules - the identity case the shared political-map
 * pipeline was carved out of: the grouping is identity, only independent space is drawn
 * in the muted style, and a bloc is labelled by its own faction's display name in the
 * player's chosen form. Reproducing these here is what lets the shared pipeline read the
 * faction view through {@link PoliticalMapView} rather than the inlined tests it used to.
 */
final class FactionsViewTest {

    // The faction view ignores its grouping argument (its own grouping is always identity),
    // so any grouping stands in where the interface demands one.
    private static final OwnershipGrouping ANY_GROUPING = OwnershipGrouping.identity();

    @Nested
    class ResolveGrouping {

        @Test
        void resolveGroupingReturnsTheIdentityGrouping() {
            // Every faction is its own bloc, so the pipeline resolves plain faction ownership.
            assertThat(FactionsView.INSTANCE.resolveGrouping())
                    .isSameAs(OwnershipGrouping.identity());
        }
    }

    @Nested
    class ShouldUseIndependentStyle {

        @Test
        void shouldUseIndependentStyleIsTrueForIndependentSpace() {
            assertThat(FactionsView.INSTANCE.shouldUseIndependentStyle(
                    Factions.INDEPENDENT, ANY_GROUPING)).isTrue();
        }

        @Test
        void shouldUseIndependentStyleIsFalseForACoreFaction() {
            assertThat(FactionsView.INSTANCE.shouldUseIndependentStyle(
                    "hegemony", ANY_GROUPING)).isFalse();
        }
    }

    @Nested
    class ResolveName {

        @Test
        void resolveNameReadsTheLongNameForTheFullFormat() {
            var sectorMock = mock(SectorAPI.class);
            var factionMock = mock(FactionAPI.class);
            when(sectorMock.getFaction("hegemony")).thenReturn(factionMock);
            when(factionMock.getDisplayNameLong()).thenReturn("The Hegemony");

            assertThat(FactionsView.INSTANCE.resolveName("hegemony", ANY_GROUPING, sectorMock,
                    FactionNameFormatChoice.FULL)).isEqualTo("The Hegemony");
        }

        @Test
        void resolveNameReadsTheShortNameForTheShortFormat() {
            var sectorMock = mock(SectorAPI.class);
            var factionMock = mock(FactionAPI.class);
            when(sectorMock.getFaction("hegemony")).thenReturn(factionMock);
            when(factionMock.getDisplayName()).thenReturn("Hegemony");

            assertThat(FactionsView.INSTANCE.resolveName("hegemony", ANY_GROUPING, sectorMock,
                    FactionNameFormatChoice.SHORT)).isEqualTo("Hegemony");
        }

        @Test
        void resolveNameIsNullWhenTheFactionDoesNotResolve() {
            // A bloc id with no faction behind it carries no name; the label fit then sizes
            // its stand-in band instead of drawing a name.
            var sectorMock = mock(SectorAPI.class);
            when(sectorMock.getFaction("ghost")).thenReturn(null);

            assertThat(FactionsView.INSTANCE.resolveName("ghost", ANY_GROUPING, sectorMock,
                    FactionNameFormatChoice.FULL)).isNull();
        }
    }
}
