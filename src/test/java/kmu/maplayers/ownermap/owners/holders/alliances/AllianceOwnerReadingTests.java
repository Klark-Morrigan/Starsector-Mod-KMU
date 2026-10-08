package kmu.maplayers.ownermap.owners.holders.alliances;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.ownermap.ContentInputs;
import kmu.maplayers.ownermap.ContentInputsFixtures;
import kmu.maplayers.ownermap.owners.OwnerPalette;
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

            var sectorMock = buildSectorShadingFaction(LEADING_MEMBER, BRIGHT, DARK);

            assertThat(new AllianceOwnerReading(sectorMock, ALLIANCE_GROUPING).resolvePalette(ALLIANCE))
                .isEqualTo(new OwnerPalette(BRIGHT, DARK));
        }

        @Test
        void coloursALoneFactionByItsOwnShades() {

            var sectorMock = buildSectorShadingFaction(LONE_FACTION, BRIGHT, DARK);

            assertThat(new AllianceOwnerReading(sectorMock, ALLIANCE_GROUPING).resolvePalette(LONE_FACTION))
                .isEqualTo(new OwnerPalette(BRIGHT, DARK));
        }
    }

    @Nested
    class ResolveName {

        @Test
        void readsTheAlliancesOwnName() {
            // An alliance's ID is not a faction ID, so its label comes from the grouping - the
            // sector is never consulted.
            var reading = new AllianceOwnerReading(mock(SectorAPI.class), ALLIANCE_GROUPING);

            assertThat(reading.resolveName(ALLIANCE, OwnerNameFormatChoice.FULL))
                .isEqualTo("Rebel Pact");
        }

        @Test
        void readsALoneFactionByItsOwnName() {

            var reading = new AllianceOwnerReading(buildSectorKnowingHegemony(), ALLIANCE_GROUPING);

            assertThat(reading.resolveName(LONE_FACTION, OwnerNameFormatChoice.FULL))
                .isEqualTo("The Hegemony");
        }
    }

    @Nested
    class ResolveCrestPath {

        @Test
        void readsTheCrestOfTheAlliancesLeadingMember() {

            var sectorMock = buildEmptySector();

            stubFaction(sectorMock, LEADING_MEMBER, "The Rebels", "graphics/rebels_crest.png");

            var reading = new AllianceOwnerReading(sectorMock, ALLIANCE_GROUPING);

            assertThat(reading.resolveCrestPath(ALLIANCE))
                .isEqualTo("graphics/rebels_crest.png");
        }

        @Test
        void readsALoneFactionsOwnCrest() {

            var reading = new AllianceOwnerReading(buildSectorKnowingHegemony(), ALLIANCE_GROUPING);

            assertThat(reading.resolveCrestPath(LONE_FACTION))
                .isEqualTo("graphics/hegemony_crest.png");
        }
    }

    @Nested
    class ResolveStyleAdjustment {

        @Test
        void takesTheSampledBackdropRecedeForALoneFactionBesideAnAlliance() {
            // A lone faction is backdrop once some alliance stands as the figure, so it takes exactly
            // the recede the rebuild sampled for the view's backdrop.
            var reading = new AllianceOwnerReading(mock(SectorAPI.class), ALLIANCE_GROUPING);

            assertThat(reading.resolveStyleAdjustment(LONE_FACTION, RECEDING_INPUTS))
                .isEqualTo(DESATURATED);
        }

        @Test
        void recedesNoAllianceEvenWhenTheBackdropRecedes() {

            var reading = new AllianceOwnerReading(mock(SectorAPI.class), ALLIANCE_GROUPING);

            assertThat(reading.resolveStyleAdjustment(ALLIANCE, RECEDING_INPUTS))
                .isEqualTo(ElementStyleAdjustment.NONE);
        }

        @Test
        void recedesNothingWhereNoAllianceExists() {
            // No alliance means no figure, so receding would sink the whole sector rather than
            // isolate anything - a fresh Nex campaign's case before diplomacy has formed one.
            var reading = new AllianceOwnerReading(mock(SectorAPI.class), HolderGrouping.identity());

            assertThat(reading.resolveStyleAdjustment(LONE_FACTION, RECEDING_INPUTS))
                .isEqualTo(ElementStyleAdjustment.NONE);
        }
    }

    @Nested
    class ResolveCategory {

        @Test
        void keepsADesaturatedAllianceAtFullStrength() {
            // An alliance always paints in the faction category so it stands out, whatever the
            // adjustment says.
            var reading = new AllianceOwnerReading(mock(SectorAPI.class), ALLIANCE_GROUPING);

            assertThat(reading.resolveCategory(ALLIANCE, DESATURATED))
                .isEqualTo(HolderCategory.FACTION);
        }

        @Test
        void placesADesaturatedLoneFactionAsTheFactionReadingDoes() {

            var reading = new AllianceOwnerReading(mock(SectorAPI.class), ALLIANCE_GROUPING);

            assertThat(reading.resolveCategory(LONE_FACTION, DESATURATED))
                .isEqualTo(HolderCategory.INDEPENDENT);
        }

        @Test
        void placesALoneFactionInTheFactionCategory() {

            var reading = new AllianceOwnerReading(mock(SectorAPI.class), ALLIANCE_GROUPING);

            assertThat(reading.resolveCategory(LONE_FACTION, ElementStyleAdjustment.NONE))
                .isEqualTo(HolderCategory.FACTION);
        }

        @Test
        void placesIndependentSpaceInTheIndependentCategory() {

            var reading = new AllianceOwnerReading(mock(SectorAPI.class), ALLIANCE_GROUPING);

            assertThat(reading.resolveCategory(Factions.INDEPENDENT, ElementStyleAdjustment.NONE))
                .isEqualTo(HolderCategory.INDEPENDENT);
        }
    }

    @Nested
    class ResolveUnownedColour {

        @Test
        void readsTheNeutralFactionsColour() {
            // A system nobody holds paints the same on a layer painting alliances as on one painting
            // factions.
            var sectorMock = buildEmptySector();

            when(stubKnownFaction(sectorMock, Factions.NEUTRAL).getBaseUIColor())
                .thenReturn(new Color(150, 150, 150));

            assertThat(new AllianceOwnerReading(sectorMock, ALLIANCE_GROUPING).resolveUnownedColour())
                .isEqualTo(new Color(150, 150, 150));
        }
    }

    @Nested
    class ResolveRecedePalette {

        @Test
        void readsIndependentsOwnShades() {

            var sectorMock = buildSectorShadingFaction(Factions.INDEPENDENT, BRIGHT, DARK);

            assertThat(new AllianceOwnerReading(sectorMock, ALLIANCE_GROUPING).resolveRecedePalette())
                .isEqualTo(new OwnerPalette(BRIGHT, DARK));
        }
    }
}
