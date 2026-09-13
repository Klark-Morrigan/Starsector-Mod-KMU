package kmu.maplayers.base.geometry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Every piece of void in a sector, named.
 *
 * <p>The one walk from a laid coast to the sections it leaves: trace the holes with the walls
 * down, take each as a section, and name it. Shared rather than written where it is wanted,
 * because it is wanted in two places that must not come to differ - the map draws these names
 * and the report checks them, and two walks would let the picture and the check describe
 * different sets of void while both looking right.
 *
 * <p>Both kinds together and in one list. Which of them a caller draws, or whether it draws any,
 * is the caller's business; what there IS does not change with a toggle.
 */
public final class VoidSections {

    private VoidSections() {
    }

    /**
     * One section with the name it is known by.
     *
     * <p>Paired because neither is much use alone: the section carries the geometry and the
     * cells, the named region carries the key and the point that key is written at, and every
     * reader of one wants the other.
     *
     * @param section the section
     * @param region  it as a named piece of map
     */
    public record NamedSection(
        VoidSection section,
        NamedRegion region) {

        /** @return what the section is called, which is the region's name */
        String id() {
            return region.name();
        }
    }

    /**
     * Names every piece of void the walls and the cells shut in.
     *
     * @param laid           the coast with its walls down, which carries the sites and the
     *                       knobs everything here is measured against
     * @param systemIdBySite each site's system ID, index-aligned with the coast's own sites
     * @return every section, in the order the boundary walk found them
     */
    public static List<NamedSection> collectNamedSections(
            LaidCoast laid,
            List<String> systemIdBySite) {

        var sites = laid.sites();
        var named = new ArrayList<NamedSection>();

        // The trace's own verdict on which unwalled holes are puddles, by the ring of cells
        // around each, so a section is not measured against a second floor.
        var puddleRings = new HashSet<Set<Integer>>();

        for (var puddle : laid.traced().puddles()) {
            puddleRings.add(Set.copyOf(puddle.ringCells()));
        }

        for (var hole : DiscUnionBoundary.traceHolesAcrossWalls(
                laid.atCells(), laid.walls(), laid.parameters().boundSegments())) {

            var section = VoidSection.buildFromHole(hole, puddleRings);

            named.add(new NamedSection(
                section,
                NamedRegion.nameRegion(
                    VoidSectionIds.nameSection(section, sites, systemIdBySite),
                    section.outline())));
        }
        return List.copyOf(named);
    }
}
