package kmu.maplayers.ownermap.holding;

import java.util.List;
import java.util.function.Function;

/**
 * The two sides of one system's contest as a {@link BlocAffiliation} places them: everyone present
 * who stands with the bloc holding it, and everyone standing against it.
 *
 * <p>The same question every surface reporting a contest has to answer - a box routing factions
 * under its headings, another routing ranked groups under its own - asked once here so two surfaces
 * over one system cannot put a bloc on different sides of it. What is being placed and how the
 * holder was found stay each surface's own: the contestants arrive as a list and name their bloc
 * through a reader, the holder having already been dropped from it.
 *
 * <p>The holder travels beside the affiliation rather than arriving per call, because both sides
 * of one contest are read against the one holder and passed apart they could be read against two.
 *
 * @param holderBlocId the bloc holding the system, as the layer's mechanic found it; no bloc at all
 *                     where nobody holds it, which leaves every contestant on the rival side, there
 *                     being nobody for them to be standing with
 * @param affiliation  who stands with whom, the sides being placed under it; it is
 *                     {@link BlocAffiliation#NONE} wherever nothing groups factions - every
 *                     contestant a rival, exactly as a contest read without groups at all
 */
public record ContestSides(
    String holderBlocId,
    BlocAffiliation affiliation) {

    /**
     * The contestants on one side of the contest, in the order they arrived.
     *
     * <p>Both sides are asked for the same way, since which one a caller wants is the only thing
     * that differs between them. The affiliation decides where a contestant lands:
     * {@link ContestSide#ALLIED} comes out empty wherever nothing groups factions or nobody holds
     * the system, which is the same reading that puts every contestant under
     * {@link ContestSide#RIVAL} - a contest read exactly as one without groups at all.
     *
     * @param side           the side wanted
     * @param contestants    everyone present but the holder itself
     * @param resolveBlocId  what names a contestant's bloc
     * @param <T>            whatever a surface lists a contestant as
     * @return the contestants that side takes
     */
    public <T> List<T> selectSide(
            ContestSide side,
            List<T> contestants,
            Function<T, String> resolveBlocId) {

        return contestants
            .stream()
            .filter(contestant ->
                side.isTakingContestant(isStandingWithHolder(contestant, resolveBlocId)))
            .toList();
    }

    // Whether one contestant stands with the holder. Asked through the affiliation rather than by
    // comparing blocs here, so every surface places a contestant by the rule a band lays its runs
    // at contested length by - two answers to one question being the disagreement between a box and
    // the band beneath it that the whole axis exists to rule out.
    private <T> boolean isStandingWithHolder(T contestant, Function<T, String> resolveBlocId) {
        return affiliation.areBlocsAllied(holderBlocId, resolveBlocId.apply(contestant));
    }
}
