package kmu.politicalmap.render;

import kmu.politicalmap.domain.politics.DominantOwner;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The built map state a single full rebuild produces and the incremental refresh
 * then edits in place: the two draw lists the renderer paints, plus the derivation
 * inputs an incremental re-shape needs to rebuild a handful of cells against the same
 * ownership and styles the full rebuild used.
 *
 * <p>{@code styledCellBySystemId} and {@code factionTerritoryByFactionId} are the
 * render output - the per-cell seam/outline records and each faction's fill and
 * national border. The rest are retained inputs: {@code ownerBySystemId} and
 * {@code decivilisedSystemIds} say who holds each system, and the four styles and
 * {@code neutralColor} say how each category draws.
 *
 * <p>The three maps ({@code styledCellBySystemId}, {@code factionTerritoryByFactionId},
 * {@code ownerBySystemId}) are mutated in place by the incremental refresh, which
 * replaces just the cells and factions an ownership change touched. The styles, the
 * decivilised set, and the neutral color are set once at build and only read after,
 * so an incremental pass re-shapes against the exact inputs the full build baked in.
 */
record PoliticalMapDrawables(
        Map<String, StyledCell> styledCellBySystemId,
        Map<String, FactionTerritory> factionTerritoryByFactionId,
        Map<String, DominantOwner> ownerBySystemId,
        Set<String> decivilisedSystemIds,
        Color neutralColor,
        MapStyle factionStyle,
        MapStyle independentStyle,
        MapStyle decivilisedStyle,
        MapStyle uninhabitedStyle) {

    // An empty placeholder for the render path to fall back on after a failed first
    // build: the two draw lists are empty so the render is a harmless no-op, and the
    // next frame's retry replaces it with a real build before any incremental pass -
    // which needs the styles - can run, so the null styles here are never read.
    static PoliticalMapDrawables empty() {
        return new PoliticalMapDrawables(new LinkedHashMap<>(), new LinkedHashMap<>(),
                new LinkedHashMap<>(), new LinkedHashSet<>(), Color.GRAY,
                null, null, null, null);
    }

    // True when there is nothing to paint, so the renderer can skip the GL state
    // push entirely.
    boolean isEmpty() {
        return styledCellBySystemId.isEmpty() && factionTerritoryByFactionId.isEmpty();
    }
}
