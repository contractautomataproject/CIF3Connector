package io.github.contractautomata.CIF3Connector;

import io.github.contractautomata.catlib.automaton.Automaton;
import io.github.contractautomata.catlib.automaton.label.CALabel;
import io.github.contractautomata.catlib.automaton.label.action.Action;
import io.github.contractautomata.catlib.automaton.label.action.OfferAction;
import io.github.contractautomata.catlib.automaton.state.BasicState;
import io.github.contractautomata.catlib.automaton.state.State;
import io.github.contractautomata.catlib.automaton.transition.ModalTransition;
import io.github.contractautomata.catlib.converters.AutDataConverter;
import io.github.contractautomata.catlib.operations.MSCACompositionFunction;
import io.github.contractautomata.catlib.operations.MpcSynthesisOperator;
import io.github.contractautomata.catlib.requirements.StrongAgreement;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.github.contractautomata.catlib.automaton.transition.ModalTransition.Modality.URGENT;

/**
 * This class computes the compositions and orchestrations for the card and railway examples,
 * and exports them to CIF3 format.
 * The encoding from semi-controllable to uncontrollable-controllable (Splitting Orchestration) is explicitly handled in the class.
 * In the railway example case, it also computes the forbidden states.
 */
public class CIF3ConnectorExamples {

    private static final AutDataConverter<CALabel> bdc = new AutDataConverter<>(CALabel::new);
//    private static final String dir = System.getProperty("user.dir")+ File.separator+"src"+File.separator+"main"+File.separator+"resources"+File.separator;

    private static final String dir = "";

    public static void main(String[] args){
        System.out.println("CIF3 connector Examples");
        try {
            computeClientServiceExample();
            computeCompositionCardExample();
            computeCompositionRailwayExample();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void computeClientServiceExample() throws IOException {
//        Automaton<String, Action, State<String>, ModalTransition<String,Action,State<String>, CALabel>> client = bdc.importMSCA(dir + "client.data");
//        Automaton<String,Action,State<String>,ModalTransition<String,Action,State<String>,CALabel>> service = bdc.importMSCA(dir + "service.data");
        Automaton<String, Action, State<String>, ModalTransition<String,Action,State<String>, CALabel>> client = loadFile("client.data");
        Automaton<String,Action,State<String>,ModalTransition<String,Action,State<String>,CALabel>> service = loadFile("service.data");

        //compose encoded principals
        Automaton<String, Action, State<String>, ModalTransition<String, Action, State<String>, CALabel>> comp =
                new MSCACompositionFunction<>(CIF3Connector.encodePrincipals(List.of(client,service)), t-> new StrongAgreement().negate().test(t.getLabel())).apply(Integer.MAX_VALUE);

        bdc.exportMSCA(dir+"ClientServiceComposition.data",comp);
        exportToCif(comp,"ClientServiceComposition.cif");

        Automaton<String,Action,State<String>,ModalTransition<String,Action,State<String>,CALabel>> mpc=new MpcSynthesisOperator<String>(new StrongAgreement()).apply(comp);

        bdc.exportMSCA(dir+"ClientServiceOrchestration.data",mpc);
        exportToCif(mpc,"ClientServiceOrchestration.cif");

    }

    private static void computeCompositionCardExample() throws IOException {
//        Automaton<String, Action, State<String>, ModalTransition<String,Action,State<String>, CALabel>> dealer = bdc.importMSCA(dir + "Dealer.data");
//        Automaton<String,Action,State<String>,ModalTransition<String,Action,State<String>,CALabel>> player = bdc.importMSCA(dir + "Player.data");

        Automaton<String, Action, State<String>, ModalTransition<String,Action,State<String>, CALabel>> dealer = loadFile("Dealer.data");
        Automaton<String,Action,State<String>,ModalTransition<String,Action,State<String>,CALabel>> player = loadFile("Player.data");


        //compose encoded principals
        Automaton<String, Action, State<String>, ModalTransition<String, Action, State<String>, CALabel>> comp =
                new MSCACompositionFunction<>(CIF3Connector.encodePrincipals(List.of(dealer,player,player)), t-> new StrongAgreement().negate().test(t.getLabel())).apply(Integer.MAX_VALUE);

        bdc.exportMSCA(dir+"CardComposition.data",comp);
        exportToCif(comp,"CardComposition.cif");

        Automaton<String,Action,State<String>,ModalTransition<String,Action,State<String>,CALabel>> mpc=new MpcSynthesisOperator<String>(new StrongAgreement()).apply(comp);

        bdc.exportMSCA(dir+"CardOrchestration.data",mpc);
        exportToCif(mpc,"CardOrchestration.cif");

    }

    private static void exportToCif(Automaton<String, Action, State<String>, ModalTransition<String,Action,State<String>, CALabel>> aut, String filename) throws IOException {
        String content = CIF3Connector.contractAutomatonToCIF3(aut);
        Path filePath = Path.of(dir+filename);
        Files.writeString(filePath, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }


    private static void computeCompositionRailwayExample() throws IOException {
//        Automaton<String, Action, State<String>, ModalTransition<String,Action,State<String>, CALabel>> train1 = bdc.importMSCA(dir + "train1.data");
//        Automaton<String, Action, State<String>, ModalTransition<String,Action,State<String>, CALabel>> train2 = bdc.importMSCA(dir + "train2.data");
//        Automaton<String,Action,State<String>,ModalTransition<String,Action,State<String>,CALabel>> driver = bdc.importMSCA(dir + "driver.data");
//        Automaton<String, Action, State<String>, ModalTransition<String,Action,State<String>,CALabel>> semaphoreContr = bdc.importMSCA(dir + "semaphoreContr.data");
//        Automaton<String, Action, State<String>, ModalTransition<String,Action,State<String>,CALabel>> semaphore = bdc.importMSCA(dir + "semaphore.data");

        Automaton<String, Action, State<String>, ModalTransition<String,Action,State<String>, CALabel>> train1 = loadFile("train1.data");
        Automaton<String, Action, State<String>, ModalTransition<String,Action,State<String>, CALabel>> train2 = loadFile("train2.data");
        Automaton<String,Action,State<String>,ModalTransition<String,Action,State<String>,CALabel>> driver = loadFile("driver.data");
        Automaton<String, Action, State<String>, ModalTransition<String,Action,State<String>,CALabel>> semaphoreContr = loadFile("semaphoreContr.data");
        Automaton<String, Action, State<String>, ModalTransition<String,Action,State<String>,CALabel>> semaphore = loadFile("semaphore.data");

        List<Automaton<String, Action, State<String>, ModalTransition<String,Action,State<String>,CALabel>>> list = List.of(train1,train2,driver,semaphoreContr,semaphore);

        Automaton<String, Action, State<String>, ModalTransition<String, Action, State<String>, CALabel>> comp =
                new MSCACompositionFunction<>(CIF3Connector.encodePrincipals(list), t-> new StrongAgreement().negate().test(t.getLabel())).apply(Integer.MAX_VALUE);

        //standard synthesis does not take a forbidden predicate, therefore I add to all forbidden states an uncontrollable transition to a sink state
        Predicate<State<String>> badState = getBadStatePredicate();

        State<String> sink = new State<>(IntStream.range(0,comp.getRank())
                .mapToObj(i->new BasicState<>("sink",false,false,false))
                .collect(Collectors.toList()));

        CALabel lab = new CALabel(comp.getRank(),1,new OfferAction("sink"));

        Set<State<String>> badStates = comp.getStates().parallelStream()
                .filter(badState)
                .collect(Collectors.toSet());

        Set<ModalTransition<String, Action, State<String>, CALabel>> sinkTransitions = badStates.stream()
                //.peek(s->{ if (s.isFinalState()) System.out.println("final");})
                .map(s->new ModalTransition<String, Action, State<String>, CALabel>(s,lab,sink, URGENT))
                .collect(Collectors.toSet());

        sinkTransitions.addAll(comp.getTransition());
        comp = new Automaton<>(sinkTransitions);

        bdc.exportMSCA(dir+"RailComposition.data",comp);
        exportToCif(comp,"RailComposition.cif");

        Automaton<String,Action,State<String>,ModalTransition<String,Action,State<String>,CALabel>> mpc=new MpcSynthesisOperator<String>(new StrongAgreement()).apply(comp);

        bdc.exportMSCA(dir+"RailOrchestration.data",mpc);
        exportToCif(mpc,"RailOrchestration.cif");

    }


    private static Predicate<State<String>> getBadStatePredicate() {
        BiFunction<State<String>, Integer, Integer> getX =  (s, i) ->
                Integer.parseInt(s.getState().get(i).getState().split(";")[0].substring(1));


        final int junctionStartAt = 4;

        final int junctionStopAt = 4;

        final int semaphoreX = 2;


        return s-> {
            int xt0 = getX.apply(s,0);
            int xt1 = getX.apply(s,1);
            boolean train1insideJunction = xt0>=junctionStartAt && xt0<=junctionStopAt;
            boolean train2insideJunction = xt1>=junctionStartAt && xt1<=junctionStopAt;
            boolean train1AtSemaphore = xt0>=semaphoreX-1 && xt0<=semaphoreX+1;
            boolean train2AtSemaphore = xt1>=semaphoreX-1 && xt1<=semaphoreX+1;
            boolean semaphoreOpen = s.getState().get(3).getState().contains("Open");
            boolean semaphoreClose = s.getState().get(3).getState().contains("Close");

            return
                    //two agents on the same cell
                    (s.getState().get(0).getState().equals(s.getState().get(1).getState())&&!s.getState().get(0).getState().contains("OUT"))
                            ||
                            IntStream.range(0,2)
                                    .mapToObj(i->getX.apply(s,i))
                                    .anyMatch(x->x==semaphoreX && semaphoreClose)  //either train is traversing the semaphore whilst is closed
                            || (train1insideJunction && train2insideJunction) //both trains inside the junction
                            || ((train1insideJunction || train2insideJunction) && semaphoreOpen) //semaphore must be closed when a train is inside the junction
                            || (!train1AtSemaphore && !train2AtSemaphore && semaphoreOpen);//the semaphore is open only when a train is near it
        };
    }

    /**
     * load automata as package resource
     */
    private static Automaton<String, Action, State<String>, ModalTransition<String, Action, State<String>, CALabel>> loadFile(String filename) throws IOException {
        AutDataConverter<CALabel> adc = new AutDataConverter<>(CALabel::new);
        InputStream in = CIF3ConnectorExamples.class.getClassLoader().getResourceAsStream(filename);
        File f = new File(filename);
        FileUtils.copyInputStreamToFile(in, f);
        Automaton<String, Action, State<String>, ModalTransition<String, Action, State<String>, CALabel>>  aut = adc.importMSCA(filename);
        f.delete();
        return aut;
    }
}
