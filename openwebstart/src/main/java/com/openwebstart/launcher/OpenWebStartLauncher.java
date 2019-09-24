package com.openwebstart.launcher;

import com.install4j.api.launcher.StartupNotification;
import com.install4j.runtime.installer.helper.InstallerUtil;
import com.openwebstart.jvm.JavaRuntimeSelector;
import com.openwebstart.jvm.LocalRuntimeManager;
import com.openwebstart.jvm.ui.dialogs.AskForRuntimeUpdateDialog;
import com.openwebstart.jvm.ui.dialogs.RuntimeDownloadDialog;
import net.adoptopenjdk.icedteaweb.commandline.CommandLineOptions;
import net.adoptopenjdk.icedteaweb.logging.Logger;
import net.adoptopenjdk.icedteaweb.logging.LoggerFactory;
import net.sourceforge.jnlp.runtime.JNLPRuntime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static net.sourceforge.jnlp.runtime.ForkingStrategy.ALWAYS;


/**
 * This launcher resolves OS specific command line argument handling and starts Iced-Tea Web with the correct
 * argument arrangement.
 */
public class OpenWebStartLauncher {

    private static final Logger LOG = LoggerFactory.getLogger(OpenWebStartLauncher.class);

    public static void main(String[] args) {
        LOG.info("OpenWebStartLauncher called with args {}.", Arrays.toString(args));
        LOG.debug("OS detected: Win[{}], MacOS[{}], Linux[{}]",
                InstallerUtil.isWindows(), InstallerUtil.isMacOS(), InstallerUtil.isLinux());

        final List<String> bootArgs = skipNotRelevantArgs(args);
        final JavaRuntimeProvider javaRuntimeProvider = JavaRuntimeSelector.getInstance();
        LocalRuntimeManager.getInstance().loadRuntimes();

        JavaRuntimeSelector.setDownloadHandler(RuntimeDownloadDialog::showDownloadDialog);
        JavaRuntimeSelector.setAskForUpdateFunction(AskForRuntimeUpdateDialog::askForUpdate);
        JNLPRuntime.setForkingStrategy(ALWAYS);

        final JnlpApplicationLauncher launcher = new JnlpApplicationLauncher(new OwsJvmLauncher(javaRuntimeProvider));

        /**
         * Listener will be called when the executable is started again or when a file open event is received.
         * Note that each invocation may be from a separate thread, therefore the implementation needs to be
         * synchronized.
         */
        StartupNotification.registerStartupListener(new StartupNotification.Listener() {
            public void startupPerformed(String parameters) {
                synchronized (this) {
                    if (InstallerUtil.isMacOS()) {
                        final String[] applicationParameters = parameters.split(" ");

                        LOG.info("MacOS detected, Launcher needs to add parameters {} to the list of arguments.", parameters);
                        final List<String> mergedParameters = new ArrayList<>();
                        mergedParameters.addAll(bootArgs);
                        mergedParameters.addAll(Arrays.asList(applicationParameters));

                        final String appName = extractAppName(mergedParameters);
                        launcher.launch(appName, mergedParameters.toArray(new String[0]));
                    }
                }
            }
        });

        // Windows and Linux are called here
        if (!InstallerUtil.isMacOS()) {
            final String appName = extractAppName(bootArgs);
            launcher.launch(appName, bootArgs.toArray(new String[0]));
        }
    }

    private static String extractAppName(final List<String> args) {
        return args.stream().filter(a -> a.toLowerCase().endsWith(".jnlp"))
                .map(a -> a.substring(0, a.length() - 5))
                .map(a -> {
                    String[] values = a.split("/");
                    if(values.length > 1) {
                        return values[values.length - 1];
                    }
                    return null;
                })
                .findAny()
                .orElse("unknown-app");

    }

    private static List<String> skipNotRelevantArgs(final String[] args) {
        final List<String> relevantJavawsArgs = Arrays.stream(args)
                .filter(arg -> !arg.equals(CommandLineOptions.NOFORK.getOption()))
                .collect(Collectors.toList());

        LOG.debug("RelevantJavawsArgs: '{}'", relevantJavawsArgs);

        return relevantJavawsArgs;
    }
}
