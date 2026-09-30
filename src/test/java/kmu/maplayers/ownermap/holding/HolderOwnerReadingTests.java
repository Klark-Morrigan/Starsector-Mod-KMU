package kmu.maplayers.ownermap.holding;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.ownermap.ContentInputs;
import kmu.maplayers.ownermap.ContentInputsFixtures;
import kmu.maplayers.ownermap.owners.OwnerPalette;
import kmu.maplayers.ownermap.preferences.FactionNameFormatChoice;
import kmu.maplayers.ownermap.render.style.OwnerMapCategory;
import kmu.starsector.StarsectorFactionFixtures;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the owner reading every layer painting holders shares: a bloc read off the faction it
 * paints as, under the grouping handed in.
 *
 * <p>Two groupings are posed throughout. Under the identity grouping every bloc is a lone faction:
 * named by its own faction, only independent space and a desaturated faction draw in the
 * independent category, and nothing recedes of the layer's own accord. Under a grouping folding
 * factions together the group rules join in: a group is named by its own name and coloured and
 * badged by its leading member, it always draws at full strength, and a lone faction recedes by
 * the view's backdrop only where some group exists.
 */
final class HolderOwnerReadingTests {

    // One group fusing two members, coloured and badged off its leading member and named by the
    // grouping; a faction outside the map stays its own lone bloc.
    private static final String GROUP = "rebel_pact";
    private static final String LEADING_MEMBER = "rebels";
    private static final HolderGrouping GROUP_GROUPING = new HolderGrouping(
        Map.of(LEADING_MEMBER, GROUP, "pirates", GROUP),
        Map.of(GROUP, LEADING_MEMBER),
        Map.of(GROUP, "Rebel Pact"));

    private static final Color BRIGHT = new Color(140, 160, 220);
    private static final Color DARK = new Color(40, 60, 120);

    // The two adjustments the category discriminates on: one that desaturates and one that only
    // dims. The mute multiplier is arbitrary - only desaturation moves the category.
    private static final ElementStyleAdjustment DESATURATED = new ElementStyleAdjustment(0.3, true);
    private static final ElementStyleAdjustment MUTED_ONLY = new ElementStyleAdjustment(0.3, false);

    // The recede the bake sampled for the view's own backdrop, distinct from the identity so an
    // owner that took it can be told from one the gate spared.
    private static final ContentInputs RECEDING_INPUTS =
        ContentInputsFixtures.createInputsRecedingNonAllied(DESATURATED);

    @Nested
    class ResolvePalette {

        @Test
        void coloursAGroupByItsLeadingMember() {

            var sectorMock = StarsectorFactionFixtures.buildSectorShadingFaction(
                LEADING_MEMBER,
                BRIGHT,
                DARK);

            assertThat(new HolderOwnerReading(sectorMock, GROUP_GROUPING).resolvePalette(GROUP))
                .isEqualTo(new OwnerPalette(BRIGHT, DARK));
        }

        @Test
        void answersNoShadesForABlocWhoseColourFactionIsGone() {

            var reading = new HolderOwnerReading(mock(SectorAPI.class), HolderGrouping.identity());

            assertThat(reading.resolvePalette("vanished"))
                .isNull();
        }
    }

    @Nested
    class ResolveName {

        @Test
        void readsTheGroupsNameForAGroup() {
            // A group's ID is not a faction ID, so its label comes from the grouping - the sector is
            // never consulted.
            var reading = new HolderOwnerReading(mock(SectorAPI.class), GROUP_GROUPING);

            assertThat(reading.resolveName(GROUP, FactionNameFormatChoice.FULL))
                .isEqualTo("Rebel Pact");
        }

        @Test
        void readsALoneFactionsLongNameForTheFullFormat() {

            var reading = new HolderOwnerReading(
                buildSectorNaming("hegemony", "Hegemony", "The Hegemony"),
                GROUP_GROUPING);

            assertThat(reading.resolveName("hegemony", FactionNameFormatChoice.FULL))
                .isEqualTo("The Hegemony");
        }

        @Test
        void readsALoneFactionsShortNameForTheShortFormat() {

            var reading = new HolderOwnerReading(
                buildSectorNaming("hegemony", "Hegemony", "The Hegemony"),
                HolderGrouping.identity());

            assertThat(reading.resolveName("hegemony", FactionNameFormatChoice.SHORT))
                .isEqualTo("Hegemony");
        }

        @Test
        void answersNoNameWhenTheFactionDoesNotResolve() {
            // A bloc with no faction behind it carries no name; the label fit then sizes its
            // stand-in band instead of drawing a name.
            var reading = new HolderOwnerReading(mock(SectorAPI.class), HolderGrouping.identity());

            assertThat(reading.resolveName("ghost", FactionNameFormatChoice.FULL))
                .isNull();
        }

        @Test
        void answersNoNameWithoutASector() {

            var reading = new HolderOwnerReading(null, HolderGrouping.identity());

            assertThat(reading.resolveName("hegemony", FactionNameFormatChoice.FULL))
                .isNull();
        }
    }

    @Nested
    class ResolveCrestPath {

        @Test
        void readsTheCrestOfTheGroupsLeadingMember() {

            var sectorMock = mock(SectorAPI.class);
            var factionMock = mock(FactionAPI.class);
            when(sectorMock.getFaction(LEADING_MEMBER))
                .thenReturn(factionMock);
            when(factionMock.getCrest())
                .thenReturn("graphics/rebels_crest.png");

            var reading = new HolderOwnerReading(sectorMock, GROUP_GROUPING);

            assertThat(reading.resolveCrestPath(GROUP))
                .isEqualTo("graphics/rebels_crest.png");
        }

        @Test
        void answersNoCrestWithoutASector() {

            var reading = new HolderOwnerReading(null, HolderGrouping.identity());

            assertThat(reading.resolveCrestPath("hegemony"))
                .isNull();
        }
    }

    @Nested
    class ResolveStyleAdjustment {

        @Test
        void takesTheSampledBackdropRecedeForALoneFactionBesideAGroup() {
            // A lone faction is backdrop once some group stands as the figure, so it takes exactly
            // the recede the rebuild sampled for the view's backdrop.
            var reading = new HolderOwnerReading(mock(SectorAPI.class), GROUP_GROUPING);

            assertThat(reading.resolveStyleAdjustment("hegemony", RECEDING_INPUTS))
                .isEqualTo(DESATURATED);
        }

        @Test
        void recedesNoGroupEvenWhenTheBackdropRecedes() {

            var reading = new HolderOwnerReading(mock(SectorAPI.class), GROUP_GROUPING);

            assertThat(reading.resolveStyleAdjustment(GROUP, RECEDING_INPUTS))
                .isEqualTo(ElementStyleAdjustment.NONE);
        }

        @Test
        void recedesNothingWhereNoGroupExists() {
            // No group means no figure, so receding would sink the whole sector rather than isolate
            // anything - the identity grouping's case, and a fresh Nex campaign's before diplomacy
            // has formed a single group.
            var reading = new HolderOwnerReading(mock(SectorAPI.class), HolderGrouping.identity());

            assertThat(reading.resolveStyleAdjustment("hegemony", RECEDING_INPUTS))
                .isEqualTo(ElementStyleAdjustment.NONE);
        }
    }

    @Nested
    class ResolveCategory {

        @Test
        void placesIndependentSpaceInTheIndependentCategory() {

            var reading = new HolderOwnerReading(mock(SectorAPI.class), GROUP_GROUPING);

            assertThat(reading.resolveCategory(Factions.INDEPENDENT, ElementStyleAdjustment.NONE))
                .isEqualTo(OwnerMapCategory.INDEPENDENT);
        }

        @Test
        void placesALoneFactionInTheFactionCategory() {

            var reading = new HolderOwnerReading(mock(SectorAPI.class), HolderGrouping.identity());

            assertThat(reading.resolveCategory("hegemony", ElementStyleAdjustment.NONE))
                .isEqualTo(OwnerMapCategory.FACTION);
        }

        @Test
        void placesADesaturatedLoneFactionInTheIndependentCategory() {
            // Desaturation means "read as backdrop", so the faction adopts the independent borders
            // and seams paired with the desaturation palette - under the identity grouping as much
            // as beside a group.
            var reading = new HolderOwnerReading(mock(SectorAPI.class), HolderGrouping.identity());

            assertThat(reading.resolveCategory("hegemony", DESATURATED))
                .isEqualTo(OwnerMapCategory.INDEPENDENT);
        }

        @Test
        void keepsAMerelyMutedLoneFactionInTheFactionCategory() {
            // Muting only dims the active style; it never moves the category, so a dimmed faction
            // keeps its faction borders and seams.
            var reading = new HolderOwnerReading(mock(SectorAPI.class), GROUP_GROUPING);

            assertThat(reading.resolveCategory("hegemony", MUTED_ONLY))
                .isEqualTo(OwnerMapCategory.FACTION);
        }

        @Test
        void keepsADesaturatedGroupAtFullStrength() {
            // A group always paints in the faction category so it stands out, whatever the
            // adjustment says.
            var reading = new HolderOwnerReading(mock(SectorAPI.class), GROUP_GROUPING);

            assertThat(reading.resolveCategory(GROUP, DESATURATED))
                .isEqualTo(OwnerMapCategory.FACTION);
        }
    }

    @Nested
    class ResolveUnownedColour {

        @Test
        void readsTheNeutralFactionsColour() {

            var sectorMock = mock(SectorAPI.class);
            var neutralMock = mock(FactionAPI.class);
            when(sectorMock.getFaction(Factions.NEUTRAL))
                .thenReturn(neutralMock);
            when(neutralMock.getBaseUIColor())
                .thenReturn(new Color(150, 150, 150));

            var reading = new HolderOwnerReading(sectorMock, HolderGrouping.identity());

            assertThat(reading.resolveUnownedColour())
                .isEqualTo(new Color(150, 150, 150));
        }
    }

    @Nested
    class ResolveRecedePalette {

        @Test
        void readsIndependentsOwnShades() {

            var sectorMock = StarsectorFactionFixtures.buildSectorShadingFaction(
                Factions.INDEPENDENT,
                BRIGHT,
                DARK);

            var reading = new HolderOwnerReading(sectorMock, HolderGrouping.identity());

            assertThat(reading.resolveRecedePalette())
                .isEqualTo(new OwnerPalette(BRIGHT, DARK));
        }
    }

    // A sector naming one faction by both its forms.
    private static SectorAPI buildSectorNaming(String factionId, String shortName, String longName) {

        var sectorMock = mock(SectorAPI.class);
        var factionMock = mock(FactionAPI.class);

        when(sectorMock.getFaction(factionId))
            .thenReturn(factionMock);
        when(factionMock.getDisplayName())
            .thenReturn(shortName);
        when(factionMock.getDisplayNameLong())
            .thenReturn(longName);

        return sectorMock;
    }
}
