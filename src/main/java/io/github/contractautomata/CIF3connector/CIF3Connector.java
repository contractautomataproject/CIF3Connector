package io.github.contractautomata.CIF3connector;

import io.github.contractautomata.catlib.automaton.Automaton;
import io.github.contractautomata.catlib.automaton.label.CALabel;
import io.github.contractautomata.catlib.automaton.label.action.Action;
import io.github.contractautomata.catlib.automaton.label.action.IdleAction;
import io.github.contractautomata.catlib.automaton.label.action.TauAction;
import io.github.contractautomata.catlib.automaton.state.AbstractState;
import io.github.contractautomata.catlib.automaton.state.BasicState;
import io.github.contractautomata.catlib.automaton.state.State;
import io.github.contractautomata.catlib.automaton.transition.ModalTransition;
import io.github.contractautomata.catlib.converters.AutDataConverter;
import io.github.contractautomata.catlib.operations.MSCACompositionFunction;
import io.github.contractautomata.catlib.operations.MpcSynthesisOperator;
import io.github.contractautomata.catlib.requirements.StrongAgreement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.github.contractautomata.catlib.automaton.transition.ModalTransition.Modality.PERMITTED;
import static io.github.contractautomata.catlib.automaton.transition.ModalTransition.Modality.URGENT;

/**
 * This is the tool for translating contract automata into CIF3 automata.
 * The tool takes in input a list of principal automata, computes their composition and the corresponding orchestration (MPC), both translated into CIF3 format.
 * In case of a single principal automaton in input, it is treated as it this is already the composed automaton.
 */
public class CIF3Connector {
    private static final AutDataConverter<CALabel> bdc = new AutDataConverter<>(CALabel::new);


    public static void main(String[] args) {
        System.out.println("Usage: java -jar <jarfile> -i <input1.data> [<input2.data> ...] [-o <composition.cif> <orchestration.cif>]");
        System.out.println("  -i: List of input automata files (.data).");
        System.out.println("  -o: (Optional) Output CIF filenames for composition and orchestration.");
        System.out.println("      Defaults: Composition.cif and Orchestration.cif");
        System.out.println("Examples:");
        System.out.println("  java -jar CIF3connector-1.0-SNAPSHOT.jar -i Dealer.data Player.data -o CardComposition.cif CardOrchestration.cif");
        System.out.println("  java -jar tool.jar -i CardComposition.data");

        List<String> inputFiles = new ArrayList<>();
        String compCif = "Composition.cif";
        String orchCif = "Orchestration.cif";

        // Simple argument parsing
        for (int i = 0; i < args.length; i++) {
            if ("-i".equals(args[i]) && i + 1 < args.length) {
                i++;
                while (i < args.length && !args[i].startsWith("-")) {
                    if (args[i].endsWith(".data")) inputFiles.add(args[i]);
                    i++;
                }
                i--;
            } else if ("-o".equals(args[i]) && i + 1 < args.length) {
                i++;
                if (i < args.length && args[i].endsWith(".cif")) compCif = args[i++];
                if (i < args.length && args[i].endsWith(".cif")) orchCif = args[i];
            }
        }

        if (inputFiles.isEmpty()) {
            System.err.println("No input files specified.");
            return;
        }

        try {
            if (inputFiles.size() == 1) {
                // Only one automaton: treat as already composed
                var aut = bdc.importMSCA(inputFiles.get(0));
                if (aut.getTransition().parallelStream().anyMatch(ModalTransition::isLazy))
                    throw new RuntimeException("The provided composed automaton contains lazy transitions. ");
                exportToCif(aut, compCif);

                var mpc = new MpcSynthesisOperator<String>(new StrongAgreement()).apply(aut);
                exportToCif(mpc, orchCif);
            } else {
                // Multiple automata: compose, then synthesize
                List<Automaton<String, Action, State<String>, ModalTransition<String, Action, State<String>, CALabel>>> automata =
                        inputFiles.stream().map(f -> {
                            try {
                                return bdc.importMSCA(f);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }).collect(Collectors.toList());

                var comp = new MSCACompositionFunction<>(
                        encodePrincipals(automata),
                        t -> new StrongAgreement().negate().test(t.getLabel())
                ).apply(Integer.MAX_VALUE);

                exportToCif(comp, compCif);

                var mpc = new MpcSynthesisOperator<String>(new StrongAgreement()).apply(comp);
                exportToCif(mpc, orchCif);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private static void exportToCif(Automaton<String, Action, State<String>, ModalTransition<String,Action,State<String>, CALabel>> aut, String filename) throws IOException {
        String content = contractAutomatonToCIF3(aut);
        Path filePath = Path.of(filename);
        Files.writeString(filePath, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public static String contractAutomatonToCIF3(Automaton<String, Action, State<String>, ModalTransition<String,Action,State<String>, CALabel>> aut) {
        StringBuilder cif = new StringBuilder();
        // Collect actions for group section
        java.util.Set<String> controllable = new java.util.LinkedHashSet<>();
        java.util.Set<String> uncontrollable = new java.util.LinkedHashSet<>();
        // Map states to location names
        java.util.Map<State<String>, String> stateLoc = new java.util.LinkedHashMap<>();
        int locIdx = 1;
        for (State<String> s : aut.getStates()) {
            stateLoc.put(s, "loc" + locIdx++);
        }
        // Collect transitions
        java.util.List<ModalTransition<String,Action,State<String>,CALabel>> transitions = new java.util.ArrayList<>(aut.getTransition());
        // Group section
        cif.append("group Aut:\n");
        for (ModalTransition<String,Action,State<String>,CALabel> t : transitions) {
            String actionName = getCIF3ActionName(t);
            if (t.isNecessary()) {
                uncontrollable.add(actionName);
            } else if (t.isPermitted()) {
                controllable.add(actionName);
            }
        }
        for (String c : controllable) cif.append("  controllable ").append(c.substring(4)).append(";\n");
        for (String u : uncontrollable) cif.append("  uncontrollable ").append(u.substring(4)).append(";\n");
        cif.append("end\n");
        // Alphabet
        cif.append("plant automaton statespace:\n  alphabet ");
        java.util.List<String> allActions = new java.util.ArrayList<>();
        allActions.addAll(controllable);
        allActions.addAll(uncontrollable);
        cif.append(String.join(", ", allActions)).append(";\n");
        // Locations
        for (State<String> s : aut.getStates()) {
            String loc = stateLoc.get(s);
            Set<ModalTransition<String,Action,State<String>,CALabel>> outgoingTransitions = transitions.parallelStream()
                    .filter(t->t.getSource().equals(s))
                    .collect(Collectors.toSet());
            cif.append("  @state(Aut: \"").append(stateToString(s)).append("\")\n  location ").append(loc);
            if (!s.isInitial()&&!s.isFinalState()&&outgoingTransitions.isEmpty())
                cif.append(";\n");
            else {
                cif.append(":\n");
                if (s.isInitial()) cif.append("    initial;\n");
                if (s.isFinalState()) cif.append("    marked;\n");
                // Edges
                for (ModalTransition<String,Action,State<String>,CALabel> t : outgoingTransitions) {
                    String edge = getCIF3ActionName(t);
                    String toLoc = stateLoc.get(t.getTarget());
                    cif.append("    edge ").append(edge).append(" goto ").append(toLoc).append(";\n");
                }
            }
        }
        cif.append("end\n");
        return cif.toString();
    }

    // Helper to convert a state to a comma-separated string
    private static String stateToString(State<String> s) {
        return s.getState().stream().map(AbstractState::getState).collect(java.util.stream.Collectors.joining(","));
    }

    // Helper to get the CIF3 action name for a transition
    private static String getCIF3ActionName(ModalTransition<String,Action,State<String>,CALabel> t) {
        CALabel label = t.getLabel();
        String prefix = t.isNecessary() ? "Aut.u_" : "Aut.c_";
        String action = (label.isTau())?label.getAction().toString():label.getAction().toString().substring(1);
        if (label.isMatch()) {
            return prefix + action + "_match_" + (label.getOfferer() + 1) + "_" + (label.getRequester() + 1);
        } else if (label.isRequest()) {
            return prefix + action + "_req_" + (label.getRequester() + 1);
        } else if (label.isOffer()) {
            return prefix + action + "_off_" + (label.getOfferer() + 1);
        } else if (label.isTau()) {
            return prefix + action + "_tau_" + (label.getTauMover() + 1);
        } else {
            // fallback for dummy or other actions
            throw new UnsupportedOperationException("Unsupported action type: " + label);
        }
    }


    //taken from CATLib
    public static  List<Automaton<String,Action,State<String>,ModalTransition<String, Action,State<String>,CALabel>>> encodePrincipals(List<Automaton<String,Action,State<String>,ModalTransition<String, Action,State<String>,CALabel>>> laut){
        return laut.stream()
                .map(aut->
                {
                    //each lazy transition is unfolded into two linked transitions, one uncontrollable and one controllable
                    Map<ModalTransition<String, Action, State<String>, CALabel>, List<ModalTransition<String, Action, State<String>, CALabel>>> map =
                            aut.getTransition().parallelStream()
                                    .collect(Collectors.toMap(t -> t, t -> {
                                        if (!t.getModality().equals(ModalTransition.Modality.LAZY))
                                            return List.of(t);
                                        else {
                                            List<Action> label = IntStream.range(0, t.getLabel().getRank())
                                                    .mapToObj(i -> new IdleAction())
                                                    .collect(Collectors.toList());

                                            List<BasicState<String>> intermediate = new ArrayList<>(t.getSource().getState());

                                            //the checks at the beginning of the method ensures that only necessary requests are lazy, and principals cannot have matches
                                            if (t.getLabel().isRequest()) {
                                                label.set(t.getLabel().getRequester(), new TauAction(t.getLabel().getAction().getLabel()));//the label cannot be a request
                                                String stateLabel = t.getSource().getState().get(t.getLabel().getRequester()).getState() + "_" + t.getLabel().getAction().getLabel() + "_" + t.getTarget().getState().get(t.getLabel().getRequester()).getState();
                                                intermediate.set(t.getLabel().getRequester(), new BasicState<>(stateLabel, false, false, false));
                                            }

                                            State<String> intermediateState = new State<>(intermediate);
                                            ModalTransition<String, Action, State<String>, CALabel> t1 = new ModalTransition<>(t.getSource(), new CALabel(label), intermediateState, URGENT);
                                            ModalTransition<String, Action, State<String>, CALabel> t2 = new ModalTransition<>(intermediateState, t.getLabel(), t.getTarget(), PERMITTED);
                                            return List.of(t1, t2);
                                        }
                                    }));

                    return new Automaton<>(
                            aut.getTransition().parallelStream()
                                    .flatMap(t -> map.get(t).stream())
                                    .collect(Collectors.toSet()));
                }).collect(Collectors.toList());
    }

}






//    public static String contractAutomatonToCIF3(Automaton<String,Action,State<String>,ModalTransition<String,Action,State<String>,CALabel>> aut){
//        StringBuilder cif = new StringBuilder();
//        cif.append("group Aut:\n");
//
//        aut.getTransition().parallelStream().filter(ModalTransition::isPermitted).collect(Collectors.toSet());
//
//        ModalTransition<String,Action,State<String>,CALabel> t = aut.getTransition().parallelStream().filter(ModalTransition::isPermitted).findFirst().orElse(null);
//
//        t.getLabel().getAction().toString();
//
//        t.getLabel().isMatch()
//        State<String> s = aut.getStates().stream().findFirst().orElse(null);
//
//        s.getState().get(0).getState()
//        return null;
//    }

//    public static String contractAutomatonToCIF3(String caSpec) {
//        StringBuilder cif = new StringBuilder();
//        List<String> controllable = new ArrayList<>();
//        List<String> uncontrollable = new ArrayList<>();
//        List<String> states = new ArrayList<>();
//        String initialState = "";
//        List<String> markedStates = new ArrayList<>();
//        List<String[]> transitions = new ArrayList<>();
//
//        for (String line : caSpec.split("\\R")) {
//            line = line.trim();
//            if (line.startsWith("Initial state:")) {
//                initialState = line.substring(line.indexOf('[')).replaceAll("[\\[\\]]", "").replaceAll("\\s+", "");
//            } else if (line.startsWith("Final states:")) {
//                String finals = line.substring(line.indexOf('['));
//                for (String s : finals.split("],")) {
//                    markedStates.add(s.replaceAll("[\\[\\]]", "").replaceAll("\\s+", ""));
//                }
//            } else if (line.startsWith("(") || line.startsWith("!U(")) {
//                boolean isUncontrollable = line.startsWith("!U");
//                String t = line.replace("!U", "").replaceAll("[()]", "");
//                String[] parts = t.split("\\],\\[");
//                String from = parts[0].replaceAll("[\\[\\]]", "").replaceAll("\\s+", "");
//                String label = parts[1].replaceAll("[\\[\\]]", "").replaceAll("\\s+", "");
//                String to = parts[2].replaceAll("[\\[\\]]", "").replaceAll("\\s+", "");
//                states.add(from);
//                states.add(to);
//                transitions.add(new String[]{from, label, to, isUncontrollable ? "u" : "c"});
//                // Collect actions for group
//                String[] acts = label.split(",");
//                if (acts.length == 2) {
//                    String actName = acts[0].replaceAll("[!?-]", "");
//                    String actType = acts[0].contains("!") ? "c" : acts[0].contains("?") ? "u" : "c";
//                    if (acts[0].contains("!") && acts[1].contains("?")) {
//                        String name = "c_" + actName + "_match_1_2";
//                        if (!controllable.contains(name)) controllable.add(name);
//                    } else if (acts[0].contains("?")) {
//                        String name = "u_" + actName + "_req_1";
//                        if (!uncontrollable.contains(name)) uncontrollable.add(name);
//                    } else if (!acts[0].equals("-")) {
//                        String name = (isUncontrollable ? "u_" : "c_") + actName + "_off_1";
//                        if (isUncontrollable) {
//                            if (!uncontrollable.contains(name)) uncontrollable.add(name);
//                        } else {
//                            if (!controllable.contains(name)) controllable.add(name);
//                        }
//                    }
//                }
//            }
//        }
//        // Remove duplicates
//        states = new ArrayList<>(new java.util.LinkedHashSet<>(states));
//        // Group section
//        cif.append("group Aut:\n");
//        for (String c : controllable) cif.append("  controllable ").append(c).append(";\n");
//        for (String u : uncontrollable) cif.append("  uncontrollable ").append(u).append(";\n");
//        cif.append("end\n");
//        // Plant automaton
//        cif.append("plant automaton statespace:\n  alphabet ");
//        cif.append(String.join(", ", controllable));
//        if (!uncontrollable.isEmpty()) cif.append(", ").append(String.join(", ", uncontrollable));
//        cif.append(";\n");
//        // Locations
//        int locIdx = 1;
//        java.util.Map<String, String> stateLoc = new java.util.HashMap<>();
//        for (String s : states) {
//            String loc = "loc" + locIdx++;
//            stateLoc.put(s, loc);
//            cif.append("  @state(Aut: \"").append(s).append("\")\n  location ").append(loc).append(":\n");
//            if (s.equals(initialState)) cif.append("    initial;\n");
//            if (markedStates.contains(s)) cif.append("    marked;\n");
//            // Edges will be added later
//        }
//        // Add edges
//        locIdx = 1;
//        for (String[] t : transitions) {
//            String fromLoc = stateLoc.get(t[0]);
//            String toLoc = stateLoc.get(t[2]);
//            String label = t[1];
//            String type = t[3];
//            String edge = "";
//            String[] acts = label.split(",");
//            if (acts.length == 2) {
//                if (acts[0].contains("!") && acts[1].contains("?")) {
//                    String actName = acts[0].replace("!", "");
//                    edge = (type.equals("c") ? "Aut.c_" : "Aut.u_") + actName + "_match_1_2";
//                } else if (acts[0].contains("?")) {
//                    String actName = acts[0].replace("?", "");
//                    edge = (type.equals("c") ? "Aut.c_" : "Aut.u_") + actName + "_req_1";
//                } else if (!acts[0].equals("-")) {
//                    String actName = acts[0].replaceAll("[!?]", "");
//                    edge = (type.equals("c") ? "Aut.c_" : "Aut.u_") + actName + "_off_1";
//                }
//            }
//            // Insert edge in the correct location
//            int idx = cif.indexOf("location " + fromLoc + ":");
//            if (idx != -1) {
//                int nextLoc = cif.indexOf("location ", idx + 1);
//                int insertPos = nextLoc == -1 ? cif.length() : nextLoc;
//                cif.insert(insertPos, "    edge " + edge + " goto " + toLoc + ";\n");
//            }
//        }
//        cif.append("end\n");
//        return cif.toString();
//    }
