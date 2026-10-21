package kmu.maplayers.base.tooltip.layout;

import kmlib.starsector.ui.widgets.tooltip.TooltipRow;

import java.util.List;

/**
 * Everything one composition of a hover box read off the sector: the lines heading it and the body
 * beneath. What {@link CellBoxMemo} holds between frames, so both halves come from one reading and
 * the box never heads one reading of the system with another's findings.
 *
 * @param titleRows the layer's lines read with the system name as the heading, empty where it heads
 *                  its box with the name alone
 * @param body      the layer's blocks and how deep they go
 */
record ComposedCellBox(
    List<TooltipRow> titleRows,
    ComposedCellBody body) {

    ComposedCellBox {
        titleRows = List.copyOf(titleRows);
    }

    /**
     * @return whether the composition found nothing to say about the system, which leaves the box
     *         undrawn rather than drawing a name that only echoes the cursor
     */
    boolean isEmpty() {

        return titleRows.isEmpty()
            && body.blocks().isEmpty();
    }
}
