package kmu.maplayers.base.render;

import kmu.maplayers.base.hover.MapLayerHoverGates;
import kmu.maplayers.base.layer.ScreenLayerPicks;
import kmu.maplayers.base.tooltip.MapHoverTooltip;

import java.util.Optional;
import java.util.function.Function;

/**
 * The five things a painting layer supplies for the framework to run its frame over: whether there is
 * anything to draw, the cache to refresh, the compositor to hand bands to, the layer's own hover
 * switches, and the layer's own hover box. Everything else about a frame - when each is asked, how
 * often, and what the answers are guarded by - is {@link SequencedMapLayerRenderer}'s, identically for
 * every layer.
 *
 * <p>The frame's subject {@code S} ties the parts together: the stand-down read finds it, and the
 * refresh and the box are each handed the one it found. For a layer offering several views it is the
 * view painting on the showing screen; a layer with nothing to choose between answers one constant
 * whenever its tab is up.
 *
 * @param subjectRead what the given screen's frame is painted under, or null to stand the frame down -
 *                    the layer's tab is open with nothing selected to paint. Asked with the screen
 *                    rather than resolving one, so the subject and the preferences it is refreshed
 *                    under come off one reading of which screen is showing
 * @param cache       what the frame draws from, refreshed once per frame under the subject found
 * @param compositor  what emits each band from that cache
 * @param hoverGates  the layer's own switches for the two kinds of cursor feedback
 * @param tooltipRead the box the layer shows for the cell under the cursor, under the subject painting;
 *                    empty for a subject that says nothing about one
 * @param <S>         what a frame is painted under
 */
public record MapLayerFrameParts<S>(
    Function<ScreenLayerPicks, S> subjectRead,
    MapFrameCache<S> cache,
    MapFrameCompositor compositor,
    MapLayerHoverGates hoverGates,
    Function<S, Optional<MapHoverTooltip>> tooltipRead) {
}
