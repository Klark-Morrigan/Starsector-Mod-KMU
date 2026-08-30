package kmu.starsector.nexerelin;

import kmlib.mods.nexerelin.NexerelinPresence;

import kmu.maplayers.base.visibility.colonies.FactionAlliances;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;

/**
 * The soft-dependency gate for the live alliance set: it answers whether Nexerelin is
 * present and, when it is, folds that set into whichever shape a reader asks for - an
 * {@link HolderGrouping} for the political map, or {@link FactionAlliances} for the
 * rule that decides who would keep a partner's secret. The sole reference to the Nex-coupled
 * {@link NexAllianceSource} lives in the nested {@link Holder}, which the classloader
 * does not resolve until the mod-enabled gate has passed, so a Nex-free install never
 * seeks an {@code exerelin.*} class - the same isolation
 * {@code NexerelinInvasionListenerInstaller} uses. Reflection is banned in KMU, so
 * this deferred-reference holder is the mechanism.
 */
public final class NexerelinAlliances {

    // The fingerprint reported when Nexerelin is absent: a fixed value the watcher polls
    // steadily, so a Nex-free install never sees a change and never bumps the alliance
    // revision. Any constant works; it is only ever compared for equality against itself.
    private static final int NO_NEXERELIN_FINGERPRINT = 0;

    private NexerelinAlliances() {
    }

    /**
     * Whether Nexerelin is enabled, and so whether the alliances view can be offered
     * at all. Stable for a session (mod set does not change in play), so a caller may
     * compute it once.
     *
     * <p>Asked through the library's own gate rather than through a mod id and a hop of
     * this class's own: the id is the mod's, not KMU's, and the gate answers "not present"
     * before the game has stood its settings up instead of throwing there.
     *
     * @return true when Nexerelin is present
     */
    public static boolean isAvailable() {
        return NexerelinPresence.isModEnabled();
    }

    /**
     * The grouping for the live alliance set, or {@link HolderGrouping#identity()}
     * when Nexerelin is absent.
     *
     * @return the alliance grouping when Nex is present, else the faction grouping
     */
    public static HolderGrouping resolveGrouping() {
        // Gated before Holder is named, so a Nex-free install never seeks exerelin.*.
        if (!isAvailable()) {
            return HolderGrouping.identity();
        }
        return Holder.resolveGrouping();
    }

    /**
     * The alliance memberships for the live alliance set, or {@link FactionAlliances#NONE}
     * when Nexerelin is absent.
     *
     * <p>Read afresh wherever a pass opens rather than snapshotted once: alliances form and
     * dissolve in play, and a visibility rule reading a stale set goes on crediting a
     * partnership that ended cycles ago.
     *
     * @return the memberships when Nex is present, else nobody standing with anybody
     */
    public static FactionAlliances resolveFactionAlliances() {
        // Gated before Holder is named, so a Nex-free install never seeks exerelin.*.
        if (!isAvailable()) {
            return FactionAlliances.NONE;
        }
        return Holder.resolveFactionAlliances();
    }

    /**
     * A membership token for the live alliance set, used by the sector watcher to detect
     * when alliances form, dissolve, or change members between polls. Returns a fixed
     * value when Nexerelin is absent, so a Nex-free install polls a steady token and never
     * bumps the alliance revision.
     *
     * @return the alliance membership fingerprint when Nex is present, else a fixed value
     */
    public static int computeAllianceFingerprint() {
        // Gated before Holder is named, so a Nex-free install never seeks exerelin.*.
        if (!isAvailable()) {
            return NO_NEXERELIN_FINGERPRINT;
        }
        return Holder.computeFingerprint();
    }

    // Isolates the only reference to the Nex-coupled source. The classloader resolves
    // this holder on first call, which the gate at the head of every read above defers
    // until Nex is known present, so NexAllianceSource - and through it exerelin.* - is
    // never sought otherwise.
    private static final class Holder {

        private static final AllianceSource SOURCE = new NexAllianceSource();

        private static HolderGrouping resolveGrouping() {
            return AllianceGroupingFactory.buildFrom(SOURCE.readAlliances());
        }

        private static FactionAlliances resolveFactionAlliances() {
            return FactionAllianceFactory.buildFrom(SOURCE.readAlliances());
        }

        private static int computeFingerprint() {
            return AllianceFingerprint.compute(SOURCE.readAlliances());
        }
    }
}
