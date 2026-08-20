package kmu.maplayers.base.visibility;

import kmlib.starsector.colonies.ColonyVisibility;
import kmlib.starsector.colonies.RevelationGate;

import kmu.settings.KmuMapLayerSettings;

import java.util.EnumSet;
import java.util.Set;

/**
 * The visibility state in force for one map-layer pass: the rule the pass reads colonies under,
 * and the override that puts a star system on the map whatever that rule says about it.
 *
 * <p>The colony rule is carried whole rather than as the loose flags it is made of, because a
 * surface handed one half of it draws under a rule nothing else on the map is drawing under - a
 * band counting a derelict the fill declines to paint. {@link ColonyVisibility} states what each
 * half does; this only says which one this pass resolved.
 *
 * <p>{@code isForcedOntoMap} sits beside it rather than inside it because it is not a question
 * about colonies at all: it admits a system regardless of reachability or inhabitation, so a rule
 * about what may be shown of a colony has nothing to say to it.
 *
 * <p>Neither is a question about any one layer's subject, which is why they are the framework's:
 * what a colony's discovery hides and what a system's reachability hides are the same for every
 * layer drawn over the sector map, and a second layer asking would otherwise have to reach into
 * the first one's vocabulary to ask it.
 *
 * <p>The pair travels as one value because the coordinators that seed and rebuild a layer need
 * both while the leaves that apply them need one each. Resolving it once at the entry point also
 * fixes it for the whole pass, so every system is admitted under the same state even if the
 * player moves a toggle mid-walk.
 *
 * <p>The components are named for what each does to the map, while the settings the read below
 * samples are named for what the player is asking to see. Same knobs, stated from the two ends
 * that care about them.
 *
 * @param colonyVisibility the rule this pass reads colonies under - the dev reveal that lifts the
 *                         fog outright, and the gates holding back the shapes a bare fog leaks
 * @param isForcedOntoMap  whether a star system is admitted to the map regardless of access or
 *                         inhabitation
 */
public record MapVisibilityOverrides(
    ColonyVisibility colonyVisibility,
    boolean isForcedOntoMap) {

    /**
     * The no-override view: the fog alone and nothing forced, so the map admits exactly what the
     * normal gates admit. The default a caller with no override to apply passes.
     */
    public static final MapVisibilityOverrides NONE =
        new MapVisibilityOverrides(ColonyVisibility.BASE_FOG, false);

    // Every gate the rule knows of, which is what the player's live read below applies until each
    // gate has a toggle of its own.
    //
    // Held rather than open because a gate narrows: showing a derelict now and taking it away
    // once the toggles ship would have the map contradict itself across a version, while holding
    // one back costs nothing but a colony the player can still reach and find.
    //
    // TODO: resolve each gate from its own player setting once the two spoiler toggles ship.
    private static final Set<RevelationGate> HELD_REVELATION_GATES =
        EnumSet.allOf(RevelationGate.class);

    /**
     * Reads an unset colony rule as the fog alone, so a pass built without one narrows nothing
     * and - far more importantly - reveals nothing the player has not found.
     */
    public MapVisibilityOverrides {
        colonyVisibility = colonyVisibility == null ? ColonyVisibility.BASE_FOG : colonyVisibility;
    }

    /**
     * Reads the player's current visibility state into one pass-wide value.
     *
     * <p>Called once per pass at the entry points, so every system in the pass is admitted under
     * the toggles in force when it began even if the player moves one mid-walk. Isolating the
     * settings read here keeps the rule and everything that threads this value free of settings
     * access.
     *
     * @return the visibility state the player's live settings describe
     */
    public static MapVisibilityOverrides readFromLunaSettings() {
        return new MapVisibilityOverrides(
            new ColonyVisibility(
                KmuMapLayerSettings.shouldShowUndiscoveredMarkets(),
                HELD_REVELATION_GATES),
            KmuMapLayerSettings.shouldShowHiddenSystems());
    }
}
