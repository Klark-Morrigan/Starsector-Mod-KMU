package kmu.maplayers.politicalmap.base.ribbon;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmlib.starsector.factions.FactionPalette;

import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderPass;

import java.awt.Color;
import java.util.Map;
import java.util.Optional;

import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildFaction;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildSectorWith;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildVisibleMarket;

/**
 * What every band suite is posed against: the blocs, the shades they draw in, the design's own
 * proportions, and the bake's inputs over a stubbed sector.
 *
 * <p>Shared because a band is counted by one rule and ranked by each mechanic's own, so the
 * mechanics' suites, the shared rule's, and the planners' all pose the same map and read the same
 * runs back. Held apart, each suite spelled out its own palette and its own reading of a sector,
 * and two of them could disagree about what "the design's proportions" are while both passing.
 *
 * <p>The colours are exported alongside the palette they are read through, since what a case
 * asserts is the run's colour: a palette a suite cannot name the shades of is a palette it cannot
 * read a band back from.
 */
public final class RibbonPlanFixtures {

    public static final String HEGEMONY = "hegemony";
    public static final String TRITACHYON = "tritachyon";
    public static final String PERSEAN = "persean";
    public static final String DIKTAT = "sindria";

    /**
     * The alliance the allies fold into, named by the synthetic id an alliance record carries
     * rather than by either member: a bloc is its own thing, and its shades are its colour
     * faction's, resolved before a band ever asks for them.
     */
    public static final String HEGEMONY_ALLIANCE = "hegemony_compact";

    /**
     * A bloc the map can no longer colour: it holds colonies, but its colour faction has gone from
     * the sector, so the palette read below has nothing to answer with.
     */
    public static final String VANISHED_BLOC = "vanished";

    public static final Color HEGEMONY_BRIGHT = new Color(140, 160, 220);
    public static final Color HEGEMONY_DARK = new Color(40, 60, 120);
    public static final Color TRITACHYON_BRIGHT = new Color(120, 220, 200);
    public static final Color TRITACHYON_DARK = new Color(20, 90, 80);
    public static final Color PERSEAN_BRIGHT = new Color(200, 180, 120);
    public static final Color PERSEAN_DARK = new Color(90, 70, 30);
    public static final Color DIKTAT_BRIGHT = new Color(220, 150, 120);
    public static final Color DIKTAT_DARK = new Color(90, 50, 40);

    /**
     * The shades every bloc draws in, read by bloc id exactly as the live map reads them. A bloc
     * absent from this map has no colour to resolve, which is the drop case
     * {@link #VANISHED_BLOC} poses.
     */
    public static final BlocPaletteReader PALETTES = Map.of(
            HEGEMONY,
            new FactionPalette(HEGEMONY_BRIGHT, HEGEMONY_DARK),
            HEGEMONY_ALLIANCE,
            new FactionPalette(HEGEMONY_BRIGHT, HEGEMONY_DARK),
            TRITACHYON,
            new FactionPalette(TRITACHYON_BRIGHT, TRITACHYON_DARK),
            PERSEAN,
            new FactionPalette(PERSEAN_BRIGHT, PERSEAN_DARK),
            DIKTAT,
            new FactionPalette(DIKTAT_BRIGHT, DIKTAT_DARK))
        ::get;

    /**
     * The design's own proportions - a colony three widths long, parted by one width - paired with
     * the uncontested shortening switched off, so every cell's runs come out at those same lengths
     * whether a rival is in it or not. What the shortening does to them is
     * {@link UncontestedRibbonRunsTest}'s.
     *
     * <p>The widths are not exported for a case to assert with. A case states the run lengths it
     * expects as its own literals, so an edit to these proportions fails the suites that read a
     * band back rather than moving their expectations along with it.
     */
    public static final RibbonPlanRules STANDARD_RULES =
        new RibbonPlanRules(
            new RibbonSegmentLengths(3, 1),
            new UncontestedRibbonRuns(false));

    /**
     * The same proportions with the shortening switched on, which is the shipped answer and the
     * only witness to which reading a cell fell under: a cell nobody contests draws its colonies
     * one width each, where a contested one keeps the authored run. A suite reaching for these is
     * posing that question and nothing else.
     *
     * <p>The proportions come off the rules above rather than being restated, so the two cannot
     * drift into disagreeing about what an unshortened run is.
     */
    public static final RibbonPlanRules SHORTENED_UNCONTESTED_RULES =
        new RibbonPlanRules(
            STANDARD_RULES.lengths(),
            new UncontestedRibbonRuns(true));

    /**
     * A cell no bloc's fill covers, which is what an unclaimed populated system draws as. There is
     * no painter for a bloc to be a rival of, so what makes such a cell contested is how many blocs
     * are in it.
     */
    public static final Optional<String> NO_PAINTER = Optional.empty();

    // Whether the pass lifts the fog off colonies the player has not found. Named at both settings
    // because a suite posing the reveal is posing the one thing the counting rule's input reads.
    public static final boolean FOG_LIFTED = true;
    public static final boolean FOG_KEPT = false;

    // The size every colony these fixtures build carries. A band counts holdings rather than
    // weighing them, so a suite about counting or ordering varies nothing by varying this.
    private static final int COLONY_SIZE = 5;

    private RibbonPlanFixtures() {
    }

    /**
     * A grouping in which the two named factions share one alliance bloc under the alliance's own
     * id, coloured off the first of them - the shape the alliances view folds a bloc in.
     *
     * @param colourFactionId the member whose shades the bloc draws in
     * @param memberFactionId the other member, folding into the same bloc
     * @return the alliance grouping
     */
    public static HolderGrouping buildAllianceOf(String colourFactionId, String memberFactionId) {
        return new HolderGrouping(
            Map.of(
                colourFactionId,
                HEGEMONY_ALLIANCE,
                memberFactionId,
                HEGEMONY_ALLIANCE),
            Map.of(HEGEMONY_ALLIANCE, colourFactionId),
            Map.of(HEGEMONY_ALLIANCE, "Hegemony Compact"));
    }

    /**
     * A sector whose one system holds a plain visible colony of each named faction, in the order
     * given - which is the economy's own listing order. A faction named twice holds two colonies,
     * which is how a case poses a bloc with a run of its own to part.
     *
     * <p>Every colony is the same size, since a band counts holdings rather than weighing them. A
     * suite whose mechanic does weigh them states its own sizes.
     *
     * @param systemId   the system's id
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
     * @param sector                           the stubbed sector the colonies are read from
     * @param grouping                         the grouping the counts are folded under
     * @param shouldIncludeUndiscoveredMarkets whether the dev reveal lifts the fog
     * @return the inputs a planner is posed with
     */
    public static RibbonPlanInputs buildInputsOver(
            SectorAPI sector,
            HolderGrouping grouping,
            boolean shouldIncludeUndiscoveredMarkets) {

        return buildInputsFor(
            HolderPass.over(sector, shouldIncludeUndiscoveredMarkets, grouping));
    }

    /**
     * The same inputs over a pass the caller already holds - what a suite building a dominance pass
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
     * @return the inputs a planner is posed with
     */
    public static RibbonPlanInputs buildInputsFor(HolderPass pass, RibbonPlanRules rules) {
        return new RibbonPlanInputs(pass, PALETTES, rules);
    }
}
