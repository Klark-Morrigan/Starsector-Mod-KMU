package kmu.maplayers.base.hover;

import kmu.maplayers.base.theme.ElementPaintSelection;

import java.awt.Color;
import java.util.List;
import java.util.Map;

/**
 * A layer's answers about the frame it painted, as two plain maps.
 *
 * <p>It hands back the very instances it was built with, so the identity comparison every
 * highlight memo runs is exercised exactly as a real layer's answers would exercise it - a copy
 * per call would make every memo look stale.
 *
 * <p>The highlight colour is a constant: no geometry reads it.
 */
record HoverHighlightSourceFake(
    Map<String, List<double[]>> fillPolygonByCellId,
    Map<String, List<float[]>> frontierLoopsByCellId) implements HoverHighlightSource {

    @Override
    public Map<String, List<double[]>> getFillPolygonByCellId() {
        return fillPolygonByCellId;
    }

    @Override
    public List<float[]> resolveCandidateFrontierLoopsOf(String cellId) {
        return frontierLoopsByCellId.getOrDefault(cellId, List.of());
    }

    @Override
    public Color resolveHighlightColourOf(String cellId, ElementPaintSelection paintSelection) {
        return Color.RED;
    }
}
