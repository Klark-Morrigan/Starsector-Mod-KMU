package kmu.maplayers.base.installation;

import kmu.maplayers.base.hover.MapHoverState;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.base.refresh.MovingSystems;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * One sector's installed map machinery: what the map layers derive from that sector and remember
 * between frames, made when the layers are installed on it and released when they are removed.
 *
 * <p>Everything the layers draw is derived from one sector, so every holder behind that drawing is
 * a fact about one sector rather than about the process. Two sectors sharing one holder would not
 * merely be unsupported: the caches behind the drawing reconcile by system id, so a system present
 * in both at different positions is not seen to have moved, and each sector keeps the shapes the
 * other cut rather than overwriting them.
 *
 * <p>What this type settles is the lifetime: one installation per sector, made and released by
 * {@link MapLayerInstallations}. Every holder below goes with the installation, so each needs no
 * discard of its own and no way to be told which sector it is now looking at - and a sector begins
 * from nothing rather than from what the sector before it left.
 *
 * <p>The holders it names are the framework's own. What a layer derives from the sector is held
 * through {@link #resolveMachinery} instead, which is what lets an installation own the lifetime of
 * a renderer and its caches without this package naming the layers those live in.
 */
public final class MapLayerInstallation {

    // Whether this installation has been released. Kept rather than inferred from an emptied
    // holder, because a caller can still be holding a reference the index has already let go of: a
    // resolution taken at the top of a frame outlives a removal that happens during it.
    //
    // Volatile because that is precisely a cross-thread read: the release is written on the
    // campaign thread and the holder that outlived it is read on the render thread, so a
    // non-volatile flag could go on answering "not released" for as long as the frame keeps
    // drawing through it - which is the one question this field exists to answer.
    private volatile boolean isDisposed;

    // What the cursor is over on this sector's map, carried from the render pass that can resolve
    // it to the highlight and the box that report it.
    private final MapHoverState hoverState = new MapHoverState();

    // Where this sector's systems were last seen, and so which of them are drifting rather than
    // sitting still.
    private final MovingSystems movingSystems = new MovingSystems();

    // What went stale in this sector since each consumer last looked.
    private final MapLayerRefreshBoard refreshBoard = new MapLayerRefreshBoard();

    // What the layers derive from this sector, made on first ask and released with this
    // installation. Keyed by the class of the thing held, so one sector has exactly one of each and
    // a resolution hands back what it asks for without a cast of its own.
    //
    // Concurrent for the reason the index above it is: the campaign thread installs and removes
    // while a frame on the render thread resolves what it is about to draw through.
    private final Map<Class<? extends InstalledMachinery>, InstalledMachinery> machineryByType =
        new ConcurrentHashMap<>();

    /**
     * Releases what this installation holds, after which it answers {@link #isDisposed}.
     *
     * <p>Called by {@link MapLayerInstallations} when a sector's machinery is replaced, removed, or
     * discarded on load. Releasing rather than dropping is what keeps a holder with something to
     * hand back - a GL buffer, a registered script - from being left to the collector.
     */
    public void disposeMachinery() {

        machineryByType.values().forEach(InstalledMachinery::disposeMachinery);
        machineryByType.clear();
        isDisposed = true;
    }

    /**
     * @return whether this installation has been released, so a caller holding one it did not just
     *         resolve can tell that the sector behind it has gone rather than drawing through it
     */
    public boolean isDisposed() {
        return isDisposed;
    }

    /**
     * @return the holder this sector's map render publishes the cursor's cell to and its highlight
     *         and hover box read it from, so a cursor read over one sector's map cannot light a
     *         cell - or name a system - on another's
     */
    public MapHoverState resolveHoverState() {
        return hoverState;
    }

    /**
     * This sector's machinery of one kind, made on the first ask and held until this installation
     * is disposed - which is what makes a layer's renderer, and the caches behind it, one sector's
     * rather than the process's.
     *
     * <p>Keyed by the class asked for rather than by a name, so the caller gets back the type it
     * asked about and one sector cannot come to hold two of a kind. The installation never names
     * what it is holding: whoever wants a piece of machinery supplies the way to make one, which is
     * what keeps this package clear of the layers that live downstream of it.
     *
     * <p>Resolved fresh from {@link MapLayerInstallations} by every caller that needs one, so a
     * disposed installation is not the one asked; what it made after disposal would answer for a
     * sector nothing draws and would never be released.
     *
     * @param machineryType   the kind being asked for, and the key it is held under
     * @param createMachinery makes this sector's, called only where it has none yet
     * @param <T>             the kind being asked for, so the caller needs no cast
     * @return this sector's machinery of that kind
     */
    public <T extends InstalledMachinery> T resolveMachinery(
            Class<T> machineryType,
            Supplier<T> createMachinery) {

        return machineryType.cast(
            machineryByType.computeIfAbsent(machineryType, key -> createMachinery.get()));
    }

    /**
     * @return the tracker this sector's poll observes hyperspace positions into and its geometry
     *         reads the movers out of, so a system in one sector cannot be judged to have drifted
     *         by what another sector saw
     */
    public MovingSystems resolveMovingSystems() {
        return movingSystems;
    }

    /**
     * @return the board this sector's producers raise refresh signals on and its overlays read them
     *         from, so a change in one sector cannot mark another sector's cache stale
     */
    public MapLayerRefreshBoard resolveRefreshBoard() {
        return refreshBoard;
    }
}
