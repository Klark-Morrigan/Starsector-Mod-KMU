package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.FactionAPI;

/**
 * How a faction is named on a tooltip row. One definition of the fallback so every row that names a
 * faction - a ranked standing, a claim, a status line - reads the same when the sector no longer
 * knows the ID it was handed.
 */
final class TooltipFactionNames {

    private TooltipFactionNames() {
    }

    /**
     * A faction's long display title, falling back to its ID when the faction will not resolve, so a
     * row is never nameless even for an ID the sector no longer knows. A row shows one faction per
     * line, so a bare ID reads better than a blank where its name belongs.
     *
     * @param faction   the faction to name, or null when the ID would not resolve
     * @param factionId the ID to fall back to
     * @return the faction's long title, or {@code factionId} when there is no faction
     */
    static String resolveLongName(FactionAPI faction, String factionId) {
        return faction == null
            ? factionId
            : faction.getDisplayNameLong();
    }
}
