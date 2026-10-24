package kmu.maplayers.base.ribbon;

import java.util.List;

/**
 * One cell's whole band, baked: its runs in draw order, each already a coloured patch of
 * triangles sitting inside the cell's own ring.
 *
 * <p>Held as its own value beside the cell's draw record rather than inside it, because a band is
 * what a layer lays inside a cell while the draw record is the cell itself - and because a band
 * exists for only a small share of the cells the map paints.
 *
 * @param bands the cell's runs in draw order, starting from the cell's top centre; empty on a
 *              cell that draws no band at all
 */
public record CellRibbon(
    List<RibbonBand> bands) {

    /** The cell that draws no band: no runs to report, or no room to report it in. */
    public static final CellRibbon NONE = new CellRibbon(List.of());

    public CellRibbon {
        bands = List.copyOf(bands);
    }

    /**
     * @return true when the cell draws nothing, so the render pass can skip it without reading
     *         into it
     */
    public boolean isEmpty() {
        return bands.isEmpty();
    }
}
