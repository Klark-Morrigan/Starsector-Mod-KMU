package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.factions.FactionCrests;
import kmlib.starsector.ui.widgets.TooltipRow;
import kmlib.text.KmlibStrings;

import kmu.util.KmuStrings;

import java.util.Optional;

/**
 * The line naming the faction that holds a hovered system as core territory - a system handed to it
 * by decree rather than won by the markets in it.
 *
 * <p>A core is a different kind of fact from a score, so it gets a line rather than a number: the
 * faction is named plainly and "core territory" is marked out in the highlight colour, which is what
 * distinguishes a decreed hold from the ranked presences around it. The row resolves empty for a
 * system under no decree, so a body can offer it unconditionally.
 */
public final class CoreTerritoryRow {

    private CoreTerritoryRow() {
    }

    /**
     * Resolves the core-territory line for a system held by decree.
     *
     * @param sector        the sector the faction's name and crest are read from
     * @param coreFactionId the id of the faction holding the system as core territory, or null (or
     *                      blank) when no decree holds it
     * @return the core-territory row, or empty when there is no core faction
     */
    public static Optional<TooltipRow> resolveCoreTerritoryRow(
            SectorAPI sector,
            String coreFactionId) {

        if (!KmlibStrings.hasText(coreFactionId)) {
            return Optional.empty();
        }
        var faction = sector.getFaction(coreFactionId);
        
        return Optional.of(SystemCellTooltip.buildMarkedNestedRow(
                FactionCrests.resolveCrestPath(faction),
                TooltipFactionNames.resolveLongName(faction, coreFactionId),
                KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_CORE_TERRITORY),
                SystemCellTooltip.NO_SCORE));
    }
}
