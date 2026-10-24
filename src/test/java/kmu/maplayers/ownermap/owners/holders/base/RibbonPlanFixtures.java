package kmu.maplayers.ownermap.owners.holders.base;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmlib.colour.ColourPair;

import kmu.maplayers.base.visibility.colonies.ColonyVisibility;
import kmu.maplayers.ownermap.owners.OwnerAffiliation;
import kmu.maplayers.ownermap.ribbon.RibbonPlanRules;

import java.awt.Color;
import java.util.Map;

import static kmu.maplayers.ownermap.owners.holders.base.ColonyReadRulesFixtures.UNDER_THE_FOG;
import static kmu.maplayers.ownermap.owners.holders.base.ColonyReadRulesFixtures.buildRulesUnder;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildFaction;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildSectorWith;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildVisibleMarket;
import static kmu.maplayers.ownermap.ribbon.OwnerRibbonRunsFixtures.ALLIED_HEGEMONY_AND_TRITACHYON;
import static kmu.maplayers.ownermap.ribbon.OwnerRibbonRunsFixtures.DIKTAT;
import static kmu.maplayers.ownermap.ribbon.OwnerRibbonRunsFixtures.DIKTAT_BRIGHT;
import static kmu.maplayers.ownermap.ribbon.OwnerRibbonRunsFixtures.DIKTAT_DARK;
import static kmu.maplayers.ownermap.ribbon.OwnerRibbonRunsFixtures.HEGEMONY;
import static kmu.maplayers.ownermap.ribbon.OwnerRibbonRunsFixtures.HEGEMONY_BRIGHT;
import static kmu.maplayers.ownermap.ribbon.OwnerRibbonRunsFixtures.HEGEMONY_DARK;
import static kmu.maplayers.ownermap.ribbon.OwnerRibbonRunsFixtures.SHORTENED_UNCONTESTED_RULES;
import static kmu.maplayers.ownermap.ribbon.OwnerRibbonRunsFixtures.STANDARD_RULES;
import static kmu.maplayers.ownermap.ribbon.OwnerRibbonRunsFixtures.TRITACHYON;
import static kmu.maplayers.ownermap.ribbon.OwnerRibbonRunsFixtures.TRITACHYON_BRIGHT;
import static kmu.maplayers.ownermap.ribbon.OwnerRibbonRunsFixtures.TRITACHYON_DARK;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What every holder band suite is posed against: the blocs, the shades they draw in, the design's
 * own proportions, and the bake's inputs over a stubbed sector.
 *
 * <p>The owners, shades, proportions and affiliation the core's rule suite shares are
 * {@code OwnerRibbonRunsFixtures}' own, and a holder suite imports them from there. Only what a
 * holder adds is stated here: a group bloc, the neutral faction, a vanished bloc and the palettes
 * and sector reads over them.
 *
 * <p>Shared because a band is counted by one rule and ranked by each mechanic's own, so the
 * mechanics' suites, the shared rule's, and the planners' all pose the same map and read the same
 * runs back. Held apart, each suite would spell out its own palette and its own reading of a
 * sector, and two of them could disagree about what "the design's proportions" are while both
 * passing.
 *
 * <p>The colours are exported alongside the sector shading they are read through, since what a case
 * asserts is the run's colour: a shade a suite cannot name is a shade it cannot read a band back
 * from.
 */
public final class RibbonPlanFixtures {

    public static final String PERSEAN = "persean";

    /**
     * The faction every unowned station falls to, and so the owner of every derelict. Carries
     * shades like any other bloc below, which is what makes a case about a derelict raising no run
     * discriminate: with no palette it would draw nothing for want of a colour, and pass whatever
     * the counting rule did with it.
     */
    public static final String NEUTRAL = Factions.NEUTRAL;

    /**
     * A bloc the map cannot colour: it holds colonies, but its colour faction has gone from
     * the sector, so the palette read below has nothing to answer with.
     */
    public static final String VANISHED_BLOC = "vanished";

    public static final Color PERSEAN_BRIGHT = new Color(200, 180, 120);
    public static final Color PERSEAN_DARK = new Color(90, 70, 30);

    private static final Color NEUTRAL_BRIGHT = new Color(170, 170, 170);
    private static final Color NEUTRAL_DARK = new Color(70, 70, 70);

    // The shades every faction these fixtures name draws in, which the sector a band is coloured
    // off answers by faction ID exactly as the live sector does. A faction absent from this map has
    // no colour to resolve, which is the drop case VANISHED_BLOC poses.
    //
    // The group bloc draws in the Hegemony's shades because that is its colour faction: every group
    // these fixtures pose is led by the Hegemony, and the pass's grouping folds it to that member
    // before the sector is asked.
    private static final Map<String, ColourPair> PALETTE_BY_FACTION_ID = Map.of(
        HEGEMONY,
        new ColourPair(HEGEMONY_BRIGHT, HEGEMONY_DARK),
        TRITACHYON,
        new ColourPair(TRITACHYON_BRIGHT, TRITACHYON_DARK),
        PERSEAN,
        new ColourPair(PERSEAN_BRIGHT, PERSEAN_DARK),
        DIKTAT,
        new ColourPair(DIKTAT_BRIGHT, DIKTAT_DARK),
        NEUTRAL,
        new ColourPair(NEUTRAL_BRIGHT, NEUTRAL_DARK));

    // The size every colony these fixtures build carries. A band counts holdings rather than
    // weighing them, so a suite about counting or ordering varies nothing by varying this.
    private static final int COLONY_SIZE = 5;

    private RibbonPlanFixtures() {
    }

    /**
     * A sector whose one system holds a plain visible colony of each named faction, in the order
     * given - which is the economy's own listing order. A faction named twice holds two colonies,
     * which is how a case poses a bloc with a run of its own to part.
     *
     * <p>Every colony is the same size, since a band counts holdings rather than weighing them. A
     * suite whose mechanic does weigh them states its own sizes.
     *
     * @param systemId   the system's ID
     * @param factionIds the owners, one colony each, in listing order
     * @return the stubbed sector
     */
    public static SectorAPI buildSectorHolding(String systemId, String... factionIds) {

        var colonies = new MarketAPI[factionIds.length];

        for (var index = 0; index < factionIds.length; index++) {
            colonies[index] = buildVisibleMarket(buildFaction(factionIds[index]), COLONY_SIZE);
        }

        return buildSectorWith(systemId, colonies);
    }

    /**
     * The bake's inputs over a stubbed sector: a pass opening its own walk of it, the sector
     * shaded with this fixture's colours, and the design's proportions.
     *
     * @param sector           the stubbed sector the colonies are read from
     * @param grouping         the grouping the counts are folded under
     * @param colonyVisibility what the pass shows of a colony
     * @return the inputs a planner is posed with
     */
    public static RibbonPlanInputs buildInputsOver(
            SectorAPI sector,
            HolderGrouping grouping,
            ColonyVisibility colonyVisibility) {

        return buildInputsFor(
            HolderPass.createOver(sector, buildRulesUnder(colonyVisibility), grouping));
    }

    /**
     * The same inputs over a pass the caller already holds - what a suite building a weighted pass
     * beside them takes, so the ranking and the counting share one walk of each system as they do
     * in a live bake.
     *
     * @param pass the bake's reading of the sector
     * @return the inputs a planner is posed with
     */
    public static RibbonPlanInputs buildInputsFor(HolderPass pass) {
        return buildInputsFor(pass, STANDARD_RULES);
    }

    /**
     * The same inputs under laying rules the suite chooses - what a case posing which reading a
     * cell fell under takes, the run lengths being the only place that is visible.
     *
     * @param pass  the bake's reading of the sector
     * @param rules how a band is laid
     * @return the inputs a planner is posed with, with nobody standing with anybody
     */
    public static RibbonPlanInputs buildInputsFor(HolderPass pass, RibbonPlanRules rules) {
        return buildInputsFor(pass, OwnerAffiliation.NONE, rules);
    }

    /**
     * The inputs a case about who counts as a rival takes: blocs painted per faction, under the
     * identity grouping, judged against {@code ALLIED_HEGEMONY_AND_TRITACHYON} and laid with the shortening
     * on - which is the one place the reading a cell fell under can be read back.
     *
     * <p>The fog is the plain one because no such case turns on it. A suite posing what the player
     * has found states its own inputs.
     *
     * @param sector the stubbed sector the colonies are read from
     * @return the inputs a planner is posed with
     */
    public static RibbonPlanInputs buildAlliedInputsOver(SectorAPI sector) {
        return buildAlliedInputsFor(HolderPass.createOver(sector, UNDER_THE_FOG, HolderGrouping.identity()));
    }

    /**
     * The same rival-judging inputs over a pass the caller already holds - what a suite standing in
     * for the pass takes, so the habitation a case states is the one the band counts.
     *
     * @param pass the bake's reading of the sector
     * @return the inputs a planner is posed with
     */
    public static RibbonPlanInputs buildAlliedInputsFor(HolderPass pass) {
        return buildInputsFor(pass, ALLIED_HEGEMONY_AND_TRITACHYON, SHORTENED_UNCONTESTED_RULES);
    }

    /**
     * The palette read a stood-in pass answers, over a sector shading every faction these fixtures
     * name and the identity grouping - what a suite posing the pass itself hands it, so a band
     * reads the same shades a pass opened over a stubbed sector would.
     *
     * @return the palettes over the shaded sector
     */
    public static SectorBlocPalettes buildShadedPalettes() {
        return new SectorBlocPalettes(shadeFactionsOn(mock(SectorAPI.class)), HolderGrouping.identity());
    }

    // The inputs under an affiliation the caller states. Held private because a case wanting one
    // wants the shape above: an affiliation is only visible against the shortening, and a suite
    // free to pair the two differently could pose a cell whose reading nothing can be read back
    // from.
    //
    // The pass's sector is shaded here, so every suite reads a band in the shades this fixture
    // exports whatever its sector was built to answer for the fills. A stood-in pass names no
    // sector, and answers its own palettes.
    private static RibbonPlanInputs buildInputsFor(
            HolderPass pass,
            OwnerAffiliation affiliation,
            RibbonPlanRules rules) {

        var sector = pass.sector();
        if (sector != null) {
            shadeFactionsOn(sector);
        }

        return new RibbonPlanInputs(pass, affiliation, rules);
    }

    // Has the stubbed sector answer every faction these fixtures name with its shades, the way the
    // live sector answers a faction's authored UI pair.
    //
    // A faction the sector already answers keeps everything else it was built with - its names
    // above all, which the boxes over a band read - and only takes these shades; one it does not
    // answer is registered fresh.
    private static SectorAPI shadeFactionsOn(SectorAPI sectorMock) {

        for (var factionPalette : PALETTE_BY_FACTION_ID.entrySet()) {

            var factionId = factionPalette.getKey();
            var registeredFaction = sectorMock.getFaction(factionId);
            var factionMock = registeredFaction == null
                ? buildFaction(factionId)
                : registeredFaction;

            when(factionMock.getBrightUIColor())
                .thenReturn(factionPalette.getValue().primaryColour());
            when(factionMock.getDarkUIColor())
                .thenReturn(factionPalette.getValue().secondaryColour());

            if (registeredFaction == null) {
                when(sectorMock.getFaction(factionId))
                    .thenReturn(factionMock);
            }
        }
        return sectorMock;
    }
}
