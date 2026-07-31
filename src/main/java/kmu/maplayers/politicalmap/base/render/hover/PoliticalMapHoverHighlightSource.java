package kmu.maplayers.politicalmap.base.render.hover;

import kmu.maplayers.base.hover.HoverHighlightSource;
import kmu.maplayers.base.theme.ElementPaint;
import kmu.maplayers.politicalmap.base.render.style.MapPalettes;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritories;

import java.awt.Color;
import java.util.List;

/**
 * The political map's answers about the cell under the cursor: read straight off the draw
 * lists this frame painted, so a highlight can only ever trace ground the map is showing.
 *
 * <p>The three answers all turn on who holds the hovered system. Its painted extent is the
 * shape the build recorded for that cell; the loops it might sit inside are its holder's traced
 * borders, which carry one ring per disjoint cluster and per enclave; and the shade is the
 * holder's own palette, or the shared neutral colour where nothing owns the ground.
 *
 * <p>The border loops are the holder's rather than the hovered cluster's because the build bakes
 * them per bloc - which of them encloses this particular cell is a geometric question, and one
 * the highlight pass answers for itself.
 *
 * <p>A value over one frame's territories rather than a long-lived collaborator: the draw lists
 * are replaced wholesale by a rebuild, and wrapping the current ones per frame is what lets the
 * highlight's memoisation compare by identity and notice when they have moved on.
 *
 * @param territories the draw lists the frame painted
 */
public record PoliticalMapHoverHighlightSource(PoliticalMapTerritories territories)
        implements HoverHighlightSource {

    @Override
    public List<float[]> resolveCandidateFrontierLoopsOf(String cellId) {
        // Factionless ground (decivilised, or uninhabited while its outline is drawn) fuses into
        // no territory, so it has no frontier at all and offers no candidates - its cell still
        // washes, just without a halo.
        var holder = territories.getHolderBySystemId().get(cellId);
        if (holder == null) {
            return List.of();
        }
        var territory = territories.getFactionTerritoryByFactionId().get(holder.factionId());
        return territory == null ? List.of() : territory.borderLoops();
    }

    @Override
    public Color resolveHighlightColourOf(String cellId, ElementPaint paletteChoice) {
        return MapPalettes.pickHolderPaletteColor(
                paletteChoice,
                territories.getHolderBySystemId().get(cellId),
                territories.getNeutralColor());
    }

    @Override
    public List<double[]> resolvePaintedExtentOf(String cellId) {
        // A cell the build dropped entirely draws nothing, so it is absent from the extents
        // rather than present with an empty shape; both mean the same thing to the caller.
        var paintedExtent = territories.getFillPolygonByCellId().get(cellId);
        return paintedExtent == null ? List.of() : paintedExtent;
    }
}
