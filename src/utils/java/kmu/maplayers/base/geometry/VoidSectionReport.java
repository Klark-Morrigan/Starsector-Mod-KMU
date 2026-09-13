package kmu.maplayers.base.geometry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * What the void comes out as once it is named and owned.
 *
 * <p>Asked of the whole population at once - every hole at the cells' own reach with the walls
 * laid, whatever closed it - because a name has to be unique across all of them and not merely
 * within the layer that produced it. Each fill layer reports on its own pockets, which cannot
 * answer that.
 *
 * <p>Taken at the cells' own reach and nowhere else. A section is going to be handed to the
 * cluster machinery as a cell, and a cell's edges sit on its own border; a reading taken a
 * channel out would name and own a piece of void the map does not have.
 *
 * <p>Run with the fixture's real owners rather than with every site unowned, since who holds a
 * section is the whole question. The unowned baseline stays where it is, on the shapes.
 */
public final class VoidSectionReport {

    // The middle, the far end, and the worst - which is what a population of shapes wants
    // read out of it. A mean would hide the one section that is nothing like the rest, and
    // the rest is what the median already says.
    private static final double[] REPORTED_PERCENTILES = {0.5, 0.9, 1.0};

    // Enough of the field to see whether a section was taken outright or scraped. Beyond the
    // first few the tail says nothing a reader acts on.
    private static final int REPORTED_CLAIMS = 4;

    // A section running on one cell has no pair to take a side of, so its name cannot be told
    // apart from that of another piece of void around the same cell.
    private static final int LONE_CELL = 1;

    private VoidSectionReport() {
    }

    /**
     * Reports every section of void the sector comes out as.
     *
     * @param fixture the sector, for the system IDs sections are named in and the owners they
     *                are claimed by
     * @param laid    the coast with its walls down, which is what shuts the void into sections
     */
    public static void reportSections(SectorFixture fixture, LaidCoast laid) {

        var systemIds = fixture.getSystemIds();
        var ownerBySite = fixture.getOwnerBySite();

        var named = new ArrayList<OwnedSection>();

        for (var section : VoidSections.collectNamedSections(laid, systemIds)) {

            named.add(new OwnedSection(
                section,
                VoidSectionOwners.resolveSectionOwner(section.section(), ownerBySite)));
        }
        reportNaming(named, systemIds);
        reportCellCounts(named);
        reportTiling(named);
        reportClaims(named);
        reportEachSection(named);
    }

    /**
     * One named section with the answer this step exists to produce about it.
     *
     * @param named the section and what it is called
     * @param owner who holds it, and who else had a claim
     */
    private record OwnedSection(
        VoidSections.NamedSection named,
        VoidSectionOwners.SectionOwner owner) {

        // Reached through rather than around, so a reader of one of these asks it for what it
        // is rather than remembering which half of it holds what.
        String id() {
            return named.id();
        }

        NamedRegion region() {
            return named.region();
        }

        VoidSection section() {
            return named.section();
        }
    }

    // Whether the sections are separate pieces of map or two names for one piece.
    //
    // They are holes in one union, so they cannot overlap - which is exactly why it is worth
    // asking: an overlap here would mean the boundary walk had produced two rings over the same
    // piece of map, and every measure taken per section would then be double-counting it.
    //
    // Asked of each section's own anchor, which sits inside it at its widest rather than near
    // an edge, so a hit is a genuinely shared middle rather than two rings agreeing about a
    // point on the boundary between them.
    private static void reportTiling(List<OwnedSection> named) {

        var overlapping = 0;

        for (var section : named) {
            for (var other : named) {

                if (other.id().equals(section.id())) {
                    continue;
                }

                var anchor = section.region().anchor();

                if (other.region().holds(anchor[0], anchor[1])) {

                    overlapping++;
                    System.out.printf(
                        Locale.ROOT,
                        "  overlap: %s has its middle inside %s%n",
                        section.id(),
                        other.id());
                    break;
                }
            }
        }
        System.out.printf(
            Locale.ROOT,
            "%d sections have their middle inside another (has to be 0)%n",
            overlapping);
    }

    // Whether the naming scheme actually names. Distinct IDs short of the section count is the
    // one failure it can have, and it is a failure of the SCHEME rather than of a section - so
    // the offending names are printed, since a count cannot say which rule fell short.
    private static void reportNaming(
            List<OwnedSection> named,
            List<String> systemIds) {

        var distinct = new TreeSet<String>();
        var collided = new TreeSet<String>();

        // A section is keyed into the same map as the cells, so a key equal to a star's is
        // worse than two sections sharing one: it would silently replace a system.
        var stars = new TreeSet<>(systemIds);
        var unsafe = new TreeSet<String>();

        for (var section : named) {

            if (!distinct.add(section.id())) {
                collided.add(section.id());
            }
            if (stars.contains(section.id()) || !isKeyShaped(section.id())) {
                unsafe.add(section.id());
            }
        }

        System.out.printf(
            Locale.ROOT,
            "sections of void at the cells' own reach: %d, %d distinct ids (has to match), "
                + "%d names collide, %d are unsafe as keys (has to be 0)%n",
            named.size(),
            distinct.size(),
            collided.size(),
            unsafe.size());

        for (var id : unsafe) {
            System.out.printf(Locale.ROOT, "  unsafe key %s%n", id);
        }

        for (var id : collided) {
            System.out.printf(Locale.ROOT, "  collides %s%n", id);
        }
    }

    // How many cells a section has around it, which is what it is named and owned by - so a
    // population sitting at one would mean the names and the owners both rest on a single
    // neighbour.
    private static void reportCellCounts(List<OwnedSection> named) {

        var counts = new ArrayList<Double>(named.size());
        var loneCell = 0;

        for (var section : named) {

            counts.add((double) section.section().cells().size());

            if (section.section().cells().size() == LONE_CELL) {
                loneCell++;
            }
        }
        counts.sort(Double::compare);

        System.out.printf(
            Locale.ROOT,
            "cells a section runs on: p50 %.0f / p90 %.0f / max %.0f; %d run on one cell "
                + "and cannot take a side%n",
            ReportFigures.findPercentile(counts, REPORTED_PERCENTILES[0]),
            ReportFigures.findPercentile(counts, REPORTED_PERCENTILES[1]),
            ReportFigures.findPercentile(counts, REPORTED_PERCENTILES[2]),
            loneCell);
    }

    // Whether a key is made of what a generated system ID is made of. The scheme is meant to
    // guarantee this; asking anyway is what turns the guarantee into something the report
    // would notice breaking.
    private static boolean isKeyShaped(String id) {
        return id.matches("[a-z0-9_-]+");
    }

    // How the sections came out owned, split by which way each answer was arrived at. Owned
    // and ownerless alone cannot be read: a section the unowned cells took outright and one
    // two owners drew on both report ownerless, and only the second is a rule failing to
    // decide.
    private static void reportClaims(List<OwnedSection> named) {

        var held = 0;
        var takenByUnowned = 0;
        var tied = 0;
        var contested = 0;

        var sectionsByOwner = new LinkedHashMap<String, Integer>();

        for (var section : named) {

            var owner = section.owner();

            if (owner.owner() != null) {
                held++;
            } else if (owner.claims().get(0).owner() == null) {
                takenByUnowned++;
            } else {
                tied++;
            }
            if (owner.isContested()) {
                contested++;
            }
            sectionsByOwner.merge(owner.owner(), 1, Integer::sum);
        }

        System.out.printf(
            Locale.ROOT,
            "%d sections are held by an owner, %d taken by the unowned cells around them, "
                + "%d left ownerless by a tie; %d had more than one claimant%n",
            held,
            takenByUnowned,
            tied,
            contested);

        var holders = new ArrayList<VoidSectionOwners.OwnerClaim>(sectionsByOwner.size());

        for (var entry : sectionsByOwner.entrySet()) {
            holders.add(new VoidSectionOwners.OwnerClaim(entry.getKey(), entry.getValue()));
        }
        holders.sort(Comparator
            .comparingInt(VoidSectionOwners.OwnerClaim::cells).reversed()
            .thenComparing(
                VoidSectionOwners.OwnerClaim::owner,
                Comparator.nullsLast(Comparator.naturalOrder())));

        System.out.printf(
            Locale.ROOT,
            "sections per owner, most first: %s%n",
            describeClaims(holders, holders.size()));
    }

    // Every section, one line each. The distribution above says how the rule behaved across
    // the map; only a line per section says which piece of void ended up where, and every
    // question asked of this map so far has turned out to be about a particular one.
    private static void reportEachSection(List<OwnedSection> named) {

        System.out.println("  section                                    owner        claims");

        for (var section : named) {

            System.out.printf(
                Locale.ROOT,
                "  %-42s %-12s %s%n",
                section.id(),
                describeOwner(section.owner()),
                describeClaims(section.owner().claims(), REPORTED_CLAIMS));
        }
    }

    private static String describeOwner(VoidSectionOwners.SectionOwner owner) {

        if (owner.owner() != null) {
            return owner.owner();
        }
        return owner.claims().get(0).owner() == null ? "unowned" : "tied";
    }

    private static String describeClaims(
            List<VoidSectionOwners.OwnerClaim> claims,
            int atMost) {

        var listed = new ArrayList<String>(claims.size());

        for (var claim : claims.subList(0, Math.min(atMost, claims.size()))) {

            listed.add(String.format(
                Locale.ROOT,
                "%s %d",
                claim.owner() == null ? "(unowned)" : claim.owner(),
                claim.cells()));
        }

        if (claims.size() > atMost) {
            listed.add("+" + (claims.size() - atMost) + " more");
        }
        return String.join(" / ", listed);
    }

}
