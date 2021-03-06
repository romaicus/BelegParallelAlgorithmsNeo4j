package de.saschapeukert.algorithms.impl.connected.components;

import com.carrotsearch.hppc.LongHashSet;
import com.carrotsearch.hppc.LongLookupContainer;
import com.carrotsearch.hppc.cursors.LongCursor;
import de.saschapeukert.algorithms.abst.MyAlgorithmBaseCallable;
import de.saschapeukert.algorithms.impl.connected.components.search.BFS;
import de.saschapeukert.database.DBUtils;
import de.saschapeukert.datastructures.TarjanInfo;
import de.saschapeukert.Starter;
import org.neo4j.collection.primitive.PrimitiveLongIterator;
import org.neo4j.graphdb.Direction;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This class represents the single thread Strongly Connected Components Algorithm (Tarjan) using the Kernel API of Neo4j
 * <br>
 * Created by Sascha Peukert on 17.10.2015.
 */
public class STConnectedComponentsAlgo extends MyAlgorithmBaseCallable {

    long componentID= -1;
    final CCAlgorithmType myType;

    private Map<Long,TarjanInfo> nodeDictionary;
    private Stack<Long> stack;
    private int maxdfs=0;

    public static LongHashSet allNodes; // except the trivial CCs

    public STConnectedComponentsAlgo(CCAlgorithmType type, TimeUnit timeUnit, DBUtils db) {
        super(timeUnit, db);
        this.myType = type;

        componentID= db.highestNodeKey+1;

        if(myType== CCAlgorithmType.STRONG) {
            // initialize nodeDictionary for tarjans algo
            this.stack = new Stack<>();
            this.nodeDictionary = new HashMap<>();
        }
    }

    @Override
    public void work() {

        timer.start();
        this.tx = db.openTransaction();
        prepareAllNodes();
        if(myType== CCAlgorithmType.WEAK) {
            weakly();
        } else{
            strongly();
        }
        db.closeTransactionWithSuccess(tx);
        timer.stop();
    }

    void strongly(){

        Iterator<LongCursor> it = allNodes.iterator();
        while(it.hasNext()) {

            try {
                Long n = it.next().value;
                tarjan(n);

            } catch (NoSuchElementException e) {
                break;
            }
            it = allNodes.iterator();
        }
    }

    void weakly(){

        Iterator<LongCursor> it = allNodes.iterator();
        while(it.hasNext()){
            // Every node has to be marked as (part of) a component

            try {
                Long n = it.next().value;
                searchForWeakly(n);
                componentID++;

            }catch (NoSuchElementException e){
                break;
            }
            it = allNodes.iterator();
        }
    }

    void searchForWeakly(long n){
        LongHashSet reachableIDs = BFS.go(n, Direction.BOTH, db);

        registerCC(reachableIDs,componentID);
        removeFromAllNodes(reachableIDs);
    }

    private void prepareAllNodes(){

        allNodes = new LongHashSet();
        PrimitiveLongIterator it  =db.getPrimitiveLongIteratorForAllNodes();
        //ResourceIterator<Node> it = db.getIteratorForAllNodes();
        while(it.hasNext()){
            //Node n = it.next();
            Long n = it.next();
            trimOrAddToAllNodes(n);

            if(myType== CCAlgorithmType.STRONG)
                nodeDictionary.put(n,new TarjanInfo());
        }
        //it.close();

    }

    private void trimOrAddToAllNodes(Long n){
        if(this.myType==CCAlgorithmType.STRONG){
            if((db.getDegree(n,Direction.OUTGOING)==0) || db.getDegree(n,Direction.INCOMING)==0){
                // trivial CC
                Starter.putIntoResultCounter(n, new AtomicLong(componentID));
                componentID++;
            } else{
                allNodes.add(n);
                furtherInspectNodeWhileTrim(n);
            }
        } else{
            if(db.getDegree(n)==0){
                // trivial CC
                Starter.putIntoResultCounter(n, new AtomicLong(componentID));
                componentID++;
            } else{
                allNodes.add(n);
                furtherInspectNodeWhileTrim(n);
            }
        }
    }

    void furtherInspectNodeWhileTrim(Long n){
        // Overwrite this method to add code for trim()
    }

    public static Map<Integer, List<Long>> getMapofComponentToIDs(){

        Map<Integer, List<Long>> myResults = new TreeMap<>();

        // to adapt to the "old" structure of componentsMap
        Iterator<LongCursor> it = Starter.getIteratorforKeySetOfResultCounter();
        while(it.hasNext()){
            long n = it.next().value;
            if(!myResults.containsKey(Starter.getResultCounterforId(n).intValue())){

                ArrayList<Long> newList = new ArrayList<>();
                newList.add(n);
                myResults.put(Starter.getResultCounterforId(n).intValue(),newList);
            } else{
                List<Long> oldList = myResults.get(Starter.getResultCounterforId(n).intValue());
                oldList.add(n);
                myResults.put(Starter.getResultCounterforId(n).intValue(),oldList);
            }
        }

        return myResults;
    }

    public String getResults(){

        Map<Integer, List<Long>> myResults = getMapofComponentToIDs();

        // Building the result string
        StringBuilder returnString = new StringBuilder();
        returnString.append("Component count: ").append(myResults.keySet().size()).append("\n");
        returnString.append("Components with Size between 3 and 5\n");
        returnString.append("- - - - - - - -\n");
        for(Integer s:myResults.keySet()){
            if((myResults.get(s).size()<=2) || (myResults.get(s).size()>=6)){
                continue;
            }

            boolean first = true;
            returnString.append("Component ").append(s).append(": ");
            for(Long n:myResults.get(s)){
                if(!first){
                    returnString.append(", ");
                } else{
                    first = false;
                }
                returnString.append(n);
            }
            returnString.append("\n");
        }

        returnString.append("- - - - - - - -\n");
        returnString.append("Done in: ").append(timer.elapsed(TimeUnit.MICROSECONDS)).append("\u00B5s");

        return returnString.toString();
    }


    private void tarjan(Long currentNode){

        TarjanInfo v = nodeDictionary.get(currentNode);
        v.dfs = maxdfs;
        v.lowlink = maxdfs;
        maxdfs++;

        v.onStack = true;           // This should be
        stack.push(currentNode);        // atomic

        allNodes.remove(currentNode);

        Iterator<LongCursor> it = db.getConnectedNodeIDs(db.getReadOperations(), currentNode, Direction.OUTGOING).iterator();


        while(it.hasNext()){
            Long l = it.next().value;

            TarjanInfo v_new = nodeDictionary.get(l);

            if(allNodes.contains(l)){
                tarjan(l);

                v.lowlink = Math.min(v.lowlink,v_new.lowlink);

            } else if(v_new.onStack){       // O(1)

                v.lowlink = Math.min(v.lowlink,v_new.dfs);
            }

        }

        if(v.lowlink == v.dfs){
            // Root of a SCC
            while(true){
                Long node_v = stack.pop();                      // This should be atomic
                TarjanInfo v_new = nodeDictionary.get(node_v);  // !
                v_new.onStack= false;                           // !

                Starter.putIntoResultCounter(node_v, new AtomicLong(componentID));
                if(Objects.equals(node_v, currentNode)){
                    componentID++;
                    break;
                }
            }
        }
    }

    public static void registerCC(LongHashSet reachableIDs,long sccID){
        Iterator<LongCursor> it =reachableIDs.iterator();
        while(it.hasNext()){
            Long l = it.next().value;
            Starter.putIntoResultCounter(l, new AtomicLong((sccID)));
        }
        //removeFromAllNodes(reachableIDs);
    }

    public static synchronized void removeFromAllNodes(LongLookupContainer c){
        allNodes.removeAll(c);
    }
}
