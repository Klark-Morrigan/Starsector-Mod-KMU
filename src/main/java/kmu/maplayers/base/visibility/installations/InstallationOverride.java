package kmu.maplayers.base.visibility.installations;

import java.util.Optional;

/**
 * What the override table states about one entity type - the map disposition a row is allowed to
 * settle by hand, where what the sector says about the type is not enough to settle it.
 *
 * <p>Every column optional, and each absent on its own. Research into what a modded station really
 * is arrives one type at a time and one question at a time: a row may know that a type belongs on
 * the map long before anybody has worked out what it counts as. A row forced to answer both would
 * have to invent whichever half it did not know, and an invented kind is exactly the silent holder
 * this family refuses to produce.
 *
 * <p>Says nothing about how either answer is reached when the row is silent. Both defaults are the
 * facts' to state, and repeating them here would be a second answer free to disagree with the one
 * the classification reaches.
 *
 * @param isAdmitted whether the map lists this type at all; absent leaves admission to the facts
 * @param kind       what every installation of this type counts as; absent leaves the kind to the
 *                   facts
 */
public record InstallationOverride(
    Optional<Boolean> isAdmitted,
    Optional<InstallationKind> kind) {

    /** Nothing stated about a type - what an entity type the file never mentions reads as. */
    public static final InstallationOverride NONE =
        new InstallationOverride(Optional.empty(), Optional.empty());

    /**
     * Reads an absent column as one the row states nothing about, so a caller building a row from
     * a half-read file cannot produce an override that throws when it is read back.
     */
    public InstallationOverride {
        isAdmitted = isAdmitted == null ? Optional.empty() : isAdmitted;
        kind = kind == null ? Optional.empty() : kind;
    }
}
