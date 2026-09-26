package kmu.settings;

import com.fs.starfarer.api.campaign.econ.MarketAPI.SurveyLevel;

import kmlib.settings.LabeledChoice;

/**
 * How far a world has to have been surveyed before a map layer will say what is on it - the
 * player's choice behind a survey-level Radio setting.
 *
 * <p>The game's own survey ladder, named for the settings screen. A player who wants the map to
 * stay behind their fleet asks for a full survey; one who wants to see the sector laid out asks
 * for nothing at all. This names the options; {@link #resolveSurveyLevel} maps a choice back to
 * the game's own value, so a rule compares survey levels rather than matching stored labels.
 *
 * <p>Its own enum rather than the game's, because a LunaLib Radio row is a list of labels and
 * {@link SurveyLevel} carries none - and a label is a stored key once shipped, where an engine
 * enum's constant names are the engine's to rename. Its labels are the Radio row's options, spelt
 * identically - see {@link LabeledChoice} for why they never change.
 */
public enum SurveyLevelChoice implements LabeledChoice {

    NOT_SURVEYED("Not surveyed", SurveyLevel.NONE),
    SEEN("Seen", SurveyLevel.SEEN),
    PRELIMINARY("Preliminary", SurveyLevel.PRELIMINARY),
    FULL("Full", SurveyLevel.FULL);

    private final String label;
    private final SurveyLevel surveyLevel;

    SurveyLevelChoice(String label, SurveyLevel surveyLevel) {
        this.label = label;
        this.surveyLevel = surveyLevel;
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
     * @return the game's own survey level this choice stands for, which is what a visibility rule
     *         compares a world against
     */
    public SurveyLevel resolveSurveyLevel() {
        return surveyLevel;
    }
}
