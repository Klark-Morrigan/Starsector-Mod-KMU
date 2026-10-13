package kmu.maplayers.base.hover.cover;

import java.util.function.BooleanSupplier;

/**
 * A cover that is nothing but somebody else's flag: whatever it stands for is up, or it is not, and
 * where the cursor rests does not come into it.
 *
 * <p>Two of the covers are this shape, and stating it once is what keeps them two <b>reasons</b>
 * rather than two copies of one delegation. What differs is the reading each binds and why that
 * reading is its own - a menu raised over the campaign, or anything that claims the screen from the
 * sidebar as well - and each subclass is the home for that. What does not differ is the answer
 * being the flag, so the flag is held here.
 *
 * <p>Bound at construction rather than read through an abstract method, so a subclass is a name, a
 * body of reasoning and one constructor. A method to override would put the delegation back in every
 * subclass, which is the duplication this removes.
 *
 * <p>Fails open only as far as its reading does. A flag that cannot be established has to answer
 * {@code false} somewhere, and that somewhere is the reading itself, per the role's rule - so this
 * holds no guard of its own and each subclass documents where its own answer comes from.
 */
abstract class FlagMapCover implements MapCover {

    private final BooleanSupplier isCoverStanding;

    /**
     * @param isCoverStanding whether whatever this cover stands for is up
     */
    FlagMapCover(BooleanSupplier isCoverStanding) {
        this.isCoverStanding = isCoverStanding;
    }

    @Override
    public final boolean isCoveringCursor() {
        return isCoverStanding.getAsBoolean();
    }
}
