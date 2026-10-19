package kmu.mods.console.commands;

import com.fs.starfarer.api.Global;

import kmlib.mods.console.ConsoleStrings;
import kmlib.mods.console.commands.BaseKmlibCommand;
import kmlib.mods.console.commands.output.CommandOutput;
import kmlib.mods.console.commands.output.ConsoleCommandOutput;
import kmlib.mods.console.commands.parsing.ParameterSpec;

import kmu.KmuErrorReporter;
import kmu.conditions.domain.KmuConditionService;
import kmu.conditions.domain.StarsectorConditionOfferPolicy;
import kmu.conditions.domain.StarsectorConditionRepository;
import kmu.conditions.ui.context.StarsectorMarketUiContextResolver;
import kmu.conditions.ui.editor.KmuConditionEditorEntryPoint;
import kmu.conditions.ui.editor.KmuConditionEditorOpenResult;
import kmu.conditions.ui.editor.KmuConditionEditorOpenStatus;
import kmu.conditions.ui.picker.KmuConditionPickerEditor;
import kmu.conditions.ui.picker.action.StarsectorConditionPickerFeedbackSink;
import kmu.conditions.ui.picker.dialog.StarsectorInteractionDialogPickerOpener;
import kmu.settings.KmuFeatureSettings;
import kmu.settings.KmuMarketConditionSettings;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static kmlib.text.KmlibStrings.normaliseText;

import static kmu.util.KmuMarketConditionStringKeys.CONSOLE_CONDITION_MANAGER_FAILED_TO_OPEN;
import static kmu.util.KmuMarketConditionStringKeys.CONSOLE_CONDITION_MANAGER_FAILURE;
import static kmu.util.KmuMarketConditionStringKeys.CONSOLE_CONDITION_MANAGER_FAILURE_WITH_CAUSE;
import static kmu.util.KmuMarketConditionStringKeys.CONSOLE_CONDITION_MANAGER_SWITCHED_OFF;
import static kmu.util.KmuStringKeys.CATEGORY;

public final class KmuOpenMarketConditionManagerCommand
        extends BaseKmlibCommand {

    // No parameters; declaring the spec still makes the parser reject a stray
    // argument as bad syntax rather than silently ignoring it.
    private static final ParameterSpec SPEC =
        ParameterSpec.takingNoArguments("kmu_mcm_open");

    private final BooleanSupplier isFeatureSwitchedOn;
    private final Supplier<KmuConditionEditorOpenResult> openEditor;

    public KmuOpenMarketConditionManagerCommand() {

        this(
            KmuFeatureSettings::isMarketConditionManagerEnabled,
            () -> createDefaultEntryPoint().openForCurrentMarketDetailed(),
            ConsoleCommandOutput.INSTANCE);
    }

    KmuOpenMarketConditionManagerCommand(
            BooleanSupplier isFeatureSwitchedOn,
            Supplier<KmuConditionEditorOpenResult> openEditor,
            CommandOutput output) {

        super(output);

        this.isFeatureSwitchedOn = Objects.requireNonNull(isFeatureSwitchedOn, "isFeatureSwitchedOn");
        this.openEditor = Objects.requireNonNull(openEditor, "openEditor");
    }

    @Override
    public CommandResult runCommand(String args, CommandContext context) {

        Objects.requireNonNull(context, "context");

        // Ahead of the context and syntax guards, because it is the more fundamental answer: a
        // player told they are in the wrong place would go and type the command somewhere else,
        // where a feature that is switched off still opens nothing.
        //
        // The refusal names where the switch is, because the command is registered by data file whatever the
        // switch says: Console Commands reads commands.csv at load, so the command exists to be typed even when
        // nothing will open, and a bare refusal would read as the command being broken.
        if (!isFeatureSwitchedOn.getAsBoolean()) {

            output.showMessage(ConsoleStrings.get(CATEGORY, CONSOLE_CONDITION_MANAGER_SWITCHED_OFF));
            return CommandResult.ERROR;
        }

        // Defer the campaign-context guard to KMLib's shared validator so this
        // command's precondition check and feedback match every other KM
        // console command rather than maintaining a parallel inline check.
        var parsed = readInput(context, args)
            .requireCampaign()
            .parseArguments(SPEC);

        if (!parsed.isValid()) {
            return parsed.getResult();
        }

        KmuConditionEditorOpenResult result;

        try {
            result = openEditor.get();

        } catch (RuntimeException runtimeException) {

            output.showMessage(describeFailure(
                ConsoleStrings.get(CATEGORY, CONSOLE_CONDITION_MANAGER_FAILED_TO_OPEN),
                runtimeException));
            return CommandResult.ERROR;
        }

        output.showMessage(messageFor(result));

        if (result.getStatus() == KmuConditionEditorOpenStatus.OPENED) {
            return CommandResult.SUCCESS;
        }

        if (result.getStatus() == KmuConditionEditorOpenStatus.NO_MARKET_CONTEXT
                || result.getStatus() == KmuConditionEditorOpenStatus.UNSUPPORTED_TARGET) {

            return CommandResult.WRONG_CONTEXT;
        }
        return CommandResult.ERROR;
    }

    private String messageFor(KmuConditionEditorOpenResult result) {

        if (result.getStatus() != KmuConditionEditorOpenStatus.FAILED) {
            return result.getMessage();
        }
        return describeFailure(result.getMessage(), result.getCause().orElse(null));
    }

    // A failure, with what threw where it says anything. The two templates carry the closing punctuation, so a
    // translation places it as its language does rather than this code trimming an English full stop.
    private static String describeFailure(String failure, RuntimeException cause) {

        var causeMessage = cause == null
            ? null
            : normaliseText(cause.getMessage());

        return causeMessage == null
            ? ConsoleStrings.format(CATEGORY, CONSOLE_CONDITION_MANAGER_FAILURE, failure)
            : ConsoleStrings.format(CATEGORY, CONSOLE_CONDITION_MANAGER_FAILURE_WITH_CAUSE, failure, causeMessage);
    }

    // The one read of the running game the editor makes. The parts below take it as handed, so the
    // market they resolve, the dialog they open and the message they post all belong to one sector.
    private static KmuConditionEditorEntryPoint createDefaultEntryPoint() {

        var sector = Global.getSector();
        var conditionService = new KmuConditionService(
            new StarsectorConditionRepository(),
            KmuErrorReporter.createLoggingReporter(KmuConditionService.class),
            new StarsectorConditionOfferPolicy(KmuMarketConditionSettings::shouldOfferAllConditions));

        return new KmuConditionEditorEntryPoint(
            new StarsectorMarketUiContextResolver(sector),
            new KmuConditionPickerEditor(
                conditionService,
                new StarsectorInteractionDialogPickerOpener(sector),
                new StarsectorConditionPickerFeedbackSink(sector)));
    }
}
