package io.github.contractautomata.CIF3Connector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** Entry point for experiments using a precomputed composition. */
public final class CIF3ConnectorExperiments {
    private CIF3ConnectorExperiments() { }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: CIF3ConnectorExperiments <composition.data> <escet-bin-directory>");
            System.exit(2);
            return;
        }

        try {
            Path composition = Path.of(args[0]);
            if (!args[0].endsWith(".data") || !Files.isRegularFile(composition)
                    || !Files.isReadable(composition)) {
                System.err.println("Input must be an existing readable .data composition file: " + args[0]);
                System.exit(1);
                return;
            }

            boolean windows = System.getProperty("os.name").startsWith("Windows");
            Path escetBin = Path.of(args[1]).toAbsolutePath();
            Path synthesisExecutable = escetBin.resolve(windows ? "cifdatasynth.cmd" : "cifdatasynth");
            Path explorationExecutable = escetBin.resolve(windows ? "cifexplorer.cmd" : "cifexplorer");
            Path equivalenceExecutable = escetBin.resolve(windows ? "ciflngeqv.cmd" : "ciflngeqv");
            for (Path executable : new Path[]{synthesisExecutable, explorationExecutable, equivalenceExecutable}) {
                if (!Files.isDirectory(escetBin) || !Files.isRegularFile(executable)
                        || !Files.isReadable(executable) || (!windows && !Files.isExecutable(executable))) {
                    System.err.println("ESCET bin directory must contain a runnable " + executable.getFileName()
                            + ": " + escetBin);
                    System.exit(1);
                    return;
                }
            }

            String basename = composition.getFileName().toString();
            String stem = basename.substring(0, basename.length() - ".data".length());
            if (!stem.contains("Composition")) {
                System.err.println("Input basename must contain Composition: " + basename);
                System.exit(1);
                return;
            }

            String compositionCif = stem + ".cif";
            String orchestrationCif = stem.replace("Composition", "Orchestration") + ".cif";
            String controlledCif = stem + ".ctrlsys.cif";
            String statespaceCif = controlledCif.substring(0, controlledCif.length() - ".cif".length())
                    + "_statespace.cif";
            String equivalenceCif = stem.replace("Composition", "Equivalence") + ".cif";
            for (String output : new String[]{compositionCif, orchestrationCif, controlledCif,
                    statespaceCif, equivalenceCif}) {
                if (Files.exists(Path.of(output), LinkOption.NOFOLLOW_LINKS)) {
                    System.err.println("Output already exists; refusing to overwrite: " + output);
                    System.exit(1);
                    return;
                }
            }

            System.out.println("Input composition: " + args[0]);
            CIF3Connector.main(new String[]{"-i", composition.toAbsolutePath().toString(),
                    "-o", compositionCif, orchestrationCif});

            runEscetTool(synthesisExecutable, compositionCif, controlledCif, "CIF data synthesis", windows);
            runEscetTool(explorationExecutable, controlledCif, statespaceCif, "CIF state-space exploration", windows);

            long preparationStart = System.nanoTime();
            CIF3EquivalencePreparation.prepare(Path.of(orchestrationCif), Path.of(statespaceCif),
                    Path.of(equivalenceCif));
            long preparationElapsed = System.nanoTime() - preparationStart;
            System.out.println("CIF equivalence preparation time (ms): " + preparationElapsed / 1_000_000.0);
            if (!Files.isRegularFile(Path.of(equivalenceCif))) {
                throw new IOException("Equivalence preparation did not produce the expected output file: "
                        + equivalenceCif);
            }
            runEscetTool(equivalenceExecutable, equivalenceCif, null, "CIF language-equivalence checking", windows);
        } catch (InvalidPathException e) {
            System.err.println("Invalid path: " + e.getInput());
            System.exit(1);
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Experiment failed: " + e.getMessage());
            System.exit(1);
        }
    }

    /** A null outputCif skips the generated-file check for console-only tools. */
    private static void runEscetTool(Path executable, String inputCif, String outputCif,
                                     String stage, boolean windows) throws IOException {
        ProcessBuilder command;
        if (windows) {
            // Keep paths quoted without expanding shell characters inside them.
            command = new ProcessBuilder("cmd.exe", "/d", "/v:off", "/s", "/c",
                    "\"\"%CIF3CONNECTOR_TOOL_EXECUTABLE%\" \"%CIF3CONNECTOR_TOOL_INPUT%\"\"");
            command.environment().put("CIF3CONNECTOR_TOOL_EXECUTABLE", executable.toString());
            command.environment().put("CIF3CONNECTOR_TOOL_INPUT", inputCif);
        } else {
            command = new ProcessBuilder(executable.toString(), inputCif);
        }
        command.directory(Path.of("").toAbsolutePath().toFile());
        command.inheritIO();
        long start = System.nanoTime();
        Process process = command.start();
        int exitCode;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException(stage + " interrupted.", e);
        }
        long elapsed = System.nanoTime() - start;
        System.out.println(stage + " wall-clock time (ms): " + elapsed / 1_000_000.0);
        if (exitCode != 0) {
            throw new IOException(executable.getFileName() + " exited with code " + exitCode);
        }
        if (outputCif != null && !Files.isRegularFile(Path.of(outputCif))) {
            throw new IOException(executable.getFileName() + " did not produce the expected output file: " + outputCif);
        }
    }
}
