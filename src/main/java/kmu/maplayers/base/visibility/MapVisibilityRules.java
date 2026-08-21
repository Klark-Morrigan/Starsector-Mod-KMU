package kmu.maplayers.base.visibility;

import kmlib.starsector.colonies.ColonyVisibility;
import kmlib.starsector.colonies.RevelationGate;

import kmu.settings.KmuMapLayerSettings;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * The visibility rules in force for one map-layer pass: the rule the pass reads colonies under,
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
 * fixes it for the whole pass, so every system is admitted under the same rules even if the
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
public record MapVisibilityRules(
    ColonyVisibility colonyVisibility,
    boolean isForcedOntoMap) {

    /**
     * The rules with nothing widened and nothing forced: the fog alone, and a system on the map
     * only where the ordinary gates admit it. What a caller stating no rules of its own passes.
     *
     * <p>Not what the live read below returns - the shipped settings leave both revelation gates
     * in force - so a caller asserting what a player is actually shown wants that read rather
     * than this value.
     */
    public static final MapVisibilityRules BASE =
        new MapVisibilityRules(ColonyVisibility.BASE_FOG, false);

    /**
     * Rejects an unstated colony rule rather than standing one in.
     *
     * <p>Every value of this record is built either from the player's settings or from a rule the
     * caller already holds, so a null is a construction fault rather than a caller with nothing to
     * say. Standing in the fog would hide that fault behind a map that merely draws less than it
     * should, which is the hardest kind of wrong to notice. A caller genuinely stating no rule has
     * {@link #BASE} to pass, and the projections themselves read an absent rule as the fog - so
     * nothing is lost by refusing one here.
     */
    public MapVisibilityRules {
        Objects.requireNonNull(colonyVisibility, "colonyVisibility");
    }

    /**
     * Reads the player's current visibility settings into one pass-wide value.
     *
     * <p>Called once per pass at the entry points, so every system in the pass is admitted under
     * the toggles in force when it began even if the player moves one mid-walk. Isolating the
     * settings read here keeps the rule and everything that threads this value free of settings
     * access.
     *
     * @return the rules the player's live settings describe
     */
    public static MapVisibilityRules readFromLunaSettings() {
        return new MapVisibilityRules(
            new ColonyVisibility(
                KmuMapLayerSettings.shouldShowUndiscoveredMarkets(),
                resolveRevelationGates()),
            KmuMapLayerSettings.shouldShowHiddenSystems());
    }

    // The gates the player's spoiler settings leave in force. Each toggle is named for what
    // switching it on shows, so it is a toggle left off that carries its gate - which is also
    // why the shipped state holds both: a gate narrows, and a map that spoiled a sector
    // before the player asked it to could not take that back.
    //
    // Read as two independent questions rather than one three-way choice, because the two shapes
    // leak for different reasons - one is a place nobody ever lived on, the other a place hiding
    // itself - and a player minding one need not mind the other.
    //
    // The abandoned-station toggle covers the unowned wreck alone, which is why the gate behind
    // it is a kind rather than vanilla's condition: a station a faction keeps wears that same
    // condition and is a place somebody is, so it is not a shape the fog leaks and nothing here
    // holds it back.
    //
    // Copied into an immutable set here rather than handed over as the EnumSet: the rule takes
    // its own defensive copy of whatever it is given, and that copy is free for a set already
    // known immutable, which matters because this read runs per hover box and not only per pass.
    private static Set<RevelationGate> resolveRevelationGates() {

        var gatesInForce = EnumSet.noneOf(RevelationGate.class);

        if (!KmuMapLayerSettings.shouldShowUnseenAbandonedStations()) {
            gatesInForce.add(RevelationGate.SPACE_DERELICTS);
        }
        if (!KmuMapLayerSettings.shouldShowUnseenHiddenMarkets()) {
            gatesInForce.add(RevelationGate.HIDDEN_COLONIES);
        }
        return Set.copyOf(gatesInForce);
    }
}
