package de.saschapeukert.algorithms.abst;

import de.saschapeukert.database.DBUtils;
import org.neo4j.graphdb.Transaction;

import java.util.concurrent.Callable;

/**
 * This class is the basis of all used Callables.
 * It nearly only provides an interface but also enables the children of this class to do db operations.
 *
 * Do not directly extend from this class, use {@link WorkerCallableTemplate} or {@link MyAlgorithmBaseCallable} instead.
 * <br>
 * Created by Sascha Peukert on 19.11.2015.
 */
public abstract class MyBaseCallable implements Callable {

    protected Transaction tx;
    protected DBUtils db;

    public MyBaseCallable(DBUtils db){
        this.db = db;
    }

    public abstract void work();
}
