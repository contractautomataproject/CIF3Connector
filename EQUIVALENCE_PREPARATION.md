# Preparing a CIF language comparison

`CIF3EquivalencePreparation` accepts exactly three paths: the CATLib orchestration,
the explored CIF3 orchestration, and the destination. It does not invoke CIF tools.
After building the project, run (substitute the actual JAR path):

```text
java -cp target/CIF3Connector-1.0-SNAPSHOT.jar io.github.contractautomata.CIF3Connector.CIF3EquivalencePreparation [--ignore-controllability-conflicts] catlib.cif cif3.cif comparison.cif
```

The output has a single `group Aut`, containing the union of declared events, and
automata named `statespace` and `statespace2`. Each retains its original kind
(`plant automaton` or `automaton`). Both receive the union of the explicit input
alphabets. Controllability conflicts are errors by default. With `--ignore-controllability-conflicts`, conflicts are reported as warnings and the first input classification is retained. Events declared but absent from
both alphabets remain declared without being added to those alphabets.

All location bodies are copied verbatim, including annotations, initial/marked
flags, edges, and comments. In particular, an edge unique to one input remains
unique to that automaton. Unused-alphabet-event warnings are expected and are not
suppressed. Historical names inside `@state` metadata remain unchanged.

## Supported input structure

The lightweight parser supports the structure in the supplied connector and
explorer files, independently of their number of events, locations, and edges:

- Exactly one `group Aut`, followed by exactly one automaton.
- Untyped `controllable` and `uncontrollable` declarations, including comma lists.
- An explicit alphabet of qualified `Aut.event` names; empty alphabets are accepted.
- Plain ASCII identifiers, named locations, `initial;`, `marked;`, and
  `edge Aut.event goto location;` statements.
- Location `@state` annotations containing identifier/string pairs.
- Whitespace, multiline lists, `//` comments, and `/* ... */` comments.

Other structures are rejected: additional groups or automata, variables, guards,
updates, typed events, other annotation forms, and implicit alphabets. Duplicate
locations, unresolved goto targets, undeclared alphabet events, and edges absent
from the explicit alphabet are errors. The parser is not a full CIF validator;
determinism, initial-location requirements and other tool-specific semantic checks
remain the responsibility of CIF. It never repairs or determinizes an input.

Both inputs are parsed before writing. Output paths that alias either input
(including existing symbolic or hard links) are rejected. Other existing output
files are overwritten; the destination directory must already exist. Invalid
arguments exit with status 2; parsing and I/O errors exit with status 1.

## Dependency-free automated tests

From the repository root, using Java 17:

```text
javac --release 17 -d target/equivalence-tests/classes src/main/java/io/github/contractautomata/CIF3Connector/CIF3EquivalencePreparation.java src/test/java/io/github/contractautomata/CIF3Connector/CIF3EquivalencePreparationTest.java
java -cp target/equivalence-tests/classes io.github.contractautomata.CIF3Connector.CIF3EquivalencePreparationTest
```

This is an explicitly invoked Java test harness, not a JUnit/Surefire suite. It
requires no new dependencies. It checks a synthetic extra-edge mutation, alphabet
and declaration unions, conflicts and unsupported syntax, exact preservation of
the automaton bodies, input protection, and the two original Card files. It also
compares shared event declarations with `TwoCardOrchestrations.cif`; that manual file's
alphabet is not used as the expected union.

The test produces `target/equivalence-tests/CardEquivalence.cif`. With ESCET
installed, execute the final check from its command-line environment:

```text
ciflngeqv target/equivalence-tests/CardEquivalence.cif
```

Alternatively, open the generated file in ESCET and choose **CIF synthesis tools >
Event-based synthesis tools > Apply language equivalence check**. See the
[ESCET language equivalence documentation](https://eclipse.dev/escet/cif/tools/eventbased/language-equivalence-check.html).
For mutation-testing experiments, use for example:

```text
java -cp target/CIF3Connector-1.0-SNAPSHOT.jar io.github.contractautomata.CIF3Connector.CIF3EquivalencePreparation --ignore-controllability-conflicts catlib.cif cif3-mutated.cif comparison-mutated.cif
``

The merger and its tests check preparation and preservation, not language equivalence.
