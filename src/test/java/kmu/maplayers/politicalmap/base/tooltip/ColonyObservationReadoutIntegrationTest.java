package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmlib.starsector.systems.claims.VanillaClaimBreakdownReader;
import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.tooltip.TooltipRow;
import kmlib.starsector.ui.widgets.tooltip.TooltipSection;
import kmlib.testfixtures.starsector.systems.claims.ClaimBreakdownReaderFake;

import kmu.maplayers.base.tooltip.CellTooltipPaletteFake;
import kmu.maplayers.base.tooltip.layout.CellTooltipRowReads;
import kmu.maplayers.base.visibility.systems.MapVisibilityRules;
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
import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.GRAY;
import static kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel.PATROL_DETAILS;
import static kmu.maplayers.base.visibility.colonies.ColonyVisibility.BASE_FOG;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildFaction;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildHiddenMarket;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildOnlySystem;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildStabilityWeightedRules;
import static kmu.maplayers.politicalmap.base.tooltip.SectorFactionsFake.stubFaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the whole route a colony's remark travels, from an observation the player's own visit wrote
 * into the sector's memory to the run it is drawn as: the register read back through the pass's
 * knowledge, dated by the notes, laid onto the colony's line, and spoken last in the box's quiet
 * shade.
 *
 * <p>Integration because every unit case along that route poses the step before it. The notes are
 * exercised over a handed-in register, the row resolvers over stubbed notes, and the label runs over
 * a hand-built line - so each half is pinned against a stand-in for the other, and a stamp written
 * in one form and read in another would leave all of them green while the player is told nothing.
 * The economy, the entity walk, the sighting register and the claim mechanic are all real here, only
 * the live settings reads, the campaign clock and the tooltip palette standing in.
 *
 * <p>Both hover families are read against one shared expected run, which is the invariant the shared
 * formatter exists to hold: they name the same colonies of one system off carriers neither shares
 * with the other, so a date composed at each could have one box state an age in words the other
 * never uses, over one hovered cell.
 *
 * <p>The system holds nothing anybody lives on. That is what keeps the remark due at all - a colony
 * standing among another faction's people is observed by them as the box is drawn, and a colony in
 * plain sight needs no date beside it - so the concealed base and the hulk are the only things here,
 * neither of which settles a place or vouches for the other.
 */
final class ColonyObservationReadoutIntegrationTest {

    private static final String SYSTEM_ID = "kumari_kandam";

    // What the two colonies are called. A line is found by its name, so each is named for a place
    // rather than for what it is - nothing the boxes say about either can have come from the name.
    private static final String DERELICT_NAME = "Sentinel Gantries";
    private static final String CONCEALED_BASE_NAME = "Kanta's Den";

    // The concealed base's size. Its weight is nobody's subject here: it is present so the system
    // holds a second gated colony beside the hulk, both recorded by the one visit.
    private static final int CONCEALED_BASE_SIZE = 4;

    // A faction the sector knows by name and marks with no crest, the presentation being beside the
    // point: what these cases read back is the run at the end of one colony's line.
    private static final String NO_CREST = null;

    // When the visit was made, the date the clock reports for it, and how long ago that was. The
    // three are stubbed apart because the game's own clock is what turns a stamp into a span and a
    // date, so a case states the age it is about rather than arithmetic over two timestamps.
    private static final long OBSERVED_AT = 4_200L;
    private static final String OBSERVED_DATE = "c206.05.12";
    private static final float ELAPSED_DAYS = 34.0f;

    /**
     * What the end of the remarked line reads as, in both families alike: the whole composed
     * sentence, in the quiet shade that parts a note about the box's own account from the gold the
     * box's findings are stated in.
     */
    private static final TextSpan LAST_SEEN_RUN =
        new TextSpan("last seen 34 days ago (" + OBSERVED_DATE + ")", GRAY);

    private MockedStatic<MapVisibilityRules> visibilityRulesMock;
    private MockedStatic<PoliticalMapViewRegistry> viewRegistryMock;
    private MockedStatic<DominanceRules> rulesMock;

    @BeforeEach
    void installColoursAndTheSettingsSeams() {

        CellTooltipPaletteFake.installPalette();

        // The colony rule is a live LunaLib read, unreachable from the test JVM; stood in as the
        // fog alone, which is the state these cases are posed under - both colonies staged here are
        // ones the player has found.
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
    class ComposeBody {

        @Test
        void remarksHowOldTheNewsOfAColonyIsOnTheDominanceBoxsOwnLineForIt() {
            // The whole route in one case: the visit wrote a stamp into sector memory, the pass read
            // it back, and the line naming the hulk runs on into how old that news is - spoken last
            // and in the quiet shade, so a reader scanning for findings passes over it.
            var sector = buildSectorTheFleetHasLongSinceLeft();

            assertThat(readRowNamed(readDominanceSections(sector), DERELICT_NAME).labelRuns())
                .endsWith(LAST_SEEN_RUN);
        }

        @Test
        void remarksHowOldTheNewsOfAColonyIsOnTheClaimBoxsOwnLineForIt() {
            // The same colony, the same age, through the other family - which reaches its lines off
            // a claim breakdown rather than a dominance weight, and must nonetheless state the run
            // above word for word and shade for shade.
            var sector = buildSectorTheFleetHasLongSinceLeft();

            assertThat(readRowNamed(readClaimSections(sector), DERELICT_NAME).labelRuns())
                .endsWith(LAST_SEEN_RUN);
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

    // The dominance box over the sector's one system, read to the depth that opens a faction up -
    // the shallowest level hangs nothing beneath one, so no colony's own line is drawn there at all.
    private static List<TooltipSection> readDominanceSections(SectorAPI sector) {

        return new SystemDominationTooltip(new ClaimBreakdownReaderFake(), HolderGrouping::identity)
            .composeBody(sector, buildOnlySystem(sector), PATROL_DETAILS).blocks().readSections();
    }

    // The claims box over the same system at that same depth, read through the real claim mechanic:
    // a stubbed contest would hold whatever markets the stub was handed, and what these cases turn
    // on is the colony reaching the list the way the game puts it there.
    private static List<TooltipSection> readClaimSections(SectorAPI sector) {

        var pass = HolderPass.over(sector, BASE_FOG, HolderGrouping.identity());

        return new SystemClaimTooltip(
                new VanillaClaimBreakdownReader(pass.colonyKnowledge(), pass.colonies()),
                HolderGrouping::identity)
            .composeBody(sector, buildOnlySystem(sector), PATROL_DETAILS).blocks().readSections();
    }

    // The one line naming the given colony, found by the words it opens with. Read by name rather
    // than by position because the two families order their blocks differently, and what these cases
    // are about is the line for one colony rather than where either box puts it.
    private static TooltipRow readRowNamed(List<TooltipSection> sections, String colonyName) {

        for (var row : TooltipSection.readRowsInOrder(sections)) {

            if (colonyName.equals(CellTooltipRowReads.readOpeningWords(row))) {
                return row;
            }
        }
        throw new IllegalStateException("No line in the box names " + colonyName);
    }

    // One system holding the two shapes a gate covers and nothing else, visited once and left.
    //
    // Neither colony settles the place: a concealed base is not the sector's town crier and nobody
    // ever lived on the hulk, so no inhabitant observes either as the box is drawn. The fleet is
    // elsewhere, an unstubbed current location already being somewhere other than this system. That
    // leaves the register as the only thing either box can date them by - which is the point.
    private static SectorAPI buildSectorTheFleetHasLongSinceLeft() {

        var pirates = buildFaction("pirates");
        var base = buildHiddenMarket(pirates, CONCEALED_BASE_SIZE);

        when(base.getName())
            .thenReturn(CONCEALED_BASE_NAME);

        var sector = SectorPoliticsFixtures.buildSectorWith(SYSTEM_ID, List.of(pirates), base);
        var system = buildOnlySystem(sector);
        var derelict = placeDerelictIn(system);

        when(derelict.getName())
            .thenReturn(DERELICT_NAME);

        // Where each colony stands, which the recorded observation names and every later read of it
        // is matched against.
        SectorPoliticsFixtures.placeMarketsInSystem(system, base, derelict);

        stubFaction(sector, "pirates", "Pirates", NO_CREST);
        stubFaction(sector, Factions.NEUTRAL, "Neutral", NO_CREST);

        installClock(sector);
        SectorPoliticsFixtures.markSystemAsVisitedByPlayer(sector, system);

        return sector;
    }

    // The campaign clock, stubbed at the two moments a remark is composed from: the timestamp the
    // visit is written down at, and - read back later - the span since and the date it names. The
    // second clock is the one the game builds from a stamp to turn it into a date.
    private static void installClock(SectorAPI sector) {

        var observedClockMock = mock(CampaignClockAPI.class);

        when(observedClockMock.getDateString())
            .thenReturn(OBSERVED_DATE);

        var clockMock = mock(CampaignClockAPI.class);

        when(clockMock.getTimestamp())
            .thenReturn(OBSERVED_AT);
        when(clockMock.getElapsedDaysSince(OBSERVED_AT))
            .thenReturn(ELAPSED_DAYS);
        when(clockMock.createClock(OBSERVED_AT))
            .thenReturn(observedClockMock);

        when(sector.getClock())
            .thenReturn(clockMock);
    }
}
