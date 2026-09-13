package kmu.maplayers.base.hover;

import kmlib.starsector.systems.SystemKey;

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
    Map<SystemKey, List<double[]>> fillPolygonByCellKey,
    Map<SystemKey, List<float[]>> frontierLoopsByCellKey) implements HoverHighlightSource {

    @Override
    public Map<SystemKey, List<double[]>> getFillPolygonByCellKey() {
        return fillPolygonByCellKey;
    }

    @Override
    public List<float[]> resolveCandidateFrontierLoopsOf(SystemKey cellKey) {
        return frontierLoopsByCellKey.getOrDefault(cellKey, List.of());
    }

    @Override
    public Color resolveHighlightColourOf(SystemKey cellKey, ElementPaintSelection paintSelection) {
        return Color.RED;
    }
}
