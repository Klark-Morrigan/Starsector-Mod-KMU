package kmu.maplayers.base.geometry.v3;

import kmu.maplayers.base.geometry.VoidKeys;

import java.util.List;

/**
 * What a section of void is called: {@link VoidKeys}' key under the prefix of the section's
 * kind.
 *
 * <p>The prefix marks the key as a region rather than a star, and says which kind of region. A
 * section is keyed into the same map as the cells, so the one thing its name must never do is
 * collide with a system's - and it is shaped like one of vanilla's own generated IDs so that it
 * reads as a key rather than as a sentence.
 */
public final class VoidSectionIds {

    private static final String PUDDLE_PREFIX = "void_puddle";
    private static final String LAKE_PREFIX = "void_lake";
    private static final String LAKE_POCKET_PREFIX = "void_lakepocket";
    private static final String COASTAL_PREFIX = "void_coast";
    private static final String INLET_PREFIX = "void_inlet";
    private static final String INTERCONTINENTAL_PREFIX = "void_sea";

    private VoidSectionIds() {
    }

    /**
     * Names one section.
     *
     * @param section        the section
     * @param sites          the sites, to find which two of its cells sit closest together
     * @param systemIdBySite each site's system ID, index-aligned with {@code sites}
     * @return its key, in the system-id namespace
     */
    static String nameSection(
            VoidSection section,
            List<double[]> sites,
            List<String> systemIdBySite) {

        return VoidKeys.buildKey(
            readPrefix(section.kind()), section.cells(), section.outline(), sites, systemIdBySite);
    }

    private static String readPrefix(VoidSection.SectionKind kind) {

        return switch (kind) {
            case PUDDLE -> PUDDLE_PREFIX;
            case LAKE -> LAKE_PREFIX;
            case LAKE_POCKET -> LAKE_POCKET_PREFIX;
            case COASTAL -> COASTAL_PREFIX;
            case INLET -> INLET_PREFIX;
            case INTERCONTINENTAL -> INTERCONTINENTAL_PREFIX;
        };
    }
}
