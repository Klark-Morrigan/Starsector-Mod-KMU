package kmu;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.MapLayers;
import kmu.maplayers.base.render.MapSurfaceInstaller;
import kmu.maplayers.base.sidebar.runtime.SidebarInstaller;
import kmu.maplayers.base.tooltip.MapHoverInstaller;
import kmu.maplayers.politicalmap.base.PoliticalMapInstaller;
import kmu.settings.KmuFeatureSettings;
import kmu.settings.KmuLunaSettings;
import kmu.settings.KmuRetiredSettings;
import kmu.starsector.colonies.ColonySightingInstaller;
import kmu.ui.context.MarketUiContextInstaller;

/**
 * KMU's entry point: the class the game constructs from the {@code modPlugin} field in
 * {@code mod_info.json}, once per launch, and calls again on every save load.
 *
 * <p>What it does is order. Each feature states its own start-up wiring on an installer beside the
 * code that wiring stands up, and this names those installers in the sequence they have to run in -
 * so a feature is added by writing its installer and one line here, rather than by editing the one
 * file every feature would otherwise have to be threaded through. Every step an installer runs is
 * guarded on its own ({@link KmuWiringSteps}), which is why the calls below are not guarded again:
 * a list of guarded steps cannot itself throw.
 *
 * <p>The order is not arbitrary and is the reason this reads as a list rather than a set. What each
 * entry has to come after is stated beside it.
 *
 * <p>The name breaks the naming rules the rest of the mod follows, and does so deliberately.
 * {@code <PREFIX>_ModPlugin} is the Starsector ecosystem's idiom for this one class, so a log line
 * or a stack trace naming it says which mod it came from without the reader knowing KMU's package
 * layout - and every enabled mod contributes one of these to the same running game, where a bare
 * {@code ModPlugin} would say nothing. No other class in KMU is named this way, and none should be.
 */
public class KMU_ModPlugin extends BaseModPlugin {

    @Override
    public void onApplicationLoad() {

        // App-scoped, once per launch: register KMU's LunaLib settings bindings before any save
        // loads. LunaLib is a hard dependency, so it has already loaded by the time this runs.
        KmuWiringSteps.runGuardedStep(
            KmuLunaSettings::installBindings,
            "Failed to install KMU LunaLib settings bindings");

        // Shed the values of settings withdrawn since shipping, LunaLib pruning nothing itself.
        // After the bindings and behind its own boundary: it is housekeeping over keys nothing
        // reads, so a failure here must cost only the orphaned values. One seam owns the full set,
        // so this call site never has to track which ids are retired - the app-load counterpart of
        // the save migrations the political map installs.
        KmuWiringSteps.runGuardedStep(
            KmuRetiredSettings::clearRetiredSettings,
            "Failed to clear retired KMU settings");

        // Wire the concrete map layers into the framework registry once per launch, before any
        // sector map can open. The registry stays agnostic to which views exist; this is the
        // one place they are named.
        KmuWiringSteps.runGuardedStep(
            MapLayers::registerAll,
            "Failed to register KMU map layers");
    }

    @Override
    public void onGameLoad(boolean newGame) {

        super.onGameLoad(newGame);

        // Read once and handed down, so every installer wires the same sector. Resolving it per
        // installer would let a load that replaced the sector halfway leave the wiring split across
        // two of them.
        var sector = Global.getSector();

        MarketUiContextInstaller.installAll(sector);

        // Kept current whether or not the map is drawing, since it is the map that would lose by a
        // gap in it: a register left unwritten while the layers are off would have every gated
        // colony unobserved again when they are switched back on.
        ColonySightingInstaller.installAll(sector);

        installMapLayers(sector);
    }

    // The map layers and everything that stands them up, or their removal when the player has
    // switched the feature off.
    //
    // Read once per load rather than per frame, so a change takes effect on the next save load -
    // which the setting's own description says, a deferred knob that does not say so reading as a
    // broken one. The layer registry is stood up either way, at application load: it holds no
    // sector state, and registering into it costs a map with nothing installed to draw through
    // nothing at all.
    static void installMapLayers(SectorAPI sector) {

        if (!KmuFeatureSettings.areMapLayersEnabled()) {

            // Only the surfaces have anything to take back. Every listener and script the four
            // installers register is transient and simply never registered again; terrain is an
            // entity, and one left behind would sit in the save for as long as it exists.
            MapSurfaceInstaller.uninstallAll(sector);
            return;
        }

        // Before the render surfaces, because its save heal repairs what the terrain then reads.
        PoliticalMapInstaller.installAll(sector);

        // Before the hover box, which asks the frame preparation claim this stands up for a frame.
        MapSurfaceInstaller.installAll(sector);

        SidebarInstaller.installAll(sector);
        MapHoverInstaller.installAll(sector);
    }
}
