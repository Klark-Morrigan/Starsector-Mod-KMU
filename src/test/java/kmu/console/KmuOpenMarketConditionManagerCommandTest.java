package kmu.console;

import kmu.conditions.ui.editor.KmuConditionEditorOpenResult;
import org.junit.jupiter.api.Test;
import org.lazywizard.console.BaseCommand.CommandContext;
import org.lazywizard.console.BaseCommand.CommandResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class KmuOpenMarketConditionManagerCommandTest {
    @Test
    void rejectsNonCampaignContextWithoutOpeningEditor() {
        AtomicBoolean opened = new AtomicBoolean(false);
        List<String> output = new ArrayList<>();
        KmuOpenMarketConditionManagerCommand command = new KmuOpenMarketConditionManagerCommand(
                () -> {
                    opened.set(true);
                    return KmuConditionEditorOpenResult.opened();
                },
                output::add);

        CommandResult result = command.runCommand("", CommandContext.COMBAT_MISSION);

        assertThat(result).isEqualTo(CommandResult.WRONG_CONTEXT);
        assertThat(opened).isFalse();
        assertThat(output).containsExactly("kmu_mcm_open can only run from campaign or market context.");
    }

    @Test
    void opensEditorInCampaignContext() {
        AtomicBoolean opened = new AtomicBoolean(false);
        List<String> output = new ArrayList<>();
        KmuOpenMarketConditionManagerCommand command = new KmuOpenMarketConditionManagerCommand(
                () -> {
                    opened.set(true);
                    return KmuConditionEditorOpenResult.opened();
                },
                output::add);

        CommandResult result = command.runCommand("", CommandContext.CAMPAIGN_MARKET);

        assertThat(result).isEqualTo(CommandResult.SUCCESS);
        assertThat(opened).isTrue();
        assertThat(output).containsExactly("Opened Market Condition Manager.");
    }

    @Test
    void surfacesNoMarketContextAsWrongContext() {
        List<String> output = new ArrayList<>();
        KmuOpenMarketConditionManagerCommand command = new KmuOpenMarketConditionManagerCommand(
                KmuConditionEditorOpenResult::noMarketContext,
                output::add);

        CommandResult result = command.runCommand("", CommandContext.CAMPAIGN_MAP);

        assertThat(result).isEqualTo(CommandResult.WRONG_CONTEXT);
        assertThat(output).containsExactly("No active context supports market condition editing.");
    }

    @Test
    void surfacesUnsupportedTargetAsWrongContext() {
        List<String> output = new ArrayList<>();
        KmuOpenMarketConditionManagerCommand command = new KmuOpenMarketConditionManagerCommand(
                () -> KmuConditionEditorOpenResult.unsupportedTarget("Unsupported target."),
                output::add);

        CommandResult result = command.runCommand("", CommandContext.CAMPAIGN_MARKET);

        assertThat(result).isEqualTo(CommandResult.WRONG_CONTEXT);
        assertThat(output).containsExactly("Unsupported target.");
    }

    @Test
    void surfacesEntryPointFailureAsError() {
        List<String> output = new ArrayList<>();
        KmuOpenMarketConditionManagerCommand command = new KmuOpenMarketConditionManagerCommand(
                () -> KmuConditionEditorOpenResult.failed(
                        "Failed to open Market Condition Manager.",
                        new IllegalStateException("dialog unavailable")),
                output::add);

        CommandResult result = command.runCommand("", CommandContext.CAMPAIGN_MARKET);

        assertThat(result).isEqualTo(CommandResult.ERROR);
        assertThat(output).containsExactly("Failed to open Market Condition Manager: dialog unavailable");
    }

    @Test
    void catchesUnexpectedEntryPointException() {
        List<String> output = new ArrayList<>();
        KmuOpenMarketConditionManagerCommand command = new KmuOpenMarketConditionManagerCommand(
                () -> {
                    throw new IllegalStateException("unexpected failure");
                },
                output::add);

        CommandResult result = command.runCommand("", CommandContext.CAMPAIGN_MARKET);

        assertThat(result).isEqualTo(CommandResult.ERROR);
        assertThat(output).containsExactly("Failed to open Market Condition Manager: unexpected failure");
    }
}
