package kmu.maplayers.base.layer;

import com.fs.starfarer.api.Global;

import kmu.maplayers.base.machinery.InstalledMachinery;
import kmu.maplayers.base.machinery.SectorMapMachinery;
import kmu.maplayers.base.render.MapLayerRenderer;

import org.apache.log4j.Logger;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The one way a pass reaches the drawn layer's renderer on a sector, and the boundary that pass's
 * work with it runs behind.
 *
 * <p>The map surface and the hover box are both driven by the game with nothing under them that
 * catches, so a layer throwing from either would end the game with the map open. Here a layer that
 * throws is switched off on the sector it threw on, and every later pass over that sector reads it
 * as a layer that draws nothing: the map goes on drawing, and so does any other layer the player
 * picks.
 *
 * <p>Off rather than retried. A layer that failed on one frame's state would have drawn again a frame
 * later, but nothing here can tell that from a layer failing on the sector itself, which would throw
 * on every frame and bury the first trace under one a frame. Held by the sector's machinery, so the
 * switch clears exactly where trying again means something - the next load, or the map layers being
 * switched off and back on - and never once a frame.
 *
 * <p>Logged rather than reported to the player: every layer registered today is this mod's own, so a
 * layer's fault has no third party for the compatibility notice to name.
 *
 * <p>Holding the draw itself to account is not enough on its own: a throw mid-draw also leaves
 * whatever GL state the draw had set up. Every bracket under the pass - an attribute save, a pushed
 * matrix, an open primitive - therefore closes itself in a finally block, so the frame is handed back
 * balanced by the time the throw reaches here. Restoring it from here instead would take reading the
 * stack depths back from the driver, which stalls the frame and is fatal under Fast Rendering.
 *
 * <p>Read and written on the game thread alone - the render pass and the UI passes asking for the
 * hover box all run there - so the state needs no publication guarantee of its own.
 */
public final class DrawnLayerGuard implements InstalledMachinery {

    // The sector this guard belongs to: the one a layer is asked for its renderer over, and whose
    // hover a switched-off layer leaves parked.
    private final SectorMapMachinery machinery;

    // Where a layer being switched off is said, with the trace that did it.
    private final Logger log;

    // The layers switched off on this sector. Held by layer rather than by renderer, because
    // resolving the renderer is itself part of what can throw.
    private final Set<MapLayer> switchedOffLayers = new HashSet<>();

    // Reached through resolveGuardIn, so the only guards that exist are ones machinery holds.
    DrawnLayerGuard(SectorMapMachinery machinery, Logger log) {
        this.machinery = machinery;
        this.log = log;
    }

    /**
     * The guard {@code machinery}'s passes share, made on the first ask and released with the
     * machinery holding it.
     *
     * @param machinery the machinery installed on the sector being drawn
     * @return that sector's guard
     */
    public static DrawnLayerGuard resolveGuardIn(SectorMapMachinery machinery) {

        return machinery.resolveMachinery(
            DrawnLayerGuard.class,
            () -> new DrawnLayerGuard(machinery, Global.getLogger(DrawnLayerGuard.class)));
    }

    /**
     * Runs {@code layerWork} over the drawn layer's renderer on this sector, unless nothing draws.
     *
     * @param layerWork the pass's work with the renderer
     * @see #callOnDrawnRenderer
     */
    public void runOnDrawnRenderer(Consumer<MapLayerRenderer> layerWork) {

        callOnDrawnRenderer(
            layerRenderer -> {
                layerWork.accept(layerRenderer);
                return null;
            },
            null);
    }

    /**
     * Answers {@code layerWork} over the drawn layer's renderer on this sector, or
     * {@code answerWhereNothingDraws} where nothing does.
     *
     * <p>Nothing draws where no layer is picked yet, where the screen's layers have faded off it,
     * where the drawn layer supplies no renderer, and where it was switched off on this sector. They
     * are one answer on purpose: every pass treats them alike, so neither a switch-only tab nor a
     * failed layer needs a case of its own in any of them.
     *
     * <p>A layer throwing from its renderer or from {@code layerWork} is switched off here, and this
     * call answers as though it drew nothing.
     *
     * @param layerWork               the pass's work with the renderer
     * @param answerWhereNothingDraws what the pass reads where nothing draws
     * @param <T>                     what the pass reads back
     * @return what {@code layerWork} answered, or {@code answerWhereNothingDraws}
     */
    public <T> T callOnDrawnRenderer(
            Function<MapLayerRenderer, T> layerWork,
            T answerWhereNothingDraws) {

        var drawnLayer = MapLayerRegistry.getDrawnLayer();

        if (drawnLayer == null || switchedOffLayers.contains(drawnLayer)) {
            return answerWhereNothingDraws;
        }
        try {
            // Asked of this sector's machinery rather than the running game's: the roster is the
            // process's while a renderer is one sector's, so a renderer resolved any other way would
            // hand every surface the same one however many sectors were being drawn.
            var layerRenderer = drawnLayer.resolveRenderer(machinery);

            return layerRenderer == null
                ? answerWhereNothingDraws
                : layerWork.apply(layerRenderer);

        } catch (LinkageError | RuntimeException failure) {

            switchLayerOff(drawnLayer, failure);
            return answerWhereNothingDraws;
        }
    }

    /**
     * Nothing to release: which layers failed is all this holds, and it goes with the machinery.
     */
    @Override
    public void disposeMachinery() {
    }

    // Takes the layer off this sector for good, and says so once with the trace that did it.
    private void switchLayerOff(MapLayer failedLayer, Throwable failure) {

        switchedOffLayers.add(failedLayer);

        // The last hover the layer published would otherwise stand for the rest of the sector, with
        // no pass of the layer's left to read the cursor again and park it.
        machinery
            .resolveHoverState()
            .clearHover();

        log.error("Map layer '" + failedLayer.getId() + "' failed and is switched off on this sector"
            + " until the next load, or until the map layers are switched off and back on.", failure);
    }
}
