package kmu.maplayers.politicalmap.base.render.hover;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.hover.HoverHighlightSource;
import kmu.maplayers.base.theme.ElementPaintSelection;
import kmu.maplayers.politicalmap.base.render.style.MapPalettes;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritories;

import java.awt.Color;
import java.util.List;
import java.util.Map;

/**
 * The political map's answers about the cell under the cursor: read straight off the draw
 * lists this frame painted, so a highlight can only ever trace what the map is showing.
 *
 * <p>Both derived answers turn on who holds the hovered system. The loops it might sit inside
 * are its holder's traced borders, which carry one ring per disjoint cluster and per enclave;
 * and the shade is the holder's own palette, or the shared neutral colour where nothing owns
 * the cell. The cell's painted extent is not derived at all - the frame's shapes pass
 * straight through, which is how the halo and the cursor read agree by construction rather
 * than by two lookups happening to land alike.
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
public record PoliticalMapHoverHighlightSource(
    PoliticalMapTerritories territories) implements HoverHighlightSource {

    @Override
    public Map<SystemKey, List<double[]>> getFillPolygonByCellKey() {
        // Handed over as the build holds them, not copied: the highlight memoises on these
        // instances, and they are the same ones the cursor was hit-tested against.
        return territories.getFillPolygonByCellKey();
    }

    @Override
    public List<float[]> resolveCandidateFrontierLoopsOf(SystemKey cellKey) {

        // A factionless cell (decivilised, or uninhabited while its outline is drawn) fuses into
        // no territory, so it has no frontier at all and offers no candidates - its cell still
        // washes, just without a halo.
        var holder = territories.getOccupancy().getHolderBySystemKey().get(cellKey);
        if (holder == null) {
            return List.of();
        }
        // Every loop the holder strokes anywhere, across all of its bodies. Handing over only the
        // body this cell sits in would mean deciding here which body that is - the containment
        // question the highlight already answers to pick its loop, and answering it twice by two
        // rules is how a halo comes to trace a frontier the cursor is not inside.
        return territories.listCandidateBorderLoopsOf(holder.factionId());
    }

    @Override
    public Color resolveHighlightColourOf(SystemKey cellKey, ElementPaintSelection paintSelection) {
        // The holder off the live occupancy; the neutral off the scheme the build painted in, so
        // the halo over an unowned cell is the shade its own outline drew in.
        return MapPalettes.pickHolderPaletteColour(
            paintSelection,
            territories.getOccupancy().getHolderBySystemKey().get(cellKey),
            territories.getBuildInputs().styling().readNeutralColour());
    }
}
