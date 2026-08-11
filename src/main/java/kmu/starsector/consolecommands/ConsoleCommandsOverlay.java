package kmu.starsector.consolecommands;

import com.fs.starfarer.api.Global;

import org.apache.log4j.Logger;

/**
 * The {@link ConsoleOverlay} answer for Console Commands: a mod-presence gate in front of the
 * mod's own overlay read, with every failure turned into "no console is open".
 *
 * <p>Console Commands is compiled against but not declared a dependency, so an install without it
 * is ordinary and must cost nothing. The gate asks the mod manager first and only then touches
 * {@link ConsoleCommandsPanelPresence}, which is what defers resolving the one class that names an
 * {@code org.lazywizard.console} type until that type is known to exist. The enablement is read
 * once and held: the mod set is fixed for a run, and this is asked every frame.
 *
 * <p>Fail-open is the governing rule here rather than a footnote. The mod absent, the class
 * missing, the accessor moved by a Console Commands release, the read throwing anything at all -
 * each reports no console open and nothing else. Callers then behave as they did before this
 * question existed: drawn over the console and still holding its hotkeys, which is a survivable
 * annoyance a player can work around by closing the panel. Reporting open-on-failure, or letting
 * a throw escape, would instead take those callers away on every screen and every frame, for a
 * mod the player may not even have installed.
 *
 * <p>A failure also settles the whole read off for the session, naming in the log which hop broke.
 * Off, because the console cannot be read any more and a retry would throw again on the next
 * frame, so it is as good as not installed. Named, because that is what turns a Console Commands
 * release moving the accessor into a line in the log rather than a report about the console being
 * unusable. Once, because this is asked from render and input passes, and the hop that broke first
 * is the one that stopped the read - a second line would say nothing the first did not.
 */
public final class ConsoleCommandsOverlay implements ConsoleOverlay {

    private static final Logger LOG = Global.getLogger(ConsoleCommandsOverlay.class);

    private static final String CONSOLE_COMMANDS_MOD_ID = "lw_console";

    // What every failure here costs, said in the log's own terms. Appended to whichever hop broke,
    // so one line names both the cause and the consequence.
    private static final String FAIL_OPEN_CONSEQUENCE =
        " Anything that steps aside for an open console will no longer do so this session.";

    // Null until the first ask, then the settled answer: whether the console can be read at all.
    // The mod set cannot change within a run, so a successful read is held rather than repeated
    // every frame; a failure settles it to false for the reason given in the class notes.
    private Boolean isConsoleReadable;

    // Built on the first ask that gets past the gate, not at construction: building it is what
    // resolves the class that names Console Commands' overlay panel, and an install without the
    // mod must never reach that name.
    private ConsoleOverlayPresence presence;

    private boolean hasWarnedThisSession;

    /**
     * Reads the console state Console Commands itself publishes.
     */
    public ConsoleCommandsOverlay() {
    }

    /**
     * @param presence the console state to read once the mod gate has passed, in place of Console
     *                 Commands' own overlay panel
     */
    ConsoleCommandsOverlay(ConsoleOverlayPresence presence) {
        this.presence = presence;
    }

    @Override
    public boolean isOpen() {
        // Short-circuit before touching the presence, so an install without Console Commands never
        // resolves the class that names its overlay panel - and so a settled failure costs one
        // boolean rather than a throw per frame.
        if (!isConsoleReadable()) {
            return false;
        }

        try {
            return resolvePresence().isOverlayUp();

        } catch (LinkageError cannotReachConsole) {

            // Installed, but its overlay panel or accessor is not where this was compiled against:
            // a Console Commands release moved, renamed or dropped it.
            return reportUnreadable(
                "Console Commands is installed but its overlay panel could not be reached.",
                cannotReachConsole);

        } catch (Throwable consoleReadFailed) {

            return reportUnreadable(
                "Reading Console Commands' overlay state failed.",
                consoleReadFailed);
        }
    }

    // Whether a console read is worth attempting at all: the mod is installed and nothing has
    // broken yet. A mod-manager read that throws answers "not readable", the fail-open answer.
    private boolean isConsoleReadable() {
        
        if (isConsoleReadable == null) {
            try {
                isConsoleReadable = Global.getSettings().getModManager()
                    .isModEnabled(CONSOLE_COMMANDS_MOD_ID);

            } catch (Throwable cannotReadModState) {

                reportUnreadable(
                    "Could not read whether Console Commands is installed.",
                    cannotReadModState);
            }
        }
        return isConsoleReadable;
    }

    // The presence, built behind the gate on first use. Not synchronised: every ask arrives on the
    // game's own thread, and a second construction would in any case only rebuild a stateless read.
    private ConsoleOverlayPresence resolvePresence() {

        if (presence == null) {
            presence = new ConsoleCommandsPanelPresence();
        }
        return presence;
    }

    // Settles the read off for the session and says why, once. Returns the fail-open answer so a
    // failure branch reads as one line at the call site.
    private boolean reportUnreadable(String cause, Throwable failure) {

        isConsoleReadable = false;

        if (!hasWarnedThisSession) {
            hasWarnedThisSession = true;
            LOG.warn(cause + FAIL_OPEN_CONSEQUENCE, failure);
        }
        return false;
    }
}
