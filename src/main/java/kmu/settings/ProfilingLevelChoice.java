package kmu.settings;

import kmlib.profiling.ProfileLevel;
import kmlib.settings.LabeledChoice;

/**
 * How much of what the mod does is measured - the player's choice behind the profiling-level Radio
 * setting.
 *
 * <p>A level rather than a switch because the two things worth measuring cost differently: the
 * beats of a frame are a handful of clock reads a frame, while the turns of a per-cell loop are a
 * read per step per cell. So a reader after "where did the frame go" pays neither the finer reads
 * nor the noise they add to the report, and a reader after "what does one cell cost" asks for them.
 *
 * <p>Its own enum rather than {@link ProfileLevel}, because a LunaLib Radio row is a list of labels
 * and the library's level carries none - and a label is a stored key once shipped, where a library
 * enum's constant names are the library's to rename. The labels here must match the
 * {@code secondaryValue} options in data/config/LunaSettings.csv exactly, and both are frozen once
 * shipped - see {@link LabeledChoice} for what a reworded label costs.
 */
public enum ProfilingLevelChoice implements LabeledChoice {

    OFF("Off", ProfileLevel.OFF),
    COARSE("Coarse", ProfileLevel.COARSE),
    FINE("Fine", ProfileLevel.FINE);

    private final String label;
    private final ProfileLevel profileLevel;

    ProfilingLevelChoice(String label, ProfileLevel profileLevel) {
        this.label = label;
        this.profileLevel = profileLevel;
    }

    /**
     * @return the LunaLib Radio option label for this choice, used both as a read fallback and as
     *         the CSV default value
     */
    @Override
    public String getLabel() {
        return label;
    }

    /**
     * @return the library's own level this choice stands for, which is what decides both which
     *         profiler is bound and which sections that profiler times
     */
    public ProfileLevel resolveProfileLevel() {
        return profileLevel;
    }
}
