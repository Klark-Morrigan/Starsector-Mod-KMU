package kmu.maplayers.base.visibility.structures;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.visibility.observations.ObservationStore;

/**
 * The structure register kept in the sector's own memory: which key it is held under, how one
 * entry is spelt, and reading it back.
 *
 * <p>A register of its own rather than a payload on the colony one, because the two record
 * different things. A sighting says a colony was seen standing somewhere; an entry here says what
 * a structure looked like, which is the only way a holder can be stated without either outing
 * every faction the player has never met or being wrong about who holds it now.
 *
 * <p>The bytes and the load lifecycle underneath are an {@link ObservationStore}'s, which every
 * map family that keeps observations shares. What one entry means stays here, in
 * {@link StructureObservationCodec}.
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
     * @return what was last observed of each structure, by entity id; never null
     */
    public static StructureObservations readObservations(SectorAPI sector) {

        var recordedObservations = OBSERVATIONS_REGISTER.readObservations(sector);

        // The register answers an absent observation with an empty optional; this port answers it
        // with null, so the adaptation is here rather than in every rule reading one.
        return structureId -> recordedObservations
            .readObservation(structureId)
            .orElse(null);
    }
}
