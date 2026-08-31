package kmu.starsector.listeners;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.campaign.SectorAPI;

import java.util.function.Supplier;

/**
 * One transient script an installer has put on a sector, held so it can be taken off again by
 * instance.
 *
 * <p>By instance rather than by class, which is the whole reason this exists.
 * {@code removeTransientScriptsOfClass} is the obvious call and the wrong one wherever the script
 * is a library class: another mod running its own of that class over the same sector would have it
 * taken out too, and a script that hands something back as it goes - an opacity, a widget's own
 * state - would hand it back on a widget this mod never touched. Holding the instance is what keeps
 * a removal to what this installer actually put there.
 *
 * <p>A slot belongs to whoever owns the sector its script was installed on, and holding one for the
 * whole process is only safe while there is one such sector. Removing against a sector other than
 * the one the held script went onto takes nothing off either of them: the sector asked does not
 * carry that script, and the sector that does goes on running it. So a second sector installed
 * through a shared slot silently strands the first sector's script.
 *
 * <p>Not for a script this mod owns outright. Those are safely removed by class, no sibling mod
 * having one, and a slot for them would be ceremony over a one-line call.
 *
 * @param <T> the script's own type, so a caller keeps whatever it built rather than an
 *            {@code EveryFrameScript} it would have to cast back
 */
public final class InstalledTransientScript<T extends EveryFrameScript> {

    private T installedScript;

    /**
     * Builds a script, adds it to the sector as a transient one, and holds it for removal.
     *
     * <p>Replaces whatever was held without removing it: what was held belongs to a sector this one
     * is not, or to a registration this call is redoing, and neither is this sector's to take off.
     *
     * @param sector      the sector to add to; null installs nothing and holds nothing
     * @param buildScript called only when there is a sector to install on, so a load that cannot
     *                    install does not construct a script it would discard
     */
    public void installOn(SectorAPI sector, Supplier<T> buildScript) {

        if (sector == null) {
            return;
        }
        installedScript = buildScript.get();

        sector.addTransientScript(installedScript);
    }

    /**
     * Takes the held script off the sector, if there is one.
     *
     * <p>Cleared after, so a second removal cannot reach for a script belonging to a sector this
     * one has since left.
     *
     * @param sector the sector to remove from; null leaves the sector untouched and the script held
     */
    public void removeFrom(SectorAPI sector) {

        if (sector == null || installedScript == null) {
            return;
        }
        sector.removeTransientScript(installedScript);

        installedScript = null;
    }
}
