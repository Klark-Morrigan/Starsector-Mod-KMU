package kmu.console;

import kmu.ui.editor.KmuConditionEditorOpenResult;
import org.junit.jupiter.api.Test;
import org.lazywizard.console.BaseCommand.CommandContext;
import org.lazywizard.console.BaseCommand.CommandResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class KmuOpenConditionsCommandTest {
    @Test
    void rejectsNonCampaignContextWithoutOpeningEditor() {
        AtomicBoolean opened = new AtomicBoolean(false);
        List<String> output = new ArrayList<>();
        KmuOpenConditionsCommand command = new KmuOpenConditionsCommand(
                () -> {
                    opened.set(true);
                    return KmuConditionEditorOpenResult.opened();
                },
                output::add);

        CommandResult result = command.runCommand("", CommandContext.COMBAT_MISSION);

        assertThat(result).isEqualTo(CommandResult.WRONG_CONTEXT);
        assertThat(opened).isFalse();
        assertThat(output).containsExactly("kmu_open_conditions can only run from campaign or market context.");
    }

    @Test
    void opensEditorInCampaignContext() {
        AtomicBoolean opened = new AtomicBoolean(false);
        List<String> output = new ArrayList<>();
        KmuOpenConditionsCommand command = new KmuOpenConditionsCommand(
                () -> {
                    opened.set(true);
                    return KmuConditionEditorOpenResult.opened();
                },
                output::add);

        CommandResult result = command.runCommand("", CommandContext.CAMPAIGN_MARKET);

        assertThat(result).isEqualTo(CommandResult.SUCCESS);
        assertThat(opened).isTrue();
        assertThat(output).containsExactly("Opened planetary condition editor.");
    }

    @Test
    void surfacesNoMarketContextAsWrongContext() {
        List<String> output = new ArrayList<>();
        KmuOpenConditionsCommand command = new KmuOpenConditionsCommand(
                KmuConditionEditorOpenResult::noMarketContext,
                output::add);

        CommandResult result = command.runCommand("", CommandContext.CAMPAIGN_MAP);

        assertThat(result).isEqualTo(CommandResult.WRONG_CONTEXT);
        assertThat(output).containsExactly("No active market context supports planetary condition editing.");
    }

    @Test
    void surfacesUnsupportedTargetAsWrongContext() {
        List<String> output = new ArrayList<>();
        KmuOpenConditionsCommand command = new KmuOpenConditionsCommand(
                () -> KmuConditionEditorOpenResult.unsupportedTarget("Unsupported target."),
                output::add);

        CommandResult result = command.runCommand("", CommandContext.CAMPAIGN_MARKET);

        assertThat(result).isEqualTo(CommandResult.WRONG_CONTEXT);
        assertThat(output).containsExactly("Unsupported target.");
    }

    @Test
    void surfacesEntryPointFailureAsError() {
        List<String> output = new ArrayList<>();
        KmuOpenConditionsCommand command = new KmuOpenConditionsCommand(
                () -> KmuConditionEditorOpenResult.failed(
                        "Failed to open planetary condition editor.",
                        new IllegalStateException("dialog unavailable")),
                output::add);

        CommandResult result = command.runCommand("", CommandContext.CAMPAIGN_MARKET);

        assertThat(result).isEqualTo(CommandResult.ERROR);
        assertThat(output).containsExactly("Failed to open planetary condition editor: dialog unavailable");
    }

    @Test
    void catchesUnexpectedEntryPointException() {
        List<String> output = new ArrayList<>();
        KmuOpenConditionsCommand command = new KmuOpenConditionsCommand(
                () -> {
                    throw new IllegalStateException("unexpected failure");
                },
                output::add);

        CommandResult result = command.runCommand("", CommandContext.CAMPAIGN_MARKET);

        assertThat(result).isEqualTo(CommandResult.ERROR);
        assertThat(output).containsExactly("Failed to open planetary condition editor: unexpected failure");
    }
}
