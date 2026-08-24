package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmlib.starsector.systems.claims.FactionClaimStanding;
import kmlib.starsector.systems.claims.SystemClaimBreakdown;
import kmlib.starsector.systems.claims.VanillaClaimBreakdownReader;
import kmlib.starsector.ui.widgets.tooltip.TooltipSection;
import kmlib.testfixtures.starsector.systems.claims.ClaimBreakdownReaderFake;

import kmu.maplayers.base.tooltip.CellTooltipPaletteFake;
import kmu.maplayers.base.tooltip.CellTooltipRowReads;
import kmu.maplayers.base.visibility.MapVisibilityRules;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.PoliticalMapViewRegistry;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderPass;
import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;

import static kmu.maplayers.SectorScenarioFixtures.placeDerelictIn;
import static kmu.maplayers.base.visibility.ColonyVisibility.BASE_FOG;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildFaction;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildOnlySystem;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildStabilityWeightedRules;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildVisibleMarket;
import static kmu.maplayers.politicalmap.base.tooltip.SectorFactionsFake.stubFaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins what a derelict does to a hover box, which is the one place the two projections beneath the
 * map visibly disagree about one system.
 *
 * <p>A hulk is named in the listing - somebody has seen it, and the box's business is everything the
 * player may be told about - and nobody has ever lived aboard it, so the status line above that
 * listing goes on saying the system is unpopulated. Read apart, either surface looks like a bug: a
 * banner calling a system empty over a box that names somebody in it is exactly the pairing every
 * other case in this package rules out. Read together, it is the true reading of a system with one
 * wreck in it, and this suite is where the pairing is stated as intended rather than accidental.
 *
 * <p>Integration because the disagreement is a property of the two projections over one colony walk:
 * stub either and what gets asserted is the stub. The economy, the entity walk and the claim
 * mechanic are all read for real, only the live settings reads and the tooltip palette standing in.
 *
 * <p>The claim case is here for the boundary the whole work is bound by: widening what is shown
 * never widens what is scored. The derelict reaches the listing, and the claimant and every score
 * behind it are the ones the same system resolves without it.
 */
final class DerelictReadoutIntegrationTest {

    private static final String SYSTEM_ID = "galatia";

    // A faction the sector knows by name and marks with no crest, the presentation being beside the
    // point here: what each case reads back is which factions the box named at all.
    private static final String NO_CREST = null;

    // The colony's size. Only a listed colony is ever weighed - a hulk is unregistered, so no term
    // of the arithmetic can read one - which is why the derelict's size is the scenario fixture's
    // business and not stated here.
    private static final int COLONY = 6;

    private MockedStatic<MapVisibilityRules> visibilityRulesMock;
    private MockedStatic<PoliticalMapViewRegistry> viewRegistryMock;
    private MockedStatic<DominanceRules> rulesMock;

    @BeforeEach
    void installColoursAndTheSettingsSeams() {

        CellTooltipPaletteFake.installPalette();

        // The colony rule is a live LunaLib read, unreachable from the test JVM; stood in as the
        // fog alone, which is the state each case is posed under - every market staged here is one
        // the player has found.
        visibilityRulesMock = Mockito.mockStatic(MapVisibilityRules.class);
        visibilityRulesMock
            .when(MapVisibilityRules::readFromLunaSettings)
            .thenReturn(MapVisibilityRules.BASE);

        rulesMock = Mockito.mockStatic(DominanceRules.class);
        rulesMock
            .when(DominanceRules::readFromLunaSettings)
            .thenReturn(buildStabilityWeightedRules());

        installFactionView();
    }

    @AfterEach
    void clearColoursAndTheSettingsSeams() {

        rulesMock.close();
        viewRegistryMock.close();
        visibilityRulesMock.close();
        CellTooltipPaletteFake.clearPalette();
    }

    @Nested
    class BuildBodySections {

        @Test
        void namesADerelictOverASystemItCallsUnpopulated() {
            // The parting itself. The hulk is the system's only market, so the listing names its
            // owner and the line above it says nobody lives here - which is what a system of wrecks
            // is, rather than the contradiction the two lines look like side by side.
            var sector = buildSectorHoldingADerelict();

            assertThat(readDominationLabels(sector))
                .contains("Unpopulated", "Neutral");
        }

        @Test
        void namesBothADerelictAndTheColonyBesideItOverAPopulatedSystem() {
            // The same system once somebody settles it. The hulk is staged unchanged, so what
            // silenced the status line is the colony rather than anything the derelict stopped
            // being - and the listing still names them both.
            var sector = buildSectorHoldingADerelict(buildColony());

            assertThat(readDominationLabels(sector))
                .contains("The Hegemony", "Neutral")
                .doesNotContain("Unpopulated");
        }
    }

    @Nested
    class ReadBreakdown {

        @Test
        void resolvesTheSameClaimantAndScoresWhetherOrNotADerelictStandsThere() {
            // The boundary the whole work is bound by: what the map shows is widened, what the
            // mechanic scores is not. The hulk reaches the listing above, and the contest behind
            // the claim resolves exactly as it does over the colony alone.
            var withDerelict = readClaimBreakdown(buildSectorHoldingADerelict(buildColony()));
            var withoutDerelict = readClaimBreakdown(
                SectorPoliticsFixtures.buildSectorWith(SYSTEM_ID, buildColony()));

            assertThat(withDerelict.claimantFactionId())
                .isEqualTo(withoutDerelict.claimantFactionId());
            assertThat(readScore(withDerelict, "hegemony"))
                .isEqualTo(readScore(withoutDerelict, "hegemony"));
        }
    }

    // The active view, standing in for the tab the player is on: the plain faction view, whose
    // identity grouping makes every faction its own bloc.
    private void installFactionView() {

        var viewMock = mock(PoliticalMapView.class);

        when(viewMock.resolveGrouping())
            .thenReturn(HolderGrouping.identity());

        viewRegistryMock = Mockito.mockStatic(PoliticalMapViewRegistry.class);
        viewRegistryMock
            .when(PoliticalMapViewRegistry::getActiveView)
            .thenReturn(viewMock);
    }

    // The dominance box over the sector's one system, read top to bottom as the words a player
    // sees - the status line included, since what this suite is about is the pairing of that line
    // with the listing beneath it.
    private static List<String> readDominationLabels(SectorAPI sector) {

        return readLabelTexts(new SystemDominationTooltip(new ClaimBreakdownReaderFake())
            .buildBodySections(sector, buildOnlySystem(sector)));
    }

    // The claim contest behind the system, read through the real mechanic over the pass's own walk
    // - a hand-built breakdown would assert the boundary by construction rather than testing it.
    private static SystemClaimBreakdown readClaimBreakdown(SectorAPI sector) {

        var pass = HolderPass.over(sector, BASE_FOG, HolderGrouping.identity());

        return new VanillaClaimBreakdownReader(pass.colonyKnowledge(), pass.colonies())
            .readBreakdown(buildOnlySystem(sector));
    }

    // One faction's score in a breakdown, or nought where the contest weighed nothing for it.
    private static int readScore(SystemClaimBreakdown breakdown, String factionId) {

        return breakdown
            .scores()
            .stream()
            .filter(standing -> factionId.equals(standing.factionId()))
            .mapToInt(FactionClaimStanding::score)
            .findFirst()
            .orElse(0);
    }

    // A system holding a derelict on one of its own entities, beside whatever colonies the economy
    // lists there.
    private static SectorAPI buildSectorHoldingADerelict(MarketAPI... listedColonies) {

        var sector = SectorPoliticsFixtures.buildSectorWith(SYSTEM_ID, listedColonies);

        placeDerelictIn(buildOnlySystem(sector));

        stubFaction(sector, "hegemony", "The Hegemony", NO_CREST);
        stubFaction(sector, Factions.NEUTRAL, "Neutral", NO_CREST);

        return sector;
    }

    // An ordinary colony the economy lists, held by a faction the sector knows by name.
    private static MarketAPI buildColony() {
        return buildVisibleMarket(buildFaction("hegemony"), COLONY);
    }

    // The box read top to bottom as the words a player sees, headings, banners and entries alike -
    // which is what "the box names a faction" means to the reader these cases are about.
    private static List<String> readLabelTexts(List<TooltipSection> sections) {

        return TooltipSection
            .readRowsInOrder(sections)
            .stream()
            .map(CellTooltipRowReads::readOpeningWords)
            .toList();
    }
}
