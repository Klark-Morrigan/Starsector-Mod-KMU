package kmu.maplayers.ownermap.owners.holders.base;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.ownermap.owners.OwnerAffiliation;
import kmu.maplayers.ownermap.ribbon.RibbonPlanRules;

/**
 * What one bake's bands are counted and coloured from: the reading of the sector the colonies come
 * out of, where each segment's two shades are read, who among the blocs stands together, and how a
 * band is laid.
 *
 * <p>They travel together through every planner because no one of them is usable without the
 * others - a run is a count of colonies, coloured, judged for contest, laid to a rule - and because
 * each is the bake's own one-time read. Carried as one value so a planner cannot be built from this
 * bake's rules and another bake's pass, and so every mechanic counting one map shares a single
 * walk of each system rather than each opening a pass of its own.
 *
 * <p>The pass is what makes the counting rule the same rule on every layer. It names the sector,
 * the grouping factions fold into blocs under and the whole colony rule - so a band cannot be
 * counted under a different fog or a different fold from the fill it sits inside, and no counter
 * needs a settings read of its own to find out which.
 *
 * <p>The palette read rides beside the pass rather than being read off it, because colouring is the
 * view's choice: by default the pass's own read, so a run draws in the shades its fill is painted
 * in, and a view colouring its segments by something its fills are not painted by hands its own.
 *
 * <p>The affiliation rides beside it as a second reading of the same sector, deliberately not the
 * pass's: the counts fold under the grouping the fills were painted with, while whether the cell is
 * a contest at all is judged against who stands together in the live sector. Carried here so both
 * readings are the bake's one sampling, a planner having no way to count under this bake's fold and
 * judge against an affiliation from another.
 *
 * <p>Each part arrives rather than being read here: which reading of the live sector a bake is
 * stated over is the caller's to settle, and a read taken here would be a second one the bake never
 * asked for.
 *
 * @param pass        the bake's reading of the sector: which sector, the grouping, the colony
 *                    rule, and the one walk of each system every count is folded from; opened once
 *                    by whoever begins the bake
 * @param palettes    where each colony's segment reads its shades, as the view chose them
 * @param affiliation who among the blocs present stands together, which decides only the length a
 *                    band's runs are laid at; {@link OwnerAffiliation#NONE} where nobody does
 * @param rules       how a band is laid: the segment lengths, and how far they reach on a cell
 *                    nobody contests
 */
public record RibbonPlanInputs(
    HolderPass pass,
    RibbonPaletteReader palettes,
    OwnerAffiliation affiliation,
    RibbonPlanRules rules) {

    /**
     * The grouping every count is folded under, which is the one the fills were painted with: a
     * bloc's colonies come out as that bloc's run exactly as they come out as its fill. Whether two
     * blocs stand together is {@link #affiliation}'s and never this - a fold merges runs, and
     * standing together must not.
     *
     * @return the bake's grouping
     */
    public HolderGrouping grouping() {
        return pass.grouping();
    }

    /**
     * What a band may report of one system - the pass's habitation of it, already folded per bloc
     * under the pass's grouping and remembered for the rest of the pass.
     *
     * <p>Habitation rather than the wider known listing, because a run stands for somebody holding
     * something in the system: a derelict the player has seen is named in the boxes over the cell
     * and has never had anybody aboard, so a segment for it would report a holding nobody has.
     *
     * <p>Taken off the pass rather than from a sector each planner holds, because a planner that
     * could reach a sector is one that could walk a system the bake has already walked.
     *
     * @param system the system to count; null yields an empty habitation
     * @return the system's habitation under the bake's colony rule and grouping
     */
    public SystemHabitation readHabitationIn(StarSystemAPI system) {
        return pass.readHabitationIn(system);
    }
}
