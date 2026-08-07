package kmu.maplayers.politicalmap.base.tooltip;

import kmu.maplayers.base.tooltip.CellTooltipEntry;
import kmu.maplayers.politicalmap.base.dominance.FactionStanding;

import java.util.List;

/**
 * What a box hangs beneath the line naming a faction: the account of where that faction's score in the
 * hovered system came from, or nothing where the box has no account to give.
 *
 * <p>Handed to the resolver that names the factions rather than laid over its answer afterwards, because
 * pairing accounts with lines after the fact means walking two lists at the same index - and getting
 * that wrong lists one faction's colonies under another faction's name, which reads as a fact rather
 * than as a bug. Asked as a function, the pairing happens in the one place already holding both the
 * standing and the line resolved from it.
 *
 * <p>Answering nothing is the ordinary case rather than a degenerate one: a glance box lists a faction
 * as its line alone, and only a box the player has asked for detail from has anything to hang beneath
 * it.
 */
@FunctionalInterface
public interface FactionAccountResolver {

    /** Hangs nothing beneath a faction - what a box with no account to give answers. */
    FactionAccountResolver NO_ACCOUNT = standing -> List.of();

    /**
     * Resolves what is listed beneath one faction's line as the account of its score.
     *
     * @param standing the faction's ranked place in the hovered system - who it is and what it scored
     * @return the entries listed beneath its line, in the order they are read; empty leaves the faction
     *         listed as its line alone
     */
    List<CellTooltipEntry> resolveAccountEntries(FactionStanding standing);
}
