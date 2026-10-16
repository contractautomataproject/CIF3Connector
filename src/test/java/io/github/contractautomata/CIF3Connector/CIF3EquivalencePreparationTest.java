package io.github.contractautomata.CIF3Connector;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

/** Dependency-free regression suite. Run main; checks do not require -ea. */
public final class CIF3EquivalencePreparationTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        String first = document("controllable a;", "Aut.a", "");
        String second = document("controllable a, b;", "Aut.a,\n Aut.b",
                "    edge Aut.b goto s;\n");
        String merged = CIF3EquivalencePreparation.merge(first, second);
        verify(first, second, merged);
        check(count(merged, "edge Aut.b goto s;") == 1, "Mutation edge preserved only in its input");
        check(count(merged, "controllable a;") == 1, "Shared declaration deduplicated");
        check(count(merged, "controllable b;") == 1, "Additional declaration retained");
        check(merged.contains("@state(Aut: \"end; \\\"location\\\"\")"), "Annotation preserved");
        String whitespace = second.replace("group Aut:", "/* group end */ group\n Aut :")
                .replace("automaton x:", "// automaton end\nautomaton\nx :");
        verify(first, whitespace, CIF3EquivalencePreparation.merge(first, whitespace));
        verify(first, first, CIF3EquivalencePreparation.merge(first, first));
        String empty = "group Aut: end automaton x: alphabet; location s: initial; marked; end";
        verify(empty, empty, CIF3EquivalencePreparation.merge(empty, empty));

        fails(() -> CIF3EquivalencePreparation.merge(first, first.replace("controllable a", "uncontrollable a")), "Conflicting controllability");
        String conflicting = second.replace("controllable a, b;", "uncontrollable a, b;");
        fails(() -> CIF3EquivalencePreparation.merge(first, conflicting), "Conflicting controllability");
        String resolved = CIF3EquivalencePreparation.merge(first, conflicting, true);
        check(count(resolved, "controllable a;") == 1
                && !eventDeclarations(resolved).contains("uncontrollable a;"),
                "Ignore flag keeps first input classification");
        String reversed = CIF3EquivalencePreparation.merge(conflicting, first, true);
        check(count(reversed, "uncontrollable a;") == 1
                && !eventDeclarations(reversed).contains("controllable a;"),
                "Reversed inputs reverse classification precedence");
        check(resolved.contains("uncontrollable b;") && reversed.contains("uncontrollable b;"),
                "Events declared only in one input are preserved");
        verify(first, conflicting, resolved);
        fails(() -> CIF3EquivalencePreparation.merge(first, first.replace("controllable a;", "controllable a; uncontrollable a;")), "Conflicting controllability");
        for (String invalid : List.of(
                first + " automaton extra: alphabet Aut.a; location s; end",
                first.replace("alphabet Aut.a;", "alphabet Aut.a; alphabet Aut.a;"),
                first.replace("alphabet Aut.a;", "alphabet Aut.missing;"),
                first.replace("alphabet Aut.a;", "alphabet;"),
                first.replace("goto s", "goto missing"),
                first.replace("edge Aut.a", "edge Aut.a when true"),
                first.replace("location s:", "disc int n = 0; location s:"),
                first.replace("@state", "@other"),
                first.replace("location s:", "location s; location s:"),
                first.replace("plant automaton", "supervisor automaton"),
                first + " /* unclosed", first.replace("marked;", "marked true;"))) {
            fails(() -> CIF3EquivalencePreparation.merge(first, invalid), null);
        }

        Path resources = Path.of("src/main/resources");
        Path catlib = resources.resolve("CardOrchestration.cif");
        Path cif3 = resources.resolve("CardComposition.ctrlsys_statespace.cif");
        byte[] originalCatlib = Files.readAllBytes(catlib);
        byte[] originalCif3 = Files.readAllBytes(cif3);
        Path output = Path.of("target/equivalence-tests/CardEquivalence.cif");
        Files.createDirectories(output.getParent());
        CIF3EquivalencePreparation.prepare(catlib, cif3, output);
        String card = Files.readString(output);
        verify(Files.readString(catlib), Files.readString(cif3), card);
        check(Arrays.equals(originalCatlib, Files.readAllBytes(catlib)), "CATLib input unchanged");
        check(Arrays.equals(originalCif3, Files.readAllBytes(cif3)), "CIF3 input unchanged");
        String reference = Files.readString(resources.resolve("TwoCardOrchestrations.cif"));
        check(count(reference, "group Aut:") == 1 && count(reference, "automaton ") == 2,
                "Manual reference has expected document structure");
        check(eventDeclarations(card).equals(eventDeclarations(reference)), "Shared events agree with manual reference");
        fails(() -> CIF3EquivalencePreparation.prepare(catlib, cif3, catlib), "overwrite an input");
        fails(() -> CIF3EquivalencePreparation.prepare(catlib, cif3, cif3), "overwrite an input");
        Path invalid = output.resolveSibling("invalid.cif");
        Files.writeString(invalid, "unsupported");
        String before = Files.readString(output);
        fails(() -> CIF3EquivalencePreparation.prepare(catlib, invalid, output), "Expected");
        check(before.equals(Files.readString(output)), "Failed parsing leaves existing output untouched");
        System.out.println("Passed " + checks + " checks. Card output: " + output);
    }

    private static String document(String declarations, String alphabet, String extra) {
        return "group Aut:\n " + declarations + "\nend\nplant automaton x:\n  alphabet "
                + alphabet + ";\n  @state(Aut: \"end; \\\"location\\\"\")\n"
                + "  location s:\n    initial;\n    marked;\n    edge Aut.a goto s;\n"
                + extra + "end\n";
    }

    private static void verify(String first, String second, String merged) {
        check(count(merged, "group Aut:") == 1, "Exactly one shared group");
        check(count(merged, "automaton ") == 2, "Exactly two automata");
        check(merged.contains("automaton statespace:") && merged.contains("automaton statespace2:"), "Distinct names");
        var matcher = Pattern.compile("alphabet\\s*([^;]*);").matcher(merged);
        check(matcher.find(), "First alphabet exists");
        String alphabet = matcher.group(1);
        check(matcher.find() && alphabet.equals(matcher.group(1)), "Identical alphabets");
        var expected = alphabet(first);
        expected.addAll(alphabet(second));
        check(expected.equals(alphabet(merged)), "Alphabet is exactly the union");
        // Exact substring comparison checks every location, edge, flag, annotation and comment.
        int firstEnd = merged.indexOf("end\n", merged.indexOf("alphabet"));
        int firstStart = merged.indexOf(';', merged.indexOf("alphabet")) + 1;
        check(body(first).equals(merged.substring(firstStart, firstEnd)), "First body unchanged");
        int secondStart = merged.indexOf(';', merged.indexOf("alphabet", firstEnd)) + 1;
        check(body(second).equals(merged.substring(secondStart, merged.lastIndexOf("end"))), "Second body unchanged");
    }

    private static String body(String document) {
        return document.substring(document.indexOf(';', document.indexOf("alphabet")) + 1,
                document.lastIndexOf("end"));
    }

    private static HashSet<String> alphabet(String document) {
        var matcher = Pattern.compile("alphabet\\s*([^;]*);").matcher(document);
        if (!matcher.find()) throw new AssertionError("Missing alphabet");
        var result = new HashSet<String>();
        for (String event : matcher.group(1).split(",")) {
            if (!event.isBlank()) result.add(event.trim());
        }
        return result;
    }

    private static HashSet<String> eventDeclarations(String document) {
        var result = new HashSet<String>();
        var matcher = Pattern.compile("\\b(?:uncontrollable|controllable)\\s+\\w+;").matcher(document);
        while (matcher.find()) result.add(matcher.group());
        return result;
    }

    private static int count(String text, String needle) {
        return (text.length() - text.replace(needle, "").length()) / needle.length();
    }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private interface Operation { void run() throws Exception; }
    private static void fails(Operation operation, String message) throws Exception {
        try { operation.run(); }
        catch (IllegalArgumentException e) {
            check(message == null || e.getMessage().contains(message), "Explicit error: " + e.getMessage());
            return;
        }
        throw new AssertionError("Expected rejection");
    }
}
