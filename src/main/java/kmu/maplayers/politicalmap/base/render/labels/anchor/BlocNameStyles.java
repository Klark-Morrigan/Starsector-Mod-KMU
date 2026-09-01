package kmu.maplayers.politicalmap.base.render.labels.anchor;

import kmu.maplayers.base.theme.ElementStyle;
import kmu.maplayers.politicalmap.base.render.style.FactionPaletteSlot;
import kmu.settings.KmuPoliticalMapTerritorySettings;

/**
 * How a cluster name is coloured and faded, per holder group: the outer-border colour choice
 * the name inherits (the same one the cluster border reads, so a name never drifts from the
 * border it labels) beside that group's own name opacity.
 *
 * <p>Two groups rather than one because independent space is styled apart from the core
 * factions throughout the map, and a name follows whichever group its bloc was classified
 * into. That classification is what makes this a political value: the search that places a
 * name knows nothing of factions or independence, so the resolved shade reaches it as a plain
 * colour and the group split stays on this side of the seam.
 *
 * @param factionNameStyle     how a core faction's names are coloured and faded
 * @param independentNameStyle how independent space's names are coloured and faded
 */
public record BlocNameStyles(
    ElementStyle factionNameStyle,
    ElementStyle independentNameStyle) {

    /**
     * Reads the live per-group name styling: each group's outer-border colour choice beside
     * its name opacity, from the politics-visuals "Faction systems" and "Independent systems"
     * sections. Read once per rebuild, since every cluster of a group resolves its shade
     * against the same pair.
     *
     * @return the two groups' name styling as the settings currently hold it
     */
    public static BlocNameStyles readFromLunaSettings() {
        return new BlocNameStyles(
            new ElementStyle(
                FactionPaletteSlot.resolvePaintSelectionOf(KmuPoliticalMapTerritorySettings.getFactionOuterBorderColour()),
                KmuPoliticalMapTerritorySettings.getFactionNameOpacity()),
            new ElementStyle(
                FactionPaletteSlot.resolvePaintSelectionOf(KmuPoliticalMapTerritorySettings.getIndependentOuterBorderColour()),
                KmuPoliticalMapTerritorySettings.getIndependentNameOpacity()));
    }
}
