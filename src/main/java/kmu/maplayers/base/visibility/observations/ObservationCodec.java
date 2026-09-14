package kmu.maplayers.base.visibility.observations;

/**
 * How one family's observation is written into the register and read back out of it.
 *
 * <p>Stated as a port because {@link ObservationStore} moves bytes and lifecycle and never a field.
 * What one entry means belongs to whoever knows what is being observed - one family's names a place
 * and a moment, another's a faction and a moment - so the meaning travels with the family and the
 * register underneath stays the same one for all of them.
 *
 * <p><strong>Fixed fields first, the free-form one last.</strong> An entry is a single string, so
 * whichever field may hold anything - an ID the game composed, separators and punctuation and all -
 * has to be the one that runs to the end. Written first it takes the separator with it and every
 * field after it parts in the wrong place; written last it reads back verbatim however it is spelt.
 *
 * <p><strong>An entry that does not read reads as the weaker true thing.</strong> Decoding neither
 * throws nor reports corruption. An entry written before a field existed is an observation with one
 * half missing rather than a broken one, and the reading that drops the doubtful half is both the
 * honest one and the one healed at the next observation - whereas a decode that failed instead
 * would take a load down over a field nothing may even go on to ask about.
 *
 * @param <T> what one entry says was observed
 */
public interface ObservationCodec<T> {

    /**
     * Reads one stored entry back.
     *
     * @param storedObservation the entry as the register holds it; never null
     * @return what it says was observed, or null where nothing usable can be read off it at all
     */
    T decodeObservation(String storedObservation);

    /**
     * Writes one observation as the register stores it.
     *
     * @param observation what was observed
     * @return the entry, fixed fields first and the free-form one last; never null
     */
    String encodeObservation(T observation);
}
