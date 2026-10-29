# CIF3Connector

**CIF3Connector** bridges the **Contract Automata Library (CATLib)** and the
**Eclipse Supervisory Control Engineering Toolkit (ESCET)**. It translates
contract automata into CIF3 and enables redundant orchestration synthesis:
CATLib and CIF3 independently synthesize orchestrations, whose languages can
then be compared to cross-validate the results.

## Features

- **Translation:** Converts CATLib contract automata (`.data`) into CIF3 (`.cif`).
- **Redundant orchestration synthesis:** Supports synthesis using CATLib and CIF3.
- **Cross-validation:** Prepares and checks language equivalence between synthesized orchestrations.
- **Automated experiment reproduction:** Runs the complete workflow for a precomputed composition.

## Prerequisites

- **Java 17 or later:** Required to run `CIF3Connector-1.0-SNAPSHOT.jar`.
- **Eclipse ESCET (CIF3) v10:** Required for CIF synthesis, exploration, and language-equivalence checking.

## General-purpose CIF3Connector usage

```text
java -jar CIF3Connector-1.0-SNAPSHOT.jar -i <input1.data> [<input2.data> ...] [-o <composition.cif> <orchestration.cif>] [-a]
```

- **`-i`:** Required input contract automata in `.data` format.
- **`-o`:** Optional output CIF filenames, in composition/orchestration order.
  Defaults: `Composition.cif` and `Orchestration.cif`.
- **`-a`:** Also exports the composition and orchestration as `.data` files using
  the corresponding output stems.

With **multiple principal inputs**, the tool encodes lazy necessary transitions
where needed, composes the principals in the supplied order, synthesizes the
CATLib orchestration, and translates both composition and orchestration into
CIF3. Encoding splits each lazy transition into an uncontrollable internal
selection followed by a controllable execution. Final states become marked CIF
locations.

For example, compose three principals and choose output filenames:

```text
java -jar CIF3Connector-1.0-SNAPSHOT.jar -i Player.data Player.data Dealer.data  -o CardComposition.cif CardOrchestration.cif
```

With **one precomputed composition**, the tool skips principal encoding and
composition, translates the input composition, applies CATLib synthesis, and translates the synthesized orchestration into CIF3 format. The precomputed composition must not contain lazy transitions.

```text
java -jar CIF3Connector-1.0-SNAPSHOT.jar -i CardComposition.data
```

This CLI performs CATLib synthesis and CIF3 translation; the reproduction workflows below also
run the ESCET tools.

## Automated experiment reproduction

`CIF3ConnectorExperiments` is a convenience tool for reproducing synthesis and
language-equivalence checks on the unmodified composition.

For convenience, the Zenodo artifact distributes the JAR and the example `.data` 
files in the same directory, but they may also be located in different directories 
by specifying their paths in the command.

The generated CIF files are written to the current working directory (the 
directory from which the command is executed). Run the experiments from a 
directory that does not already contain the generated output files, since 
the automated tool does not overwrite existing files.


```text
java -cp CIF3Connector-1.0-SNAPSHOT.jar io.github.contractautomata.CIF3Connector.CIF3ConnectorExperiments <composition.data> <escet-bin-directory>
```

Replace the placeholders with an existing readable composition and the ESCET
`bin` directory. Quote paths containing spaces. The input basename must contain
`Composition`; output names replace it with `Orchestration` or `Equivalence`
where appropriate, preserving suffixes. The ESCET directory must contain
`cifdatasynth`, `cifexplorer`, and `ciflngeqv` (the `.cmd` versions on Windows).

The tool automatically performs:

1. CATLib orchestration synthesis and CIF3 translation of the composition and orchestration.
2. CIF data synthesis (`cifdatasynth`) on the composition CIF.
3. CIF state-space exploration (`cifexplorer`) of the controlled system.
4. Equivalence comparison preparation (`CIF3EquivalencePreparation`).
5. Language-equivalence checking (`ciflngeqv`).

It prints state and transition counts for the input composition and CATLib
orchestration, separate translation and CATLib MPC synthesis timings, and
separate timings for CIF synthesis, exploration, comparison preparation, and
equivalence checking, all in milliseconds. ESCET output, including warnings and
counterexamples, is displayed directly. Process failures stop the pipeline;
a successful checker exit alone does not mean that the languages are equal.
The comparison preparation uses the default strict controllability policy.

### Card

```text
java -cp CIF3Connector-1.0-SNAPSHOT.jar io.github.contractautomata.CIF3Connector.CIF3ConnectorExperiments CardComposition.data "<escet-bin-directory>"
```

The canonical Card composition uses principal order **Player, Player, Dealer**.

### Railway

```text
java -cp CIF3Connector-1.0-SNAPSHOT.jar io.github.contractautomata.CIF3Connector.CIF3ConnectorExperiments RailComposition.data "<escet-bin-directory>"
```

The five generated files are:

| Output | Card | Railway |
| --- | --- | --- |
| Composition | `CardComposition.cif` | `RailComposition.cif` |
| CATLib orchestration | `CardOrchestration.cif` | `RailOrchestration.cif` |
| CIF controlled system | `CardComposition.ctrlsys.cif` | `RailComposition.ctrlsys.cif` |
| CIF state-space orchestration | `CardComposition.ctrlsys_statespace.cif` | `RailComposition.ctrlsys_statespace.cif` |
| Equivalence comparison | `CardEquivalence.cif` | `RailEquivalence.cif` |

For both original experiments, the expected checker result is
`Automata have the same language.`

## Manual experiment reproduction

Each stage can also be executed independently, without
`CIF3ConnectorExperiments`. The commands below assume that the JAR and required
example inputs are in the same working directory, with the ESCET `bin` directory
on `PATH`. The example inputs and CIF fixtures are available in
`src/main/resources/` and can be copied to a working directory for reproduction,
leaving the repository resources unchanged. Standalone commands write the output
filenames shown and replace existing files with those names.

### Card

```text
java -jar CIF3Connector-1.0-SNAPSHOT.jar -i CardComposition.data -o CardComposition.cif CardOrchestration.cif
cifdatasynth CardComposition.cif
cifexplorer CardComposition.ctrlsys.cif
java -cp CIF3Connector-1.0-SNAPSHOT.jar io.github.contractautomata.CIF3Connector.CIF3EquivalencePreparation CardOrchestration.cif CardComposition.ctrlsys_statespace.cif CardEquivalence.cif
ciflngeqv CardEquivalence.cif
```

### Railway

```text
java -jar CIF3Connector-1.0-SNAPSHOT.jar -i RailComposition.data -o RailComposition.cif RailOrchestration.cif
cifdatasynth RailComposition.cif
cifexplorer RailComposition.ctrlsys.cif
java -cp CIF3Connector-1.0-SNAPSHOT.jar io.github.contractautomata.CIF3Connector.CIF3EquivalencePreparation RailOrchestration.cif RailComposition.ctrlsys_statespace.cif RailEquivalence.cif
ciflngeqv RailEquivalence.cif
```

Both sequences should report `Automata have the same language.` If using the
supplied CIF orchestrations after synthesis and exploration have already been
performed, run only the final preparation and checking commands.

### Card fault injection (manual experiment)

The Card fault-injection experiment is reproduced manually using the individual
CIF3Connector and ESCET commands. It deliberately modifies the controllability of selected actions in the CIF3
model produced by translation to demonstrate discrepancy detection.
It is separate from `CIF3ConnectorExperiments`, whose automated reproduction
pipeline operates on the unmodified model.

The supplied `CardComposition_mutated.cif` reverses the controllability
declarations of 22 actions: 14 from `controllable` to `uncontrollable` and eight
in the opposite direction. Action names, states, transitions and all other model
content remain unchanged. The original `CardComposition.cif` is also supplied
for inspection of the mutation.

The Card procedure above provides the original CATLib orchestration
`CardOrchestration.cif` and the successful language-equivalence check. With the
JAR and supplied `CardComposition_mutated.cif` in the same working directory,
the following commands synthesise the CIF3 orchestration from the modified
composition and compare it with that original CATLib orchestration.
The original `CardOrchestration.cif` is used unchanged, and all generated outputs
have distinct filenames.

```text
cifdatasynth CardComposition_mutated.cif
cifexplorer CardComposition_mutated.ctrlsys.cif
java -cp CIF3Connector-1.0-SNAPSHOT.jar io.github.contractautomata.CIF3Connector.CIF3EquivalencePreparation --ignore-controllability-conflicts CardOrchestration.cif CardComposition_mutated.ctrlsys_statespace.cif CardEquivalence_mutated.cif
ciflngeqv CardEquivalence_mutated.cif
```

The mutation introduces conflicting controllability declarations when preparing
the comparison. `--ignore-controllability-conflicts` permits the comparison file
to be generated, retaining the original CATLib classifications for shared
actions. Actions declared only in the second input retain their own
classifications. The transitions of both automata remain unchanged.

The mutated CIF3 orchestration contains 97 states and 118 transitions, and the
checker reports `Automata have a different language!` The distinguishing action
`Aut.c_pair3_match_2_3` can be performed from the initial state of the mutated
orchestration but is unavailable in the original CATLib orchestration. ESCET 10
returns exit code 1 for this non-equivalence result.

Alternatively, open a generated comparison file in ESCET and choose **CIF
synthesis tools > Event-based synthesis tools > Apply language equivalence
check**. See the [ESCET language equivalence documentation](https://eclipse.dev/escet/cif/tools/eventbased/language-equivalence-check.html).

## Equivalence preparation reference

`CIF3EquivalencePreparation` only prepares the comparison file. It does not run
synthesis, exploration, or language-equivalence checking.

### Arguments

```text
java -cp CIF3Connector-1.0-SNAPSHOT.jar io.github.contractautomata.CIF3Connector.CIF3EquivalencePreparation [--ignore-controllability-conflicts] <catlib-orchestration.cif> <cif3-orchestration.cif> <equivalence.cif>
```

Replace the angle-bracket placeholders with filenames:

1. **CATLib orchestration:** the orchestration synthesized by CATLib and translated
   into CIF format by CIF3Connector, for example `CardOrchestration.cif`.
2. **CIF3 orchestration:** the result of running `cifdatasynth` on the CIF
   representation of the composition generated by CIF3Connector, followed by
   `cifexplorer` on the synthesized controlled system, for example
   `CardComposition.ctrlsys_statespace.cif`.
3. **Equivalence file:** the merged CIF file produced by
   `CIF3EquivalencePreparation`, containing both automata and intended as input
   to the ESCET language-equivalence checker `ciflngeqv`.

### Controllability, alphabets, and warnings

Controllability conflicts are errors by default.
`--ignore-controllability-conflicts` converts conflicts into warnings and retains
the classification from the first input. Events declared only in the second
input retain their own classification. The flag is useful for the
controllability-mutation experiment and is not normally needed for the original
Card and Railway experiments.

The output has a single `group Aut`, containing the union of declared events,
and automata named `statespace` and `statespace2`. Each retains its original kind
(`plant automaton` or `automaton`). Both receive the union of the explicit input
alphabets. Events declared but absent from both alphabets remain declared without
being added to those alphabets. Unused events may therefore produce harmless
warnings from `ciflngeqv`, as observed for `Aut.u_sink_off_2` in Railway.

All location bodies are copied verbatim, including annotations, initial/marked
flags, edges, and comments. In particular, an edge unique to one input remains
unique to that automaton. Unused-alphabet-event warnings are expected and are not
suppressed. Historical names inside `@state` metadata remain unchanged.

### Supported input structure and error handling

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

## Developer testing

### Dependency-free Java regression harness

These commands are for developers with a source checkout, run from the repository
root using Java 17. They are separate from Zenodo artifact reproduction:

```text
javac --release 17 -d target/equivalence-tests/classes src/main/java/io/github/contractautomata/CIF3Connector/CIF3EquivalencePreparation.java src/test/java/io/github/contractautomata/CIF3Connector/CIF3EquivalencePreparationTest.java
java -cp target/equivalence-tests/classes io.github.contractautomata.CIF3Connector.CIF3EquivalencePreparationTest
```

This is an explicitly invoked Java test harness, not a JUnit/Surefire suite. It
requires no new dependencies. It checks a synthetic extra-edge mutation, alphabet
and declaration unions, conflicts and unsupported syntax, exact preservation of
the automaton bodies, input protection, and the two original Card files. It also
compares shared event declarations with the manually prepared Card comparison
`TwoCardOrchestrations.cif`; that file's alphabet is not used as the expected union.

The test produces `target/equivalence-tests/CardEquivalence.cif`. The merger and
its tests check preparation and preservation, not language equivalence; use
`ciflngeqv` separately as shown in the manual reproduction instructions.

---

*This tool is part of the research presented in "Redundant Orchestration Synthesis for Contract Automata with CATLib and CIF3".*
