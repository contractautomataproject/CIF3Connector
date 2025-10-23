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
import io.github.contractautomata.catlib.requirements.StrongAgreement;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.github.contractautomata.CIF3connector.CIF3Connector.contractAutomatonToCIF3;
import static io.github.contractautomata.catlib.automaton.transition.ModalTransition.Modality.PERMITTED;
import static io.github.contractautomata.catlib.automaton.transition.ModalTransition.Modality.URGENT;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {

    private static final AutDataConverter<CALabel> bdc = new AutDataConverter<>(CALabel::new);
    private static final String dir = System.getProperty("user.dir")+ File.separator+"src"+File.separator+"main"+File.separator+"resources"+File.separator;

    public static void main(String[] args) throws IOException {
        railwayExample();
    }

    private static void cardExample() throws IOException {
        Automaton<String,Action,State<String>,ModalTransition<String,Action,State<String>,CALabel>> test1 = bdc.importMSCA(dir+ "CardComposition.data");
        String content = contractAutomatonToCIF3(test1);
        Path filePath = Path.of(dir+"CardComposition.cif");
        Files.writeString(filePath, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        Automaton<String,Action,State<String>,ModalTransition<String,Action,State<String>,CALabel>> test2 = bdc.importMSCA(dir+ "Table2_SplittingOrchestration.data");
        content = contractAutomatonToCIF3(test2);
        filePath = Path.of(dir+"Table2_SplittingOrchestration.cif");
        Files.writeString(filePath, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

    }

    private static void railwayExample() throws IOException {
        Automaton<String,Action,State<String>,ModalTransition<String,Action,State<String>,CALabel>> test1 = bdc.importMSCA(dir+ "RailComposition.data");
        String content = contractAutomatonToCIF3(test1);
        Path filePath = Path.of(dir+"RailComposition.cif");
        Files.writeString(filePath, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        Automaton<String,Action,State<String>,ModalTransition<String,Action,State<String>,CALabel>> test2 = bdc.importMSCA(dir+ "Table3_SplittingOrchestration.data");


        content = contractAutomatonToCIF3(test2);
        filePath = Path.of(dir+"Table3_SplittingOrchestration.cif");
        Files.writeString(filePath, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

    }
//        // Find the first pair of transitions with the same source and label
//        Optional<AbstractMap.SimpleEntry<
//                ModalTransition<String,Action,State<String>,CALabel>,
//                ModalTransition<String,Action,State<String>,CALabel>
//                >> pair = test1.getTransition().stream()
//                .flatMap(t1 -> test1.getTransition().stream()
//                        .filter(t2 -> t1 != t2 &&
//                                t1.getSource().equals(t2.getSource()) &&
//                                t1.getLabel().equals(t2.getLabel()))
//                        .map(t2 -> new AbstractMap.SimpleEntry<>(t1, t2)))
//                .findFirst();
//
//        if (pair.isPresent()) {
//            var t1 = pair.get().getKey();
//            var t2 = pair.get().getValue();
//            System.out.println("Found non-deterministic transitions:");
//            System.out.println("t1: " + t1);
//            System.out.println("t2: " + t2);
//        };
}

