package kmu.maplayers.base.visibility.structures;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.visibility.observations.ObservationStore;

/**
 * The structure register kept in the sector's own memory: which key it is held under, how one
 * entry is spelt, and reading it back.
 *
 * <p>The bytes and the load lifecycle underneath are an {@link ObservationStore}'s, which every
 * map family that keeps observations shares. What one entry says is {@link StructureObservation}'s
 * and how it is spelt is {@link StructureObservationCodec}'s; a register of this family's own is
 * what keeps both from having to fit beside a colony's.
 *
 * <p>Final class with a private constructor: pure-function utility, no instance state, and
 * null-defensive like the rest of the map framework.
 */
public final class SectorStructureObservations {

    // The save-serialised identity of the register. Stable once shipped: renaming it silently
    // drops every observation in every existing save, and nothing in a loaded game can tell that
    // from a player who has found nothing.
    private static final String OBSERVATIONS_KEY = "$kmu_structure_observations";

    // The register itself. Held once rather than opened per call: it carries the key and the codec
    // and nothing of any one sector, so a store per sector would only be the same two fields again.
    private static final ObservationStore<StructureObservation> OBSERVATIONS_REGISTER =
        new ObservationStore<>(OBSERVATIONS_KEY, new StructureObservationCodec());

    private SectorStructureObservations() {
        // utility class, no instances.
    }

    /**
     * Opens the sector's structure register for reading.
     *
     * @param sector the sector whose memory holds the register; null - or one holding no register
     *               yet - reads as nothing having been observed of anything
     * @return what was last observed of each structure, by entity ID; never null
     */
    public static StructureObservations readObservations(SectorAPI sector) {

        return OBSERVATIONS_REGISTER
            .readObservations(sector)::readObservationOrNull;
    }
}
