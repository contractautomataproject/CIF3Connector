package io.github.contractautomata.CIF3Connector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Prepares two explicit CIF state spaces for language comparison; does not run synthesis.
 * Supported grammar: one group Aut of untyped controllable/uncontrollable event lists,
 * followed by one (optionally plant) automaton with an explicit alphabet and named
 * locations. Locations support @state annotations, initial/marked flags and unguarded
 * single-event edges with local goto targets. Comments and multiline lists are supported.
 * Variables, guards, updates, nested definitions and other annotations are rejected.
 * Only the automaton name and alphabet are replaced; the location body is copied verbatim.
 */
public final class CIF3EquivalencePreparation {
    private CIF3EquivalencePreparation() { }

    public static void main(String[] args) {
        boolean ignoreConflicts = args.length > 0 && args[0].equals("--ignore-controllability-conflicts");
        int offset = ignoreConflicts ? 1 : 0;
        boolean invalidFlag = java.util.Arrays.stream(args, offset, args.length)
                .anyMatch(argument -> argument.startsWith("--"));
        if (args.length - offset != 3 || invalidFlag) {
            System.err.println("Usage: CIF3EquivalencePreparation [--ignore-controllability-conflicts] <first.cif> <second.cif> <output.cif>");
            System.exit(2);
        }
        try {
            prepare(Path.of(args[offset]), Path.of(args[offset + 1]), Path.of(args[offset + 2]), ignoreConflicts);
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("CIF equivalence preparation failed: " + e.getMessage());
            System.exit(1);
        }
    }
    /** Writes only after both inputs have been parsed and validated. Input aliases are rejected. */
    public static void prepare(Path catlib, Path cif3, Path output) throws IOException {
        prepare(catlib, cif3, output, false);
    }

    /** Writes only after both inputs have been parsed and validated. */
    public static void prepare(Path catlib, Path cif3, Path output,
                               boolean ignoreControllabilityConflicts) throws IOException {
        for (Path input : List.of(catlib, cif3)) {
            if (output.toAbsolutePath().normalize().equals(input.toAbsolutePath().normalize())
                    || (Files.exists(output) && Files.isSameFile(input, output))) {
                throw new IllegalArgumentException("Output must not overwrite an input: " + input);
            }
        }
        String merged = merge(Files.readString(catlib), Files.readString(cif3),
                ignoreControllabilityConflicts);
        Files.writeString(output, merged);
    }

    public static String merge(String catlib, String cif3) {
        return merge(catlib, cif3, false);
    }

    public static String merge(String catlib, String cif3, boolean ignoreControllabilityConflicts) {
        Document first = new Parser(catlib, "CATLib input").parse();
        Document second = new Parser(cif3, "CIF3 input").parse();
        Map<String, String> events = new LinkedHashMap<>(first.events);
        second.events.forEach((name, kind) -> {
            if (ignoreControllabilityConflicts && events.containsKey(name)
                    && !events.get(name).equals(kind)) {
                System.err.println("Warning: conflicting controllability for Aut." + name
                        + ": using first input classification " + events.get(name)
                        + " instead of " + kind);
            } else {
                declare(events, name, kind);
            }
        });
        Set<String> alphabet = new LinkedHashSet<>(first.alphabet);
        alphabet.addAll(second.alphabet);
        StringBuilder result = new StringBuilder("group Aut:\n");
        events.forEach((name, kind) -> result.append("  ").append(kind).append(' ')
                .append(name).append(";\n"));
        result.append("end\n\n");
        result.append(first.render("statespace", alphabet)).append('\n');
        result.append(second.render("statespace2", alphabet));
        return result.toString();
    }
    private static void declare(Map<String, String> events, String name, String kind) {
        String previous = events.putIfAbsent(name, kind);
        if (previous != null && !previous.equals(kind)) {
            throw new IllegalArgumentException("Conflicting controllability for Aut." + name
                    + ": " + previous + " versus " + kind);
        }
    }

    private record Document(Map<String, String> events, Set<String> alphabet,
                            boolean plant, String body) {
        String render(String name, Set<String> commonAlphabet) {
            return (plant ? "plant " : "") + "automaton " + name + ":\n  alphabet "
                    + String.join(", ", commonAlphabet) + ";" + body + "end\n";
        }
    }

    private record Token(String text, int start, int end) { }

    private static final class Parser {
        private final String source;
        private final String label;
        private final List<Token> tokens = new ArrayList<>();
        private int pos;

        Parser(String source, String label) {
            this.source = source;
            this.label = label;
            tokenize();
        }

        private IllegalArgumentException error(String message) {
            int offset = pos < tokens.size() ? tokens.get(pos).start : source.length();
            long line = source.substring(0, offset).chars().filter(c -> c == '\n').count() + 1;
            return new IllegalArgumentException(label + " at line " + line + ": " + message);
        }

        private void tokenize() {
            int i = 0;
            while (i < source.length()) {
                char c = source.charAt(i);
                if (Character.isWhitespace(c)) { i++; continue; }
                if (source.startsWith("//", i)) {
                    int end = source.indexOf('\n', i + 2);
                    i = end < 0 ? source.length() : end + 1;
                    continue;
                }
                if (source.startsWith("/*", i)) {
                    int end = source.indexOf("*/", i + 2);
                    if (end < 0) throw error("Unterminated comment");
                    i = end + 2;
                    continue;
                }
                int start = i++;
                if (c == '"') {
                    boolean closed = false;
                    while (i < source.length()) {
                        char next = source.charAt(i++);
                        if (next == '\\') {
                            if (i < source.length()) i++;
                        } else if (next == '"') { closed = true; break; }
                    }
                    if (!closed) throw error("Unterminated string");
                } else if (Character.isLetter(c) || c == '_') {
                    while (i < source.length() && (Character.isLetterOrDigit(source.charAt(i))
                            || source.charAt(i) == '_')) i++;
                }
                tokens.add(new Token(source.substring(start, i), start, i));
            }
        }

        private String peek() { return pos < tokens.size() ? tokens.get(pos).text : "<EOF>"; }
        private boolean accept(String text) {
            if (!peek().equals(text)) return false;
            pos++;
            return true;
        }
        private void expect(String text) {
            if (!accept(text)) throw error("Expected '" + text + "', found '" + peek() + "'");
        }
        private String identifier() {
            String value = peek();
            if (!value.matches("[A-Za-z_][A-Za-z_0-9]*")
                    || Set.of("end", "location", "initial", "marked", "edge", "goto", "alphabet",
                    "group", "automaton", "plant", "controllable", "uncontrollable").contains(value)) {
                throw error("Expected a plain identifier, found '" + value + "'");
            }
            pos++;
            return value;
        }
        private String event() {
            expect("Aut");
            expect(".");
            return "Aut." + identifier();
        }

        Document parse() {
            Map<String, String> events = new LinkedHashMap<>();
            expect("group"); expect("Aut"); expect(":");
            while (!accept("end")) {
                String kind = peek();
                if (!accept("controllable") && !accept("uncontrollable"))
                    throw error("Only controllable/uncontrollable event declarations are supported");
                do { declare(events, identifier(), kind); } while (accept(","));
                expect(";");
            }
            boolean plant = accept("plant");
            expect("automaton"); identifier(); expect(":");
            expect("alphabet");
            Set<String> alphabet = new LinkedHashSet<>();
            if (!peek().equals(";")) {
                do {
                    String event = event();
                    if (!events.containsKey(event.substring(4))) throw error("Undeclared event " + event);
                    alphabet.add(event);
                } while (accept(","));
            }
            expect(";");
            int bodyStart = tokens.get(pos - 1).end;
            Set<String> locations = new LinkedHashSet<>();
            List<String> targets = new ArrayList<>();
            while (!peek().equals("end")) {
                while (accept("@")) annotation();
                expect("location");
                String location = identifier();
                if (!locations.add(location)) throw error("Duplicate location " + location);
                if (accept(";")) continue;
                expect(":");
                boolean initial = false;
                boolean marked = false;
                while (Set.of("initial", "marked", "edge").contains(peek())) {
                    if (accept("initial")) {
                        if (initial) throw error("Duplicate initial flag");
                        initial = true;
                    } else if (accept("marked")) {
                        if (marked) throw error("Duplicate marked flag");
                        marked = true;
                    } else {
                        expect("edge");
                        String event = event();
                        if (!alphabet.contains(event)) throw error("Edge event absent from alphabet: " + event);
                        expect("goto"); targets.add(identifier());
                    }
                    expect(";");
                }
            }
            if (locations.isEmpty()) throw error("Expected at least one location");
            for (String target : targets) {
                if (!locations.contains(target)) throw error("Unknown goto target " + target);
            }
            int bodyEnd = tokens.get(pos).start;
            expect("end");
            if (pos != tokens.size()) throw error("Unexpected content after automaton");
            return new Document(events, alphabet, plant, source.substring(bodyStart, bodyEnd));
        }

        // Explorer state metadata uses identifier/string pairs, not executable references.
        // Preserve it literally even when its historical source name is 'statespace'.
        private void annotation() {
            expect("state"); expect("(");
            do {
                identifier(); expect(":");
                if (!peek().startsWith("\"")) throw error("Expected string in @state annotation");
                pos++;
            } while (accept(","));
            expect(")");
        }
    }
}
