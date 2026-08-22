package kmu.maplayers.base.sidebar.runtime;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.ui.coreui.ReflectiveCoreUiComponentRepainter;
import kmlib.starsector.ui.map.probes.VanillaMapTooltipProbe;

import kmu.starsector.listeners.SectorListeners;

import static kmu.KmuWiringSteps.runGuardedStep;

/**
 * Standing up the sidebar on every screen it draws on, and opening each one at the fold its save
 * was left at.
 *
 * <p>Two steps in one place because the second is only meaningful after the first: a fold restored
 * onto a host whose listeners never registered would be a preference nothing reads. Guarded apart
 * all the same, so a save that cannot be read for its folds still gets its sidebars.
 *
 * <p>Final class with a private constructor: pure-function utility, no instance state.
 */
public final class SidebarInstaller {

    private SidebarInstaller() {
        // utility class, no instances.
    }

    /**
     * Registers every host's sidebar listeners and restores its fold, each step behind its own
     * failure boundary.
     *
     * @param sector the loaded sector; null leaves the sidebar uninstalled rather than throwing
     */
    public static void installAll(SectorAPI sector) {

        runGuardedStep(
            () -> installPoliticalMapSidebar(sector),
            "Failed to install KMU political map sidebar");

        // Open every sidebar at the fold this save was left at. Per load rather than at construction:
        // the hosts are process-lifetime singletons built before any sector exists, so this is the only
        // point they can read the save - and it also stops the previous save's folds leaking into this
        // one. Each host reads its own key, so the screens' folds stay independent.
        runGuardedStep(
            () -> {
                for (var host : SidebarHosts.getRegisteredHosts()) {
                    host.restoreFoldFromSave();
                }
            },
            "Failed to restore KMU political map sidebar folds");
    }

    /**
     * Clears the sidebar from every screen it draws on, for a player switching the map layers off.
     *
     * <p>Both classes rather than the roster, because the roster says which hosts exist and this has
     * to clear whatever is registered - including a host's pair left by a roster that has since
     * changed.
     *
     * @param sector the loaded sector; null is a no-op
     */
    public static void uninstallAll(SectorAPI sector) {

        runGuardedStep(
            () -> {
                SectorListeners.removeListener(sector, SidebarRenderer.class);
                SectorListeners.removeListener(sector, SidebarInput.class);
            },
            "Failed to remove KMU political map sidebar");
    }

    // Registers the sidebar's render and input listeners for every screen it draws on, walking the one
    // roster the rest of the mod asks its host-blind questions of. One SidebarRenderer and one
    // SidebarInput per host - a render listener that draws the panel and an input listener that reads
    // its clicks, notch, and hotkeys (a render pass gets no events to consume). All transient, so
    // none enters a save and each load stands its own up: the host's active-layer pick lives in
    // sector memory rather than on the listener, and the render listeners' cached GL text has no
    // business in a save. Remove-then-add clears whatever this session already registered before
    // re-adding every host, so a second install cannot leave two of each drawing on one screen.
    //
    // Written out rather than made through SectorListeners, which registers one listener of one
    // class: here two classes are cleared before either is re-added, so a host is never left with
    // one half of its pair registered while the loop is partway through the roster.
    static void installPoliticalMapSidebar(SectorAPI sector) {

        if (sector == null) {
            return;
        }

        var listenerManager = sector.getListenerManager();
        if (listenerManager == null) {
            return;
        }

        listenerManager.removeListenerOfClass(SidebarRenderer.class);
        listenerManager.removeListenerOfClass(SidebarInput.class);

        for (var host : SidebarHosts.getRegisteredHosts()) {
            // Its own probe per renderer rather than one shared: the probe warns once per instance when
            // its read breaks, so a shared one would let the first screen to fail silence the news on
            // the other. The repaint binding is the shared singleton beside it - a stateless forwarder
            // over the GL surface, with no per-screen state to keep apart.
            listenerManager.addListener(
                new SidebarRenderer(
                    host,
                    new VanillaMapTooltipProbe(),
                    ReflectiveCoreUiComponentRepainter.INSTANCE),
                true);
            listenerManager.addListener(new SidebarInput(host), true);
        }
    }
}
