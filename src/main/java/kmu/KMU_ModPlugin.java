package kmu;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.diagnostics.ProfilingCaptureInstaller;
import kmu.maplayers.MapLayers;
import kmu.maplayers.base.chrome.MapChromeInstaller;
import kmu.maplayers.base.installation.MapLayerInstallations;
import kmu.maplayers.base.layer.MapLayerStandings;
import kmu.maplayers.base.refresh.MapSubstrateRefreshInstaller;
import kmu.maplayers.base.render.MapSurfaceInstaller;
import kmu.maplayers.base.sidebar.runtime.SidebarInstaller;
import kmu.maplayers.base.tooltip.MapHoverInstaller;
import kmu.maplayers.base.visibility.colonies.ColonySightingInstaller;
import kmu.maplayers.politicalmap.base.FilterSelectionHeal;
import kmu.settings.KmuFeatureSettings;
import kmu.settings.KmuLunaSettings;
import kmu.starsector.rat.RandomAssortmentOfThingsCompatibilityInstaller;
import kmu.starsector.rat.RandomAssortmentOfThingsCompatibilityMode;
import kmu.starsector.rat.RandomAssortmentOfThingsSettings;
import kmu.ui.context.MarketUiContextInstaller;

import java.util.List;

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

    // The switches this mod answers, each holding what it was last applied as so a settings change
    // can tell which of them moved. Listed here because deciding what runs is the entry point's
    // whole subject; what each half does is its installers'.
    //
    // This one is every layer's - a layer another mod registers with KMU draws through the same
    // installers, and this switch takes it down with KMU's own.
    private static final KmuToggledFeature mapLayers = new KmuToggledFeature(
        KmuFeatureSettings::areMapLayersEnabled,
        KMU_ModPlugin::installMapLayers,
        KMU_ModPlugin::uninstallMapLayers);

    // The condition editor, switched apart from the map because it is a separate feature and,
    // being unfinished, the one that ships off. Its whole per-save wiring is the tracker that
    // remembers which market a UI is open on, which nothing else reads - so a player who has not
    // asked for the editor runs none of it. The console command that opens the editor is registered
    // by data file rather than from here and so cannot be withheld; it reads the same switch itself.
    private static final KmuToggledFeature marketConditionManager = new KmuToggledFeature(
        KmuFeatureSettings::isMarketConditionManagerEnabled,
        MarketUiContextInstaller::installAll,
        MarketUiContextInstaller::uninstallAll);

    // Adapting to another mod, and so switched on its own: none of what it does is about anything
    // this mod paints, and a player who turned the overlay off has not thereby asked for a minimap
    // they cannot see to start rendering a whole sector again.
    //
    // The switch is the mode's own composed condition - the player's compatibility toggle, and that
    // mod present with its minimap switch on - so the wiring follows every one of the three: ours
    // through our settings listener, the mod's own switch through the listener on its settings, and
    // presence through the load that first applies this, presence being the one that cannot move
    // within a run.
    private static final KmuToggledFeature randomAssortmentOfThingsCompatibility =
        new KmuToggledFeature(
            RandomAssortmentOfThingsCompatibilityMode.createForLiveGame()::isEngaged,
            RandomAssortmentOfThingsCompatibilityInstaller::installAll,
            RandomAssortmentOfThingsCompatibilityInstaller::uninstallAll);

    // The switches above as one list, in the order they are applied in, because each of them is
    // asked twice - once against a freshly loaded sector and again wherever the player flips it -
    // and naming them per ask would mean two lists to keep the same. A feature in one and not the
    // other is a feature the player can switch and see nothing happen until they reload.
    private static final List<KmuToggledFeature> switchedFeatures = List.of(
        mapLayers,
        marketConditionManager,
        randomAssortmentOfThingsCompatibility);

    @Override
    public void onApplicationLoad() {

        // App-scoped, once per launch: register KMU's LunaLib settings bindings before any save
        // loads. LunaLib is a hard dependency, so it has already loaded by the time this runs.
        KmuWiringSteps.runGuardedStep(
            KmuLunaSettings::installBindings,
            "Failed to install KMU LunaLib settings bindings");

        // After the settings above, because which profiler is bound is itself a setting. Nothing
        // before this point is measured, which is what the shipped state asks for anyway: the
        // level ships off, so a player who never opens the readout binds nothing that keeps a
        // capture.
        ProfilingCaptureInstaller.installAll();

        // Wire the concrete map layers into the framework registry once per launch, before any
        // sector map can open. The registry stays agnostic to which views exist; this is the
        // one place they are named. Registered whatever the feature toggle says: the registry is
        // process-level and holds no sector state, so a map with nothing installed draws through
        // nothing at all - and gating it here would make the toggle need a restart rather than
        // taking effect where the player made it.
        KmuWiringSteps.runGuardedStep(
            MapLayers::registerAll,
            "Failed to register KMU map layers");

        // Answer every switch where the player flips it, rather than at the next load. One
        // registration for all of them: LunaLib announces the settings rather than the setting, so
        // there is one announcement to react to however many switches read it.
        KmuWiringSteps.runGuardedStep(
            () -> KmuLunaSettings.runOnSettingsChange(KMU_ModPlugin::applySwitchedFeatures),
            "Failed to install KMU feature switch listener");

        // The spotlight is the other thing a settings change can invalidate. Its own registration
        // rather than a passenger on the switch listener above, since it answers a different question:
        // what the settings made unpickable, not which feature they switched.
        KmuWiringSteps.runGuardedStep(
            FilterSelectionHeal::installHealOnSettingsChange,
            "Failed to install KMU map filter heal listener");

        // The same reaction to Random Assortment of Things' own saves, so the compatibility follows
        // that mod flipping its minimap switch live instead of at the next load. A second settings
        // source rather than a second kind of step, which is why it reads like the one above.
        KmuWiringSteps.runGuardedStep(
            () -> RandomAssortmentOfThingsSettings.runOnSettingsChange(
                KMU_ModPlugin::applySwitchedFeatures),
            "Failed to install KMU Random Assortment of Things settings listener");
    }

    @Override
    public void onGameLoad(boolean newGame) {

        super.onGameLoad(newGame);

        // Read once and handed down, so every installer wires the same sector. Resolving it per
        // installer would let a load that replaced the sector halfway leave the wiring split across
        // two of them.
        var sector = Global.getSector();

        // Kept current whether or not the map is drawing, since it is the map that would lose by a
        // gap in it: a register left unwritten while the layers are off would have every gated
        // colony unobserved again when they are switched back on.
        ColonySightingInstaller.installAll(sector);

        // Everything the map layers had installed on the sector this load replaced, discarded
        // before anything is installed on the loaded one. No other seam is told that a sector went
        // away, so this is the one point at which a previous save's drawing can be stopped from
        // outliving it. Ahead of the switch rather than inside it, since a load that finds the
        // overlay switched off has just as much of the previous sector's to discard.
        KmuWiringSteps.runGuardedStep(
            MapLayerInstallations::disposeEveryInstallation,
            "Failed to discard KMU map layer machinery from the previous save");

        // Whatever each switch reads as, against a sector carrying nothing of what was applied to
        // the one it replaced - so there is no previous state worth comparing against here.
        for (var feature : switchedFeatures) {
            feature.applyTo(sector);
        }
    }

    // Brings every switch to bear on the sector already wired, each acting only if it was the one
    // that moved.
    static void applySwitchedFeatures() {

        var sector = Global.getSector();

        for (var feature : switchedFeatures) {
            feature.applyToIfSwitched(sector);
        }
    }

    // What the map layers need of a sector while they are on, in the order they need it in.
    static void installMapLayers(SectorAPI sector) {

        // First, and taken back last: everything below derives what it draws from this sector, and
        // this is where that sector's share of it is held.
        MapLayerInstallations.installMachineryOn(sector);

        // Beside that machinery and above every layer: the sweep it drives keeps a record several
        // map families read, so it accrues for as long as the layers are standing at all rather
        // than for as long as one particular layer is on the bar.
        MapSubstrateRefreshInstaller.installAll(sector);

        // Every layer the player has on their bar, stood up on this sector; every tab they took off
        // it left unwired, so a layer nobody can reach costs nothing. Named as the framework's walk
        // rather than as one installer per layer: which layers exist is the registry's, and a layer
        // another mod ships is stood up here on the same terms as KMU's own.
        //
        // Before the render surfaces, because the political map's save heal - one of the halves this
        // walk runs - repairs what the terrain then reads.
        MapLayerStandings.applyArrangementTo(sector);

        // Before the hover box, which asks the frame preparation claim this stands up for a frame.
        MapSurfaceInstaller.installAll(sector);

        SidebarInstaller.installAll(sector);
        MapHoverInstaller.installAll(sector);

        // Last, and waited on by none of the above: the control it maintains goes on the game's own
        // filter row rather than on anything this mod draws, and the pick it moves is held apart
        // from the sector. It is also the only thing that says a screen has a control able to
        // reverse a hide, so it has to be stood up wherever the layers are - which is here, rather
        // than beside the sidebar it can switch off.
        MapChromeInstaller.installAll(sector);
    }

    // Everything those six stand up, taken back. All of them and not only the surfaces: across a
    // load their listeners and scripts would be gone by themselves, being transient, but a player
    // switching the overlay off mid-campaign is still running every one of them.
    static void uninstallMapLayers(SectorAPI sector) {

        MapChromeInstaller.uninstallAll(sector);
        MapHoverInstaller.uninstallAll(sector);
        SidebarInstaller.uninstallAll(sector);
        MapSurfaceInstaller.uninstallAll(sector);
        MapLayerStandings.standEveryLayerDownFrom(sector);
        MapSubstrateRefreshInstaller.uninstallAll(sector);

        // Last, mirroring the install: the six above are taken back through the state this holds,
        // so releasing it first would leave them undoing their work against nothing. Reached with a
        // sector nothing was ever installed on too, since a load with the overlay switched off
        // takes it back rather than declining to stand it up.
        MapLayerInstallations.uninstallMachineryFrom(sector);
    }
}
