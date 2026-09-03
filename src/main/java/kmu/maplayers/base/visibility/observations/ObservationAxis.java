package kmu.maplayers.base.visibility.observations;

import java.util.Objects;

/**
 * One concealed fact a row carries: how old the news of it is, and the words a date off it would be
 * introduced by.
 *
 * <p>A row states several concealed facts at once and only ever one date beside them, so the two
 * have to travel together to be weighed against each other. Kept apart, whichever fact turned out to
 * date the row would be stated in whatever wording happened to be nearest.
 *
 * <p>The lead-in arrives as a string id rather than as text, so no shared class holds a phrase it
 * chose itself. A fact somebody stood over and one read off a distant trace are not the same claim,
 * and which of the two a row is making is not something this package can know - the family that owns
 * the fact supplies the words, and {@link ObservationNotes} only picks which of them the row states.
 *
 * @param leadInKey the words introducing a date off this fact, as a string id taking the span and
 *                  the date in that order
 * @param recency   how old the news on this fact is
 */
public record ObservationAxis(
    String leadInKey,
    ObservationRecency recency) {

    /**
     * A half-built axis names itself where it is put together rather than where a row is drawn -
     * a remark is the last thing composed on a surface, and a fault carried that far reads as a
     * fault in the surface.
     */
    public ObservationAxis {

        Objects.requireNonNull(leadInKey, "An axis states its date in words of its own.");
        Objects.requireNonNull(recency, "An axis is in one of the three states.");
    }
}
