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

    // What the toggle was last applied as, so a settings change can tell whether this knob is the
    // one that moved. Null until the first apply, which reads as "not what the setting says" and so
    // applies - the safe direction for a state nothing has established yet.
    private static Boolean appliedMapLayersEnabled;

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
        // one place they are named. Registered whatever the feature toggle says: the registry is
        // process-level and holds no sector state, so a map with nothing installed draws through
        // nothing at all - and gating it here would make the toggle need a restart rather than
        // taking effect where the player made it.
        KmuWiringSteps.runGuardedStep(
            MapLayers::registerAll,
            "Failed to register KMU map layers");

        // React to the feature toggle where the player flips it, rather than at the next load.
        KmuWiringSteps.runGuardedStep(
            () -> KmuLunaSettings.runOnSettingsChange(
                () -> applyMapLayerFeatureIfToggled(Global.getSector())),
            "Failed to install KMU feature toggle listener");
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

        applyMapLayerFeature(sector);
    }

    // Brings the sector into line with the map-layer toggle: everything stood up, or everything
    // taken back. Named for what it does either way, since a method called install that also
    // uninstalls tells a reader half of what it is for.
    //
    // Applied unconditionally on load, whatever was applied before, because the sector is new and
    // carries none of the previous one's wiring.
    static void applyMapLayerFeature(SectorAPI sector) {

        var areMapLayersEnabled = KmuFeatureSettings.areMapLayersEnabled();

        appliedMapLayersEnabled = areMapLayersEnabled;

        if (!areMapLayersEnabled) {
            uninstallMapLayers(sector);
            return;
        }

        // Before the render surfaces, because its save heal repairs what the terrain then reads.
        PoliticalMapInstaller.installAll(sector);

        // Before the hover box, which asks the frame preparation claim this stands up for a frame.
        MapSurfaceInstaller.installAll(sector);

        SidebarInstaller.installAll(sector);
        MapHoverInstaller.installAll(sector);
    }

    // The same, run when the settings change rather than when a save loads, and only when this
    // toggle is what changed.
    //
    // LunaLib announces that the settings changed rather than which setting did, so acting every
    // time would tear down and rebuild the whole overlay whenever the player moved an unrelated
    // slider - dropping the hover box's cached text and re-arming the frame claim for nothing.
    // Comparing against what was last applied makes this the edge it reads as.
    static void applyMapLayerFeatureIfToggled(SectorAPI sector) {

        if (appliedMapLayersEnabled != null
                && appliedMapLayersEnabled == KmuFeatureSettings.areMapLayersEnabled()) {
            return;
        }
        applyMapLayerFeature(sector);
    }

    // Takes back everything the four installers stand up. All of them and not only the surfaces:
    // across a load their listeners and scripts would be gone by themselves, being transient, but a
    // player switching the overlay off mid-campaign is still running every one of them.
    private static void uninstallMapLayers(SectorAPI sector) {

        MapHoverInstaller.uninstallAll(sector);
        SidebarInstaller.uninstallAll(sector);
        MapSurfaceInstaller.uninstallAll(sector);
        PoliticalMapInstaller.uninstallAll(sector);
    }
}
