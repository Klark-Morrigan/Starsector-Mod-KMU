package kmu.maplayers.base.refresh;

import kmu.settings.KmuLunaSettings;
import kmu.settings.KmuMapRefreshSettings;

import org.mockito.MockedStatic;

import static org.mockito.Mockito.mockStatic;

/**
 * Answers the two settings reads a poll decides a retune by, for the length of one body: the
 * announcement that something moved, and the cadence it moved to. Both are static reads, so they
 * can only be stood in for within a scope.
 *
 * <p>The two are settled together rather than one at a time, because neither says anything alone -
 * a revision with no cadence behind it is a case that observes nothing, and a cadence with no
 * revision is one the loop will never look at. Settling them as a pair is what stops a case
 * asserting about a retune it never actually asked for.
 *
 * <p>A scope object rather than the run-a-body shape its neighbours use: a case about a retune has
 * to move the answers part-way through, which a body handed one fixed set of them cannot do.
 */
final class RefreshSettingsScope implements AutoCloseable {

    // The cadence the reader answers with when nothing is stored, which is what every case that
    // opens no scope at all runs on. Stood in here so a case about a retune starts from the same
    // window as those.
    private static final int SHIPPED_POLL_SECONDS = 4;

    private static final int UNMOVED_SETTINGS_REVISION = 0;

    private final MockedStatic<KmuLunaSettings> lunaSettingsMock;
    private final MockedStatic<KmuMapRefreshSettings> refreshSettingsMock;

    private RefreshSettingsScope() {
        this.lunaSettingsMock = mockStatic(KmuLunaSettings.class);
        this.refreshSettingsMock = mockStatic(KmuMapRefreshSettings.class);
    }

    /**
     * Opens a scope answering the shipped cadence under a revision nothing has moved yet, so what a
     * case built inside it observes afterwards is the retune alone.
     *
     * @return the open scope, to be closed by the caller's try-with-resources
     */
    static RefreshSettingsScope openOnTheShippedCadence() {

        var scope = new RefreshSettingsScope();

        scope.settleCadence(UNMOVED_SETTINGS_REVISION, SHIPPED_POLL_SECONDS);

        return scope;
    }

    @Override
    public void close() {
        // Both, whatever the first one does: a scope that left one static stood in would leak it
        // into whatever case ran next, and the failure would land there rather than here.
        try {
            lunaSettingsMock.close();
        } finally {
            refreshSettingsMock.close();
        }
    }

    /**
     * Settles a moved revision with the cadence left exactly where it was - what every knob but this
     * one looks like from the loop, LunaLib announcing the settings rather than the setting.
     *
     * @param settingsRevision what the settings revision reads as
     */
    void settleRevisionAtTheShippedCadence(int settingsRevision) {
        settleCadence(settingsRevision, SHIPPED_POLL_SECONDS);
    }

    /**
     * Settles what the two reads answer from here on.
     *
     * @param settingsRevision what the settings revision reads as
     * @param pollSeconds      what the cadence reads as
     */
    void settleCadence(int settingsRevision, int pollSeconds) {

        lunaSettingsMock
            .when(KmuLunaSettings::getSettingsRevision)
            .thenReturn(settingsRevision);
        refreshSettingsMock
            .when(KmuMapRefreshSettings::getMapRefreshPollSeconds)
            .thenReturn(pollSeconds);
    }
}
