package kmu.maplayers.ownermap.owners.holders.factions;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmlib.colour.ColourPair;

import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.ownermap.ContentInputs;
import kmu.maplayers.ownermap.ContentInputsFixtures;
import kmu.maplayers.ownermap.owners.holders.base.HolderCategory;
import kmu.maplayers.ownermap.preferences.OwnerNameFormatChoice;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static kmu.starsector.SectorFactionFixtures.HEGEMONY;
import static kmu.starsector.SectorFactionFixtures.buildEmptySector;
import static kmu.starsector.SectorFactionFixtures.buildSectorKnowingHegemony;
import static kmu.starsector.SectorFactionFixtures.buildSectorShadingFaction;
import static kmu.starsector.SectorFactionFixtures.stubKnownFaction;
import static kmu.starsector.SectorFactionFixtures.stubNamedFaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the owner reading of a layer painting one faction as one owner: every answer read off the
 * faction whose ID the owner is. Named by its own faction, only independent space and a desaturated
 * faction draw in the independent category, and nothing recedes of the layer's own accord.
 */
final class FactionOwnerReadingTests {

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

            // ARRANGE
            var expectedPalette = new ColourPair(BRIGHT, DARK);

            var sectorMock = buildSectorShadingFaction(HEGEMONY, BRIGHT, DARK);

            // ACT
            var actualPalette = new FactionOwnerReading(sectorMock).resolvePalette(HEGEMONY);

            // ASSERT
            assertThat(actualPalette)
                .isEqualTo(expectedPalette);
        }

        @Test
        void answersNoShadesForAFactionThatIsGone() {

            // ACT
            var actualPalette = new FactionOwnerReading(mock(SectorAPI.class)).resolvePalette("vanished");

            // ASSERT
            assertThat(actualPalette)
                .isNull();
        }

        @Test
        void answersNoShadesWithoutASector() {

            // ACT
            var actualPalette = new FactionOwnerReading(null).resolvePalette(HEGEMONY);

            // ASSERT
            assertThat(actualPalette)
                .isNull();
        }
    }

    @Nested
    class ResolveName {

        @Test
        void readsTheLongNameForTheFullFormat() {

            // ARRANGE
            var expectedName = "The Hegemony";

            var reading = new FactionOwnerReading(buildSectorNamingHegemony());

            // ACT
            var actualName = reading.resolveName(HEGEMONY, OwnerNameFormatChoice.FULL);

            // ASSERT
            assertThat(actualName)
                .isEqualTo(expectedName);
        }

        @Test
        void readsTheShortNameForTheShortFormat() {

            // ARRANGE
            var expectedName = "Hegemony";

            var reading = new FactionOwnerReading(buildSectorNamingHegemony());

            // ACT
            var actualName = reading.resolveName(HEGEMONY, OwnerNameFormatChoice.SHORT);

            // ASSERT
            assertThat(actualName)
                .isEqualTo(expectedName);
        }

        // An owner with no faction behind it carries no name; the label fit then sizes its
        // stand-in band instead of drawing a name.
        @Test
        void answersNoNameWhenTheFactionDoesNotResolve() {

            // ARRANGE
            var reading = new FactionOwnerReading(mock(SectorAPI.class));

            // ACT
            var actualName = reading.resolveName("ghost", OwnerNameFormatChoice.FULL);

            // ASSERT
            assertThat(actualName)
                .isNull();
        }

        @Test
        void answersNoNameWithoutASector() {

            // ACT
            var actualName = new FactionOwnerReading(null).resolveName(HEGEMONY, OwnerNameFormatChoice.FULL);

            // ASSERT
            assertThat(actualName)
                .isNull();
        }
    }

    @Nested
    class ResolveCrestPath {

        @Test
        void readsTheFactionsCrest() {

            // ARRANGE
            var expectedCrestPath = "graphics/hegemony_crest.png";

            var sectorMock = buildSectorKnowingHegemony();

            // ACT
            var actualCrestPath = new FactionOwnerReading(sectorMock).resolveCrestPath(HEGEMONY);

            // ASSERT
            assertThat(actualCrestPath)
                .isEqualTo(expectedCrestPath);
        }

        @Test
        void answersNoCrestWhenTheFactionDoesNotResolve() {

            // ARRANGE
            var reading = new FactionOwnerReading(buildSectorKnowingHegemony());

            // ACT
            var actualCrestPath = reading.resolveCrestPath("ghost");

            // ASSERT
            assertThat(actualCrestPath)
                .isNull();
        }

        @Test
        void answersNoCrestWithoutASector() {

            // ACT
            var actualCrestPath = new FactionOwnerReading(null).resolveCrestPath(HEGEMONY);

            // ASSERT
            assertThat(actualCrestPath)
                .isNull();
        }
    }

    @Nested
    class ResolveStyleAdjustment {

        // Every owner is a lone faction, so there is no figure for a backdrop to sit behind, and
        // receding would sink the whole sector rather than isolate anything.
        @Test
        void recedesNothingEvenWhenTheBackdropRecedes() {

            // ARRANGE
            var expectedAdjustment = ElementStyleAdjustment.NONE;

            var reading = new FactionOwnerReading(mock(SectorAPI.class));

            // ACT
            var actualAdjustment = reading.resolveStyleAdjustment(HEGEMONY, RECEDING_INPUTS);

            // ASSERT
            assertThat(actualAdjustment)
                .isEqualTo(expectedAdjustment);
        }
    }

    @Nested
    class ResolveCategory {

        @Test
        void placesIndependentSpaceInTheIndependentCategory() {

            // ARRANGE
            var expectedCategory = HolderCategory.INDEPENDENT;

            var reading = new FactionOwnerReading(mock(SectorAPI.class));

            // ACT
            var actualCategory = reading.resolveCategory(Factions.INDEPENDENT, ElementStyleAdjustment.NONE);

            // ASSERT
            assertThat(actualCategory)
                .isEqualTo(expectedCategory);
        }

        @Test
        void placesAFactionInTheFactionCategory() {

            // ARRANGE
            var expectedCategory = HolderCategory.FACTION;

            var reading = new FactionOwnerReading(mock(SectorAPI.class));

            // ACT
            var actualCategory = reading.resolveCategory(HEGEMONY, ElementStyleAdjustment.NONE);

            // ASSERT
            assertThat(actualCategory)
                .isEqualTo(expectedCategory);
        }

        // Desaturation means "read as backdrop", so the faction adopts the independent borders
        // and seams paired with the desaturation palette.
        @Test
        void placesADesaturatedFactionInTheIndependentCategory() {

            // ARRANGE
            var expectedCategory = HolderCategory.INDEPENDENT;

            var reading = new FactionOwnerReading(mock(SectorAPI.class));

            // ACT
            var actualCategory = reading.resolveCategory(HEGEMONY, DESATURATED);

            // ASSERT
            assertThat(actualCategory)
                .isEqualTo(expectedCategory);
        }

        // Muting only dims the active style; it never moves the category, so a dimmed faction
        // keeps its faction borders and seams.
        @Test
        void keepsAMerelyMutedFactionInTheFactionCategory() {

            // ARRANGE
            var expectedCategory = HolderCategory.FACTION;

            var reading = new FactionOwnerReading(mock(SectorAPI.class));

            // ACT
            var actualCategory = reading.resolveCategory(HEGEMONY, MUTED_ONLY);

            // ASSERT
            assertThat(actualCategory)
                .isEqualTo(expectedCategory);
        }
    }

    @Nested
    class ResolveUnownedColour {

        @Test
        void readsTheNeutralFactionsColour() {

            // ARRANGE
            var expectedColour = new Color(150, 150, 150);

            var sectorMock = buildEmptySector();

            when(stubKnownFaction(sectorMock, Factions.NEUTRAL).getBaseUIColor())
                .thenReturn(new Color(150, 150, 150));

            // ACT
            var actualColour = new FactionOwnerReading(sectorMock).resolveUnownedColour();

            // ASSERT
            assertThat(actualColour)
                .isEqualTo(expectedColour);
        }

        // A grey stand-in rather than a null, so a reading over no sector still paints its
        // unowned cells.
        @Test
        void fallsBackToGreyWithoutASector() {

            // ARRANGE
            var expectedColour = new Color(128, 128, 128);

            // ACT
            var actualColour = new FactionOwnerReading(null).resolveUnownedColour();

            // ASSERT
            assertThat(actualColour)
                .isEqualTo(expectedColour);
        }
    }

    @Nested
    class ResolveRecedePalette {

        @Test
        void readsIndependentsOwnShades() {

            // ARRANGE
            var expectedPalette = new ColourPair(BRIGHT, DARK);

            var sectorMock = buildSectorShadingFaction(
                Factions.INDEPENDENT,
                BRIGHT,
                DARK);

            // ACT
            var actualPalette = new FactionOwnerReading(sectorMock).resolveRecedePalette();

            // ASSERT
            assertThat(actualPalette)
                .isEqualTo(expectedPalette);
        }

        // The backdrop still has two shades to darken without a sector to read Independent from.
        @Test
        void fallsBackToGreyShadesWithoutASector() {

            // ARRANGE
            var expectedPalette = new ColourPair(new Color(128, 128, 128), new Color(128, 128, 128));

            // ACT
            var actualPalette = new FactionOwnerReading(null).resolveRecedePalette();

            // ASSERT
            assertThat(actualPalette)
                .isEqualTo(expectedPalette);
        }
    }

    // A sector naming the Hegemony by both its forms.
    private static SectorAPI buildSectorNamingHegemony() {

        var sectorMock = buildEmptySector();

        stubNamedFaction(sectorMock, HEGEMONY, "Hegemony", "The Hegemony");

        return sectorMock;
    }
}
