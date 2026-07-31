package kmu.starsector.nexerelin;

import com.fs.starfarer.api.Global;

import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;

/**
 * The soft-dependency gate for the political map's alliance grouping: it answers
 * whether Nexerelin is present and, when it is, builds an {@link HolderGrouping}
 * from the live alliance set. The sole reference to the Nex-coupled
 * {@link NexAllianceSource} lives in the nested {@link Holder}, which the classloader
 * does not resolve until the mod-enabled gate has passed, so a Nex-free install never
 * seeks an {@code exerelin.*} class - the same isolation
 * {@code NexerelinInvasionListenerInstaller} uses. Reflection is banned in KMU, so
 * this deferred-reference holder is the mechanism.
 */
public final class NexerelinAlliances {

    private static final String NEXERELIN_MOD_ID = "nexerelin";

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
     * @return true when Nexerelin is present
     */
    public static boolean isAvailable() {
        return Global.getSettings().getModManager().isModEnabled(NEXERELIN_MOD_ID);
    }

    /**
     * The grouping for the live alliance set, or {@link HolderGrouping#identity()}
     * when Nexerelin is absent. Short-circuits on the gate before touching
     * {@link Holder}, so the Nex-coupled source is never loaded without Nex.
     *
     * @return the alliance grouping when Nex is present, else the faction grouping
     */
    public static HolderGrouping resolveGrouping() {
        // Short-circuit before referencing Holder so a Nex-free install never loads the
        // class that names NexAllianceSource, which imports exerelin.*.
        if (!isAvailable()) {
            return HolderGrouping.identity();
        }
        return Holder.resolveGrouping();
    }

    /**
     * A membership token for the live alliance set, used by the sector watcher to detect
     * when alliances form, dissolve, or change members between polls. Returns a fixed
     * value when Nexerelin is absent, so a Nex-free install polls a steady token and never
     * bumps the alliance revision. Short-circuits on the gate before touching
     * {@link Holder}, so the Nex-coupled source is never loaded without Nex.
     *
     * @return the alliance membership fingerprint when Nex is present, else a fixed value
     */
    public static int computeAllianceFingerprint() {
        // Short-circuit before referencing Holder so a Nex-free install never loads the
        // class that names NexAllianceSource, which imports exerelin.*.
        if (!isAvailable()) {
            return NO_NEXERELIN_FINGERPRINT;
        }
        return Holder.computeFingerprint();
    }

    // Isolates the only reference to the Nex-coupled source. The classloader resolves
    // this holder on first call, which the gates in resolveGrouping and
    // computeAllianceFingerprint defer until Nex is known present, so NexAllianceSource -
    // and through it exerelin.* - is never sought otherwise.
    private static final class Holder {

        private static final AllianceSource SOURCE = new NexAllianceSource();

        private static HolderGrouping resolveGrouping() {
            return AllianceGroupingFactory.buildFrom(SOURCE.readAlliances());
        }

        private static int computeFingerprint() {
            return AllianceFingerprint.compute(SOURCE.readAlliances());
        }
    }
}
