package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.markets.DecivilisedMarkets;
import kmlib.starsector.systems.StarSystems;
import kmlib.starsector.ui.widgets.TooltipRow;

import kmu.util.KmuStrings;

import java.util.Optional;

/**
 * The line naming why a hovered system holds nobody: a dead colony the player has already seen reads
 * "Decivilised", any other system with no counted colony "Unpopulated".
 *
 * <p>A shared row rather than each tooltip's own empty state, because the emptiness is a fact about
 * the system, not about the layer looking at it - a layer that says nothing else about a dead system
 * still has to say that much, and two layers wording or placing it differently would read as two
 * different facts. The row resolves empty for a populated system, so a body can offer the status
 * unconditionally and let the system decide whether it appears.
 */
public final class SystemStatusRow {

    private SystemStatusRow() {
    }

    /**
     * Resolves the status line for a system that holds no counted colony.
     *
     * @param sector                           the sector whose economy is read
     * @param system                           the hovered system
     * @param shouldIncludeUndiscoveredMarkets whether an undiscovered colony still counts as
     *                                         populating the system (the "show all factions" dev
     *                                         reveal); passed by the caller so the status agrees with
     *                                         whatever that caller's own reads admit, rather than
     *                                         calling a system empty that the body below it fills
     * @return the Decivilised or Unpopulated row, or empty when the system holds a counted colony
     */
    public static Optional<TooltipRow> resolveStatusRow(
            SectorAPI sector,
            StarSystemAPI system,
            boolean shouldIncludeUndiscoveredMarkets) {

        if (StarSystems.hasKnownOwnedMarket(sector, system, shouldIncludeUndiscoveredMarkets)) {
            return Optional.empty();
        }
        var statusKey = DecivilisedMarkets.hasRevealedDecivilisedPlanet(system)
                ? KmuStrings.POLITICAL_MAP_TOOLTIP_DECIVILISED
                : KmuStrings.POLITICAL_MAP_TOOLTIP_UNPOPULATED;

        // Flush under the system name the box is headed with, crestless and scoreless: the status
        // qualifies the whole system rather than being one entry of a list, so it reads as a standalone
        // line rather than as the first - indented - row of a breakdown that has none.
        return Optional.of(SystemCellTooltip.buildStandaloneRow(KmuStrings.get(statusKey)));
    }
}
