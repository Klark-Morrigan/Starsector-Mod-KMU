package kmu.maplayers.ownermap.owners.holders.alliances;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmlib.colour.ColourPair;

import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.ownermap.ContentInputs;
import kmu.maplayers.ownermap.ContentInputsFixtures;
import kmu.maplayers.ownermap.owners.holders.base.HolderCategory;
import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;
import kmu.maplayers.ownermap.preferences.OwnerNameFormatChoice;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.Map;

import static kmu.starsector.SectorFactionFixtures.HEGEMONY;
import static kmu.starsector.SectorFactionFixtures.buildEmptySector;
import static kmu.starsector.SectorFactionFixtures.buildSectorKnowingHegemony;
import static kmu.starsector.SectorFactionFixtures.buildSectorShadingFaction;
import static kmu.starsector.SectorFactionFixtures.stubFaction;
import static kmu.starsector.SectorFactionFixtures.stubKnownFaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins what the alliance reading adds to the faction one: an alliance is named by its own name,
 * coloured and badged by its leading member, and always draws at full strength, while a lone faction
 * recedes by the view's backdrop only where some alliance exists.
 *
 * <p>A lone faction's shades and crest, and the two neutrals, are the faction reading's answers. They
 * are read back here once each, so a reading that answered them itself, or asked about the wrong ID,
 * fails here rather than only in play.
 */
final class AllianceOwnerReadingTests {

    // One alliance fusing two members, coloured and badged off its leading member and named by the
    // grouping; a faction outside the map stays its own lone bloc.
    private static final String ALLIANCE = "rebel_pact";
    private static final String LEADING_MEMBER = "rebels";
    private static final HolderGrouping ALLIANCE_GROUPING = new HolderGrouping(
        Map.of(LEADING_MEMBER, ALLIANCE, "pirates", ALLIANCE),
        Map.of(ALLIANCE, LEADING_MEMBER),
        Map.of(ALLIANCE, "Rebel Pact"));

    private static final String LONE_FACTION = HEGEMONY;

    private static final Color BRIGHT = new Color(140, 160, 220);
    private static final Color DARK = new Color(40, 60, 120);

    private static final ElementStyleAdjustment DESATURATED = new ElementStyleAdjustment(0.3, true);

    // The recede the bake sampled for the view's own backdrop, distinct from the identity so an
    // owner that took it can be told from one the gate spared.
    private static final ContentInputs RECEDING_INPUTS =
        ContentInputsFixtures.createInputsRecedingNonAllied(DESATURATED);

    @Nested
    class ResolvePalette {

        @Test
        void coloursAnAllianceByItsLeadingMember() {

            // ARRANGE
            var expectedPalette = new ColourPair(BRIGHT, DARK);

            var sectorMock = buildSectorShadingFaction(LEADING_MEMBER, BRIGHT, DARK);

            // ACT
            var actualPalette = new AllianceOwnerReading(sectorMock, ALLIANCE_GROUPING).resolvePalette(ALLIANCE);

            // ASSERT
            assertThat(actualPalette)
                .isEqualTo(expectedPalette);
        }

        @Test
        void coloursALoneFactionByItsOwnShades() {

            // ARRANGE
            var expectedPalette = new ColourPair(BRIGHT, DARK);

            var sectorMock = buildSectorShadingFaction(LONE_FACTION, BRIGHT, DARK);

            // ACT
            var actualPalette = new AllianceOwnerReading(sectorMock, ALLIANCE_GROUPING).resolvePalette(LONE_FACTION);

            // ASSERT
            assertThat(actualPalette)
                .isEqualTo(expectedPalette);
        }
    }

    @Nested
    class ResolveName {

        // An alliance's ID is not a faction ID, so its label comes from the grouping - the
        // sector is never consulted.
        @Test
        void readsTheAlliancesOwnName() {

            // ARRANGE
            var expectedName = "Rebel Pact";

            var reading = new AllianceOwnerReading(mock(SectorAPI.class), ALLIANCE_GROUPING);

            // ACT
            var actualName = reading.resolveName(ALLIANCE, OwnerNameFormatChoice.FULL);

            // ASSERT
            assertThat(actualName)
                .isEqualTo(expectedName);
        }

        @Test
        void readsALoneFactionByItsOwnName() {

            // ARRANGE
            var expectedName = "The Hegemony";

            var reading = new AllianceOwnerReading(buildSectorKnowingHegemony(), ALLIANCE_GROUPING);

            // ACT
            var actualName = reading.resolveName(LONE_FACTION, OwnerNameFormatChoice.FULL);

            // ASSERT
            assertThat(actualName)
                .isEqualTo(expectedName);
        }
    }

    @Nested
    class ResolveCrestPath {

        @Test
        void readsTheCrestOfTheAlliancesLeadingMember() {

            // ARRANGE
            var expectedCrestPath = "graphics/rebels_crest.png";

            var sectorMock = buildEmptySector();

            stubFaction(sectorMock, LEADING_MEMBER, "The Rebels", "graphics/rebels_crest.png");

            var reading = new AllianceOwnerReading(sectorMock, ALLIANCE_GROUPING);

            // ACT
            var actualCrestPath = reading.resolveCrestPath(ALLIANCE);

            // ASSERT
            assertThat(actualCrestPath)
                .isEqualTo(expectedCrestPath);
        }

        @Test
        void readsALoneFactionsOwnCrest() {

            // ARRANGE
            var expectedCrestPath = "graphics/hegemony_crest.png";

            var reading = new AllianceOwnerReading(buildSectorKnowingHegemony(), ALLIANCE_GROUPING);

            // ACT
            var actualCrestPath = reading.resolveCrestPath(LONE_FACTION);

            // ASSERT
            assertThat(actualCrestPath)
                .isEqualTo(expectedCrestPath);
        }
    }

    @Nested
    class ResolveStyleAdjustment {

        // A lone faction is backdrop once some alliance stands as the figure, so it takes exactly
        // the recede the rebuild sampled for the view's backdrop.
        @Test
        void takesTheSampledBackdropRecedeForALoneFactionBesideAnAlliance() {

            // ARRANGE
            var expectedAdjustment = DESATURATED;

            var reading = new AllianceOwnerReading(mock(SectorAPI.class), ALLIANCE_GROUPING);

            // ACT
            var actualAdjustment = reading.resolveStyleAdjustment(LONE_FACTION, RECEDING_INPUTS);

            // ASSERT
            assertThat(actualAdjustment)
                .isEqualTo(expectedAdjustment);
        }

        @Test
        void recedesNoAllianceEvenWhenTheBackdropRecedes() {

            // ARRANGE
            var expectedAdjustment = ElementStyleAdjustment.NONE;

            var reading = new AllianceOwnerReading(mock(SectorAPI.class), ALLIANCE_GROUPING);

            // ACT
            var actualAdjustment = reading.resolveStyleAdjustment(ALLIANCE, RECEDING_INPUTS);

            // ASSERT
            assertThat(actualAdjustment)
                .isEqualTo(expectedAdjustment);
        }

        // No alliance means no figure, so receding would sink the whole sector rather than
        // isolate anything - a fresh Nex campaign's case before diplomacy has formed one.
        @Test
        void recedesNothingWhereNoAllianceExists() {

            // ARRANGE
            var expectedAdjustment = ElementStyleAdjustment.NONE;

            var reading = new AllianceOwnerReading(mock(SectorAPI.class), HolderGrouping.identity());

            // ACT
            var actualAdjustment = reading.resolveStyleAdjustment(LONE_FACTION, RECEDING_INPUTS);

            // ASSERT
            assertThat(actualAdjustment)
                .isEqualTo(expectedAdjustment);
        }
    }

    @Nested
    class ResolveCategory {

        // An alliance always paints in the faction category so it stands out, whatever the
        // adjustment says.
        @Test
        void keepsADesaturatedAllianceAtFullStrength() {

            // ARRANGE
            var expectedCategory = HolderCategory.FACTION;

            var reading = new AllianceOwnerReading(mock(SectorAPI.class), ALLIANCE_GROUPING);

            // ACT
            var actualCategory = reading.resolveCategory(ALLIANCE, DESATURATED);

            // ASSERT
            assertThat(actualCategory)
                .isEqualTo(expectedCategory);
        }

        // A lone faction's category is the faction reading's answer, pinned in its own suite, so
        // one case shows the delegation. Desaturated, because that is where a lone faction's
        // answer parts from an alliance's.
        @Test
        void placesADesaturatedLoneFactionAsTheFactionReadingDoes() {

            // ARRANGE
            var expectedCategory = HolderCategory.INDEPENDENT;

            var reading = new AllianceOwnerReading(mock(SectorAPI.class), ALLIANCE_GROUPING);

            // ACT
            var actualCategory = reading.resolveCategory(LONE_FACTION, DESATURATED);

            // ASSERT
            assertThat(actualCategory)
                .isEqualTo(expectedCategory);
        }
    }

    @Nested
    class ResolveUnownedColour {

        // A system nobody holds paints the same on a layer painting alliances as on one painting
        // factions.
        @Test
        void readsTheNeutralFactionsColour() {

            // ARRANGE
            var expectedColour = new Color(150, 150, 150);

            var sectorMock = buildEmptySector();

            when(stubKnownFaction(sectorMock, Factions.NEUTRAL).getBaseUIColor())
                .thenReturn(new Color(150, 150, 150));

            // ACT
            var actualColour = new AllianceOwnerReading(sectorMock, ALLIANCE_GROUPING).resolveUnownedColour();

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

            var sectorMock = buildSectorShadingFaction(Factions.INDEPENDENT, BRIGHT, DARK);

            // ACT
            var actualPalette = new AllianceOwnerReading(sectorMock, ALLIANCE_GROUPING).resolveRecedePalette();

            // ASSERT
            assertThat(actualPalette)
                .isEqualTo(expectedPalette);
        }
    }
}
