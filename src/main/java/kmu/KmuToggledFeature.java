package kmu;

import com.fs.starfarer.api.campaign.SectorAPI;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * One feature a player switch stands up or takes back, and what it was last applied as.
 *
 * <p>A feature answering a switch has to answer it twice: once against a freshly loaded sector,
 * which carries none of the previous one's wiring, and again wherever the player flips it, which is
 * not a load at all. The two differ only in whether an unchanged switch is worth acting on, and
 * that is the whole of what this holds - the state a settings change is compared against.
 *
 * <p>The comparison is the reason this exists rather than an {@code if} at each call site. LunaLib
 * announces that the settings changed rather than which setting did, so a feature acting on every
 * announcement would be torn down and rebuilt whenever the player moved an unrelated slider. What
 * that costs is a feature's own business - cached text dropped, a claim re-armed, a widget handed
 * back - and none of it is visible from the settings screen where it would be provoked.
 *
 * <p>Holds no sector. The sector is handed in per call, because the one this is applied to changes
 * with every load while the switch and the two halves do not.
 */
public final class KmuToggledFeature {

    private final BooleanSupplier isSwitchedOn;
    private final Consumer<SectorAPI> standUpFeature;
    private final Consumer<SectorAPI> takeFeatureBack;

    // What the switch read as when this was last applied, or null before it ever has been. Null
    // reads as "not what the switch says", so the first ask always applies - the safe direction for
    // a state nothing has established yet.
    private Boolean appliedState;

    /**
     * @param isSwitchedOn    the player's switch, read afresh at every ask
     * @param standUpFeature  what the feature needs of a sector while it is on
     * @param takeFeatureBack what has to be undone for it to be off, which within one session is
     *                        everything the stand-up registered rather than merely declining to
     *                        register it again
     */
    public KmuToggledFeature(
            BooleanSupplier isSwitchedOn,
            Consumer<SectorAPI> standUpFeature,
            Consumer<SectorAPI> takeFeatureBack) {

        this.isSwitchedOn = isSwitchedOn;
        this.standUpFeature = standUpFeature;
        this.takeFeatureBack = takeFeatureBack;
    }

    /**
     * Brings {@code sector} into line with the switch, whatever was applied before.
     *
     * <p>For a load, where the sector is new and carries nothing of what was applied to the last
     * one, so there is no previous state worth comparing against.
     *
     * @param sector the loaded sector
     */
    public void applyTo(SectorAPI sector) {
        applyStateTo(sector, isSwitchedOn.getAsBoolean());
    }

    /**
     * The same, but only when the switch has moved since it was last applied.
     *
     * <p>For a settings change, where the sector is the one already wired and most announcements
     * are about some other setting entirely.
     *
     * @param sector the sector already wired, which may be null when the settings are changed with
     *               no game loaded
     */
    public void applyToIfSwitched(SectorAPI sector) {

        var isWanted = isSwitchedOn.getAsBoolean();

        if (appliedState != null && appliedState == isWanted) {
            return;
        }
        applyStateTo(sector, isWanted);
    }

    private void applyStateTo(SectorAPI sector, boolean isWanted) {

        appliedState = isWanted;

        if (isWanted) {
            standUpFeature.accept(sector);
        } else {
            takeFeatureBack.accept(sector);
        }
    }
}
