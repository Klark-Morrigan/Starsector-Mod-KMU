package kmu.maplayers.ownermap.owners.holders.base;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmlib.colour.ColourPair;

import kmu.maplayers.base.visibility.colonies.ColonyVisibility;
import kmu.maplayers.ownermap.owners.OwnerAffiliation;
import kmu.maplayers.ownermap.ribbon.RibbonPlanRules;
import kmu.maplayers.ownermap.ribbon.RibbonPlanRulesFixtures;

import java.awt.Color;
import java.util.Map;
import java.util.Optional;

import static kmu.maplayers.ownermap.owners.holders.base.ColonyReadRulesFixtures.UNDER_THE_FOG;
import static kmu.maplayers.ownermap.owners.holders.base.ColonyReadRulesFixtures.buildRulesUnder;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildFaction;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildSectorWith;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildVisibleMarket;

/**
 * What every holder band suite is posed against: the blocs, the shades they draw in, the design's
 * own proportions, and the bake's inputs over a stubbed sector.
 *
 * <p>The owners, shades, proportions and affiliation the core's rule suite shares are
 * {@link RibbonPlanRulesFixtures}' own, named again here so a holder suite reads one fixture. Only
 * what a holder adds is stated here: a group bloc, the neutral faction, a vanished bloc and the
 * palettes and sector reads over them.
 *
 * <p>Shared because a band is counted by one rule and ranked by each mechanic's own, so the
 * mechanics' suites, the shared rule's, and the planners' all pose the same map and read the same
 * runs back. Held apart, each suite would spell out its own palette and its own reading of a
 * sector, and two of them could disagree about what "the design's proportions" are while both
 * passing.
 *
 * <p>The colours are exported alongside the palette they are read through, since what a case
 * asserts is the run's colour: a palette a suite cannot name the shades of is a palette it cannot
 * read a band back from.
 */
public final class RibbonPlanFixtures {

    public static final String HEGEMONY = RibbonPlanRulesFixtures.HEGEMONY;
    public static final String TRITACHYON = RibbonPlanRulesFixtures.TRITACHYON;
    public static final String PERSEAN = "persean";
    public static final String DIKTAT = RibbonPlanRulesFixtures.DIKTAT;

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

    /**
     * The bloc a Hegemony-led group folds into, read off the grouping fixture rather than spelled
     * here, so the palette below answers for exactly the bloc a suite's grouping produces.
     */
    public static final String GROUP_BLOC_ID =
        HolderGroupingFixture.buildGroupOf(HEGEMONY).resolveBlocId(HEGEMONY);

    public static final Color HEGEMONY_BRIGHT = RibbonPlanRulesFixtures.HEGEMONY_BRIGHT;
    public static final Color HEGEMONY_DARK = RibbonPlanRulesFixtures.HEGEMONY_DARK;
    public static final Color TRITACHYON_BRIGHT = RibbonPlanRulesFixtures.TRITACHYON_BRIGHT;
    public static final Color TRITACHYON_DARK = RibbonPlanRulesFixtures.TRITACHYON_DARK;
    public static final Color PERSEAN_BRIGHT = new Color(200, 180, 120);
    public static final Color PERSEAN_DARK = new Color(90, 70, 30);
    public static final Color DIKTAT_BRIGHT = RibbonPlanRulesFixtures.DIKTAT_BRIGHT;
    public static final Color DIKTAT_DARK = RibbonPlanRulesFixtures.DIKTAT_DARK;
    public static final Color NEUTRAL_BRIGHT = new Color(170, 170, 170);
    public static final Color NEUTRAL_DARK = new Color(70, 70, 70);

    /**
     * The shades every bloc draws in, read by bloc ID exactly as the live map reads them. A bloc
     * absent from this map has no colour to resolve, which is the drop case
     * {@link #VANISHED_BLOC} poses.
     *
     * <p>The group bloc draws in the Hegemony's shades because that is its colour faction: every
     * group these fixtures pose is led by the Hegemony, and a bloc paints in the shades of the
     * member it was folded around rather than in any of its own.
     */
    public static final BlocPaletteReader PALETTES = Map.of(
            HEGEMONY,
            new ColourPair(HEGEMONY_BRIGHT, HEGEMONY_DARK),
            GROUP_BLOC_ID,
            new ColourPair(HEGEMONY_BRIGHT, HEGEMONY_DARK),
            TRITACHYON,
            new ColourPair(TRITACHYON_BRIGHT, TRITACHYON_DARK),
            PERSEAN,
            new ColourPair(PERSEAN_BRIGHT, PERSEAN_DARK),
            DIKTAT,
            new ColourPair(DIKTAT_BRIGHT, DIKTAT_DARK),
            NEUTRAL,
            new ColourPair(NEUTRAL_BRIGHT, NEUTRAL_DARK))
        ::get;

    /**
     * The core's own proportions, with the shortening off. See {@link RibbonPlanRulesFixtures#STANDARD_RULES}.
     */
    public static final RibbonPlanRules STANDARD_RULES = RibbonPlanRulesFixtures.STANDARD_RULES;

    /**
     * The core's own proportions, with the shortening on. See
     * {@link RibbonPlanRulesFixtures#SHORTENED_UNCONTESTED_RULES}.
     */
    public static final RibbonPlanRules SHORTENED_UNCONTESTED_RULES =
        RibbonPlanRulesFixtures.SHORTENED_UNCONTESTED_RULES;

    /**
     * The Hegemony and Tri-Tachyon standing together, every other bloc outside. See
     * {@link RibbonPlanRulesFixtures#ALLIED_HEGEMONY_AND_TRITACHYON}.
     *
     * <p>Stated as an affiliation rather than as a grouping, which is the distinction the type exists
     * for: a case folding the two under a grouping would merge them into one run instead of leaving
     * them their own.
     */
    public static final OwnerAffiliation ALLIED_HEGEMONY_AND_TRITACHYON =
        RibbonPlanRulesFixtures.ALLIED_HEGEMONY_AND_TRITACHYON;

    /**
     * A cell no bloc's fill covers, which is what a populated system no mechanic paints draws as.
     * See {@link RibbonPlanRulesFixtures#NO_PAINTER}.
     */
    public static final Optional<String> NO_PAINTER = RibbonPlanRulesFixtures.NO_PAINTER;

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
     * The bake's inputs over a stubbed sector: a pass opening its own walk of it, the shared
     * palettes, and the design's proportions.
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
     * identity grouping, judged against {@link #ALLIED_HEGEMONY_AND_TRITACHYON}
     * and laid with the shortening on - which is the one place the reading a cell fell under can be
     * read back.
     *
     * <p>The fog is the plain one because no such case turns on it. A suite posing what the player
     * has found states its own inputs.
     *
     * @param sector the stubbed sector the colonies are read from
     * @return the inputs a planner is posed with
     */
    public static RibbonPlanInputs buildAlliedInputsOver(SectorAPI sector) {

        return buildInputsFor(
            HolderPass.createOver(sector, UNDER_THE_FOG, HolderGrouping.identity()),
            ALLIED_HEGEMONY_AND_TRITACHYON,
            SHORTENED_UNCONTESTED_RULES);
    }

    // The inputs under an affiliation the caller states. Held private because a case wanting one
    // wants the shape above: an affiliation is only visible against the shortening, and a suite
    // free to pair the two differently could pose a cell whose reading nothing can be read back
    // from.
    private static RibbonPlanInputs buildInputsFor(
            HolderPass pass,
            OwnerAffiliation affiliation,
            RibbonPlanRules rules) {

        return new RibbonPlanInputs(pass, PALETTES, affiliation, rules);
    }
}
