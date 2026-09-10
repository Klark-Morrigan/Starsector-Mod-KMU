package kmu.maplayers.base.visibility.installations;

import java.util.Optional;

import static kmu.util.KmuValues.normalizeText;

/**
 * What kind of place a market-less installation is, as a map layer has to tell them apart: a hulk
 * nobody keeps, one a faction owns outright, or one a garrison holds for whoever posted it.
 *
 * <p>A classification drawn for the map rather than a shape the sector holds. Nothing in the game
 * answers it: a remnant station and an abandoned habitat are the same kind of entity, differing
 * only in who - if anyone - is there, and that difference is what decides whether a system reads as
 * held by somebody or as empty space with wreckage in it.
 *
 * <p>What separates the three is who a place counts for, which is why they are worth telling apart
 * at all. A {@link #DERELICT} counts for nobody and weighs nothing. A {@link #HELD} counts for the
 * faction that owns the entity. A {@link #GARRISONED} counts for whoever posted what is standing
 * there, which is frequently not the faction the entity itself names - a remnant picket sits on a
 * neutral-owned station, and reading the owner would file every one of them as a hulk.
 *
 * <p>Resolved from facts, and stated by a row of the override table where the facts are not enough:
 * research into what a modded entity type actually is arrives one type at a time, and has to be
 * able to land as a row rather than as a rule.
 *
 * <p>An enum rather than a flag, because a fourth kind is expected rather than merely possible -
 * remnant battlestations are fleets and reach no entity walk at all - and because the readers that
 * route on it should have to say what they do with a new one instead of being silently left out of
 * a comparison written before it existed.
 */
public enum InstallationKind {

    /**
     * A hulk nobody keeps: no garrison met, and no real faction owning it.
     *
     * <p>The default the classification errs towards, and deliberately the timid one. Understating
     * a place as unheld costs nought weight in a ranking; inventing a holder for an unresearched
     * entity type would hand somebody a system inside a number the player reads and cannot check.
     */
    DERELICT,

    /**
     * A place a real faction owns outright, with nothing posted at it - what a defensive station
     * is once the faction that built it has taken it over.
     */
    HELD,

    /**
     * A place whose garrison the player has met, held for the faction that garrison counts for
     * rather than for whoever the entity names as its owner.
     */
    GARRISONED;

    /**
     * Finds the kind a table row names.
     *
     * <p>Matched case-insensitively, because the file is hand-edited and a row is not worth losing
     * over the shift key. An unmatched name is absent rather than an error: a row written against a
     * later version of this mod, or simply mistyped, leaves the kind to the facts - which is what
     * the column's absence means anyway, and is the one fallback that cannot invent a holder.
     *
     * @param name the name a row states, blank or absent where the row states none
     * @return the kind of that name, or empty where nothing of that name is a kind
     */
    public static Optional<InstallationKind> findKindNamed(String name) {

        var stated = normalizeText(name);

        if (stated == null) {
            return Optional.empty();
        }
        for (var kind : values()) {

            if (kind.name().equalsIgnoreCase(stated)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
