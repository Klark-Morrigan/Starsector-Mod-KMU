package kmu.maplayers.ownermap.owners.holders.factions;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.ownermap.ContentInputs;
import kmu.maplayers.ownermap.ContentInputsFixtures;
import kmu.maplayers.ownermap.owners.OwnerPalette;
import kmu.maplayers.ownermap.preferences.OwnerNameFormatChoice;
import kmu.starsector.StarsectorFactionFixtures;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the owner reading of a layer painting one faction as one owner: every answer read off the
 * faction whose ID the owner is. Named by its own faction, only independent space and a desaturated
 * faction draw in the independent category, and nothing recedes of the layer's own accord.
 */
final class FactionOwnerReadingTests {

    private static final String HEGEMONY = "hegemony";

    private static final Color BRIGHT = new Color(140, 160, 220);
    private static final Color DARK = new Color(40, 60, 120);

    // The two adjustments the category discriminates on: one that desaturates and one that only
    // dims. The mute multiplier is arbitrary - only desaturation moves the category.
    private static final ElementStyleAdjustment DESATURATED = new ElementStyleAdjustment(0.3, true);
    private static final ElementStyleAdjustment MUTED_ONLY = new ElementStyleAdjustment(0.3, false);

    // A recede the bake sampled for a view's own backdrop, so a case can show this reading never
    // takes it.
    private static final ContentInputs RECEDING_INPUTS =
        ContentInputsFixtures.createInputsRecedingNonAllied(DESATURATED);

    @Nested
    class ResolvePalette {

        @Test
        void readsTheFactionsOwnShades() {

            var sectorMock = StarsectorFactionFixtures.buildSectorShadingFaction(HEGEMONY, BRIGHT, DARK);

            assertThat(new FactionOwnerReading(sectorMock).resolvePalette(HEGEMONY))
                .isEqualTo(new OwnerPalette(BRIGHT, DARK));
        }

        @Test
        void answersNoShadesForAFactionThatIsGone() {

            assertThat(new FactionOwnerReading(mock(SectorAPI.class)).resolvePalette("vanished"))
                .isNull();
        }

        @Test
        void answersNoShadesWithoutASector() {

            assertThat(new FactionOwnerReading(null).resolvePalette(HEGEMONY))
                .isNull();
        }
    }

    @Nested
    class ResolveName {

        @Test
        void readsTheLongNameForTheFullFormat() {

            var reading = new FactionOwnerReading(buildSectorNaming(HEGEMONY, "Hegemony", "The Hegemony"));

            assertThat(reading.resolveName(HEGEMONY, OwnerNameFormatChoice.FULL))
                .isEqualTo("The Hegemony");
        }

        @Test
        void readsTheShortNameForTheShortFormat() {

            var reading = new FactionOwnerReading(buildSectorNaming(HEGEMONY, "Hegemony", "The Hegemony"));

            assertThat(reading.resolveName(HEGEMONY, OwnerNameFormatChoice.SHORT))
                .isEqualTo("Hegemony");
        }

        @Test
        void answersNoNameWhenTheFactionDoesNotResolve() {
            // An owner with no faction behind it carries no name; the label fit then sizes its
            // stand-in band instead of drawing a name.
            var reading = new FactionOwnerReading(mock(SectorAPI.class));

            assertThat(reading.resolveName("ghost", OwnerNameFormatChoice.FULL))
                .isNull();
        }

        @Test
        void answersNoNameWithoutASector() {

            assertThat(new FactionOwnerReading(null).resolveName(HEGEMONY, OwnerNameFormatChoice.FULL))
                .isNull();
        }
    }

    @Nested
    class ResolveCrestPath {

        @Test
        void readsTheFactionsCrest() {

            var sectorMock = mock(SectorAPI.class);
            var factionMock = mock(FactionAPI.class);
            when(sectorMock.getFaction(HEGEMONY))
                .thenReturn(factionMock);
            when(factionMock.getCrest())
                .thenReturn("graphics/hegemony_crest.png");

            assertThat(new FactionOwnerReading(sectorMock).resolveCrestPath(HEGEMONY))
                .isEqualTo("graphics/hegemony_crest.png");
        }

        @Test
        void answersNoCrestWithoutASector() {

            assertThat(new FactionOwnerReading(null).resolveCrestPath(HEGEMONY))
                .isNull();
        }
    }

    @Nested
    class ResolveStyleAdjustment {

        @Test
        void recedesNothingEvenWhenTheBackdropRecedes() {
            // Every owner is a lone faction, so there is no figure for a backdrop to sit behind, and
            // receding would sink the whole sector rather than isolate anything.
            var reading = new FactionOwnerReading(mock(SectorAPI.class));

            assertThat(reading.resolveStyleAdjustment(HEGEMONY, RECEDING_INPUTS))
                .isEqualTo(ElementStyleAdjustment.NONE);
        }
    }

    @Nested
    class ResolveCategory {

        @Test
        void placesIndependentSpaceInTheIndependentCategory() {

            var reading = new FactionOwnerReading(mock(SectorAPI.class));

            assertThat(reading.resolveCategory(Factions.INDEPENDENT, ElementStyleAdjustment.NONE))
                .isEqualTo(OwnerMapCategory.INDEPENDENT);
        }

        @Test
        void placesAFactionInTheFactionCategory() {

            var reading = new FactionOwnerReading(mock(SectorAPI.class));

            assertThat(reading.resolveCategory(HEGEMONY, ElementStyleAdjustment.NONE))
                .isEqualTo(OwnerMapCategory.FACTION);
        }

        @Test
        void placesADesaturatedFactionInTheIndependentCategory() {
            // Desaturation means "read as backdrop", so the faction adopts the independent borders
            // and seams paired with the desaturation palette.
            var reading = new FactionOwnerReading(mock(SectorAPI.class));

            assertThat(reading.resolveCategory(HEGEMONY, DESATURATED))
                .isEqualTo(OwnerMapCategory.INDEPENDENT);
        }

        @Test
        void keepsAMerelyMutedFactionInTheFactionCategory() {
            // Muting only dims the active style; it never moves the category, so a dimmed faction
            // keeps its faction borders and seams.
            var reading = new FactionOwnerReading(mock(SectorAPI.class));

            assertThat(reading.resolveCategory(HEGEMONY, MUTED_ONLY))
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

            assertThat(new FactionOwnerReading(sectorMock).resolveUnownedColour())
                .isEqualTo(new Color(150, 150, 150));
        }

        @Test
        void fallsBackToGreyWithoutASector() {
            // A grey stand-in rather than a null, so a reading over no sector still paints its
            // unowned cells.
            assertThat(new FactionOwnerReading(null).resolveUnownedColour())
                .isEqualTo(new Color(128, 128, 128));
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

            assertThat(new FactionOwnerReading(sectorMock).resolveRecedePalette())
                .isEqualTo(new OwnerPalette(BRIGHT, DARK));
        }

        @Test
        void fallsBackToGreyShadesWithoutASector() {
            // The backdrop still has two shades to darken without a sector to read Independent from.
            assertThat(new FactionOwnerReading(null).resolveRecedePalette())
                .isEqualTo(new OwnerPalette(new Color(128, 128, 128), new Color(128, 128, 128)));
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
