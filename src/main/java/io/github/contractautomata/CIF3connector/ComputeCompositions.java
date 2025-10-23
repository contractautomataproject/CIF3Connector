package io.github.contractautomata.CIF3connector;

import io.github.contractautomata.catlib.automaton.Automaton;
import io.github.contractautomata.catlib.automaton.label.CALabel;
import io.github.contractautomata.catlib.automaton.label.action.Action;
import io.github.contractautomata.catlib.automaton.label.action.IdleAction;
import io.github.contractautomata.catlib.automaton.label.action.OfferAction;
import io.github.contractautomata.catlib.automaton.label.action.TauAction;
import io.github.contractautomata.catlib.automaton.state.BasicState;
import io.github.contractautomata.catlib.automaton.state.State;
import io.github.contractautomata.catlib.automaton.transition.ModalTransition;
import io.github.contractautomata.catlib.converters.AutDataConverter;
import io.github.contractautomata.catlib.operations.MSCACompositionFunction;
import io.github.contractautomata.catlib.operations.MpcSynthesisOperator;
import io.github.contractautomata.catlib.requirements.Agreement;
import io.github.contractautomata.catlib.requirements.StrongAgreement;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.github.contractautomata.CIF3connector.CIF3Connector.contractAutomatonToCIF3;
import static io.github.contractautomata.catlib.automaton.transition.ModalTransition.Modality.PERMITTED;
import static io.github.contractautomata.catlib.automaton.transition.ModalTransition.Modality.URGENT;
public class ComputeCompositions {

    private static final AutDataConverter<CALabel> bdc = new AutDataConverter<>(CALabel::new);
    private static final String dir = System.getProperty("user.dir")+ File.separator+"src"+File.separator+"main"+File.separator+"resources"+File.separator;

    public static void main(String[] args){
        System.out.println("CIF3 connector Example");
        try {
            computeCompositionCardExample();
            computeCompositionRailwayExample();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void computeCompositionCardExample() throws IOException {
        Automaton<String, Action, State<String>, ModalTransition<String,Action,State<String>, CALabel>> dealer = bdc.importMSCA(dir + "Dealer.data");
        Automaton<String,Action,State<String>,ModalTransition<String,Action,State<String>,CALabel>> player = bdc.importMSCA(dir + "Player.data");

        //compose encoded principals
        Automaton<String, Action, State<String>, ModalTransition<String, Action, State<String>, CALabel>> comp =
                new MSCACompositionFunction<>(encodePrincipals(List.of(dealer,player,player)), t-> new StrongAgreement().negate().test(t.getLabel())).apply(Integer.MAX_VALUE);

        bdc.exportMSCA(dir+"CardComposition.data",comp);
        exportToCif(comp,"CardComposition.cif");

        Automaton<String,Action,State<String>,ModalTransition<String,Action,State<String>,CALabel>> mpc=new MpcSynthesisOperator<String>(new StrongAgreement()).apply(comp);

        bdc.exportMSCA(dir+"CardOrchestration.data",mpc);
        exportToCif(mpc,"CardOrchestration.cif");

    }

    private static void exportToCif(Automaton<String, Action, State<String>, ModalTransition<String,Action,State<String>, CALabel>> aut, String filename) throws IOException {
        String content = contractAutomatonToCIF3(aut);
        Path filePath = Path.of(dir+filename);
        Files.writeString(filePath, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static void computeCompositionRailwayExample() throws IOException {
        Automaton<String, Action, State<String>, ModalTransition<String,Action,State<String>, CALabel>> train1 = bdc.importMSCA(dir + "train1.data");
        Automaton<String, Action, State<String>, ModalTransition<String,Action,State<String>, CALabel>> train2 = bdc.importMSCA(dir + "train2.data");
        Automaton<String,Action,State<String>,ModalTransition<String,Action,State<String>,CALabel>> driver = bdc.importMSCA(dir + "driver.data");
        Automaton<String, Action, State<String>, ModalTransition<String,Action,State<String>,CALabel>> semaphoreContr = bdc.importMSCA(dir + "semaphoreContr.data");
        Automaton<String, Action, State<String>, ModalTransition<String,Action,State<String>,CALabel>> semaphore = bdc.importMSCA(dir + "semaphore.data");
        List<Automaton<String, Action, State<String>, ModalTransition<String,Action,State<String>,CALabel>>> list = List.of(train1,train2,driver,semaphoreContr,semaphore);

        Automaton<String, Action, State<String>, ModalTransition<String, Action, State<String>, CALabel>> comp =
                new MSCACompositionFunction<>(encodePrincipals(list), t-> new StrongAgreement().negate().test(t.getLabel())).apply(Integer.MAX_VALUE);

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


    //taken from CATLib
    private static  List<Automaton<String,Action,State<String>,ModalTransition<String, Action,State<String>,CALabel>>> encodePrincipals(List<Automaton<String,Action,State<String>,ModalTransition<String, Action,State<String>,CALabel>>> laut){
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
