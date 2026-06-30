package kmu.starsector.rat;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;

import assortment_of_things.abyss.entities.hyper.AbyssalFracture;

/**
 * Recognises Random Assortment of Things' Abyssal Fracture so KMU can treat it
 * as a means of access even though it is not a jump point. RAT routes fleets
 * through the fracture with a manual hyperspace transition on its custom entity
 * plugin, bypassing the vanilla jump-point system, so connectivity checks that
 * inspect only jump points never see it.
 *
 * <p>RAT is an optional dependency. The match is gated behind a mod-enabled
 * check, and the sole reference to the RAT type lives in the nested
 * {@link RatTypes} holder, which the classloader does not resolve until the
 * gate has passed. That keeps an install without RAT from ever loading
 * {@link AbyssalFracture}, and so from failing with a missing-class error.
 */
public final class RandomAssortmentOfThingsMatcher {

    private static final String RAT_MOD_ID = "assortment_of_things";

    private RandomAssortmentOfThingsMatcher() {
    }

    /**
     * @param entity the entity to test; null yields false
     * @return true when RAT is enabled and the entity is an Abyssal Fracture
     */
    public static boolean isAbyssalFracture(SectorEntityToken entity) {
        // Short-circuit before touching RatTypes so a RAT-free install never
        // loads the class that names AbyssalFracture.
        if (entity == null
                || !Global.getSettings().getModManager().isModEnabled(RAT_MOD_ID)) {
            return false;
        }
        return RatTypes.isAbyssalFracture(entity);
    }

    // Isolates the only reference to a RAT type. The classloader resolves this
    // holder on first call, which the gate in isAbyssalFracture defers until
    // RAT is known to be present, so AbyssalFracture is never sought otherwise.
    private static final class RatTypes {

        private static boolean isAbyssalFracture(SectorEntityToken entity) {
            return entity.getCustomPlugin() instanceof AbyssalFracture;
        }
    }
}
