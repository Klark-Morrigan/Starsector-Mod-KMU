package kmu.maplayers.base.installation;

/**
 * Something one sector's {@link MapLayerInstallation} holds and releases with itself.
 *
 * <p>The installation owns the lifetime of what a layer derives from its sector - a renderer, and
 * the caches behind it - without naming any of it. That is the whole of what this seam is for: the
 * things being held live in the layer and feature packages, which are downstream of this one, so an
 * installation that named them would point back at its own dependents. Holding them through a
 * release contract keeps the edge one-way while still making disposal certain.
 *
 * <p>Certain rather than incidental, because some of what is held owns resources the collector will
 * not free in time: a cached label holds a GL buffer, and a sector removed mid-session would leak
 * every one it had built if release were left to a finalizer sweep.
 */
public interface InstalledMachinery {

    /**
     * Releases what this holds, called once when the installation holding it is disposed.
     *
     * <p>Nothing is asked of it afterwards: the installation is disposed with it and every caller
     * resolves a fresh one from the index, so this need not leave a usable object behind.
     */
    void disposeMachinery();
}
