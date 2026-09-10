package kmu.maplayers.base.visibility.structures;

/**
 * What has been observed of each structure - who was holding it, what state it was in, and when
 * each of those was last established - asked by the structure's own entity id.
 *
 * <p>Says nothing about who did the observing. The player standing in a system, a colony living in
 * one, and a relay heard over its own network are all observations, and one register holds them
 * alike - which is what keeps a structure known once whatever reported it is gone.
 *
 * <p>Says nothing either about what may be shown right now. The record is one half of that; what a
 * route is revealing at this moment is the other, and the two are weighed where a box is composed
 * rather than here.
 *
 * <p>Stated as a port rather than as a value because what answers it is save state, while the rule
 * read over it is not. A caller holding no register of its own reads {@link #NONE}, under which
 * nothing has ever been observed - the conservative answer, since a register that invented
 * observations would name holders nobody has ever established.
 */
@FunctionalInterface
public interface StructureObservations {

    /**
     * Nothing has ever been observed of any structure. What an unstated register reads as, since
     * an absent record of what was observed is not a reason to suppose anything was.
     */
    StructureObservations NONE = structureId -> null;

    /**
     * What was last observed of one structure.
     *
     * @param structureId the structure entity's own id, as {@code SectorEntityToken#getId} reports
     *                    it; an id the register has never held reads as never observed
     * @return what it was last observed to be, or null where nobody has ever found it
     */
    StructureObservation readObservation(String structureId);
}
