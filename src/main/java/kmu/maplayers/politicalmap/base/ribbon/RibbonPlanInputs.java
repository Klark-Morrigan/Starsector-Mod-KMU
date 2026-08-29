package kmu.maplayers.politicalmap.base.ribbon;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.colonies.Colony;

import kmu.maplayers.politicalmap.base.dominance.BlocAffiliation;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderPass;

import java.util.List;

/**
 * What one bake's bands are counted and coloured from: the reading of the sector the colonies come
 * out of, where a bloc's two shades are read, who among the blocs stands together, and how a band
 * is laid.
 *
 * <p>They travel together through every planner because no one of them is usable without the
 * others - a run is a count of colonies, coloured, judged for contest, laid to a rule - and because
 * each is the bake's own one-time read. Carried as one value so a planner cannot be built from this
 * bake's rules and a stale palette source, and so the two mechanics counting one map share a single
 * walk of each system rather than each opening a pass of its own.
 *
 * <p>The pass is what makes the counting rule the same rule on every layer. It names the sector,
 * the grouping factions fold into blocs under, and the whole colony rule - so a band cannot be
 * counted under a different fog or a different fold from the fill it sits inside, and no counter
 * needs a settings read of its own to find out which.
 *
 * <p>The affiliation joins that trio because it is a second reading of the same sector, and
 * deliberately not the pass's: the counts fold under the grouping the fills were painted with,
 * which the faction and claims layers pin to identity so every faction keeps its own run in its own
 * colours, while whether a cell is a contest at all is judged against the live alliance set. Widen
 * the pass's grouping to settle the contest instead and two allies' runs merge into one bloc's, and
 * leave it out and the two of them band as though they fought over the system.
 *
 * @param pass        the bake's reading of the sector: which sector, the grouping, the colony
 *                    rule, and the one walk of each system every count is folded from
 * @param palettes    where each present bloc's two shades are read from
 * @param affiliation who among the blocs present stands together, which decides only the length a
 *                    band's runs are laid at; {@link BlocAffiliation#NONE} where nothing groups
 *                    factions
 * @param rules       how a band is laid: the run lengths, and how far they reach on a cell nobody
 *                    contests
 */
public record RibbonPlanInputs(
    HolderPass pass,
    BlocPaletteReader palettes,
    BlocAffiliation affiliation,
    RibbonPlanRules rules) {

    /**
     * Samples the bake's inputs over a pass the caller has already opened.
     *
     * <p>The palette source is built here, once, off the pass's own sector and grouping - so two
     * mechanics counting the same map read a bloc's colours through one object, and neither can
     * colour a bloc under a grouping the counts were not folded under.
     *
     * <p>The alliance set arrives rather than being read here, for the reason the pass does: which
     * reading of the live sector a bake is stated over is the caller's to settle, and a read taken
     * here would be a second one the bake never asked for.
     *
     * @param pass        the bake's reading of the sector, opened once by whoever begins the bake
     * @param affiliation who stands together this bake, sampled once by whoever begins it
     * @param rules       how a band is laid, sampled once by the bake
     * @return the inputs every planner in the bake is stated over
     */
    public static RibbonPlanInputs createForPass(
            HolderPass pass,
            BlocAffiliation affiliation,
            RibbonPlanRules rules) {

        return new RibbonPlanInputs(
            pass,
            new SectorBlocPalettes(pass.sector(), pass.grouping()),
            affiliation,
            rules);
    }

    /**
     * The grouping every count is folded under, so two allies' colonies come out as one bloc's run
     * exactly as they come out as one bloc's fill.
     *
     * @return the bake's grouping
     */
    public HolderGrouping grouping() {
        return pass.grouping();
    }

    /**
     * The colonies in one system a band may report - the pass's habitation projection over its
     * single walk of the system.
     *
     * <p>Habitation rather than the wider known listing, because a run stands for somebody holding
     * something in the system: a derelict the player has seen is named in the boxes over the cell
     * and has never had anybody aboard, so a segment for it would report a holding nobody has.
     *
     * <p>Taken off the pass rather than from a sector each planner holds, because a planner that
     * could reach a sector is one that could walk a system the bake has already walked.
     *
     * @param system the system to count; null yields an empty list
     * @return the system's colonies somebody lives on that the bake's colony rule admits, in the
     *         set's own order
     */
    public List<Colony> readInhabitingColoniesIn(StarSystemAPI system) {
        return pass.readInhabitingColoniesIn(system);
    }
}
