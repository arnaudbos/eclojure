/**
 *   Copyright (c) Daniel Rune Jensen, Thomas Stig Jacobsen and
 *   Søren Kejser Jensen. All rights reserved.
 *   Copyright (c) Rich Hickey. All rights reserved.
 *   The use and distribution terms for this software are covered by the
 *   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 *   which can be found in the file epl-v10.html at the root of this distribution.
 *   By using this software in any fashion, you are agreeing to be bound by
 * 	 the terms of this license.
 *   You must not remove this notice, or any other, from this software.
 **/

/* rich Jul 26, 2007 */

/*
TODO * Finish nested transactions fix
TODO * Deal with abort events in orElse when nested transactions is done
 */

package clojure.lang;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

@SuppressWarnings({"SynchronizeOnNonFinalField"})
public class IOLockingTransaction extends LockingTransaction {

//    // Event handle keywords, public to allow use by macros, ensures change are reflected in both places
//    public static final Keyword ONABORTKEYWORD = Keyword.intern("on-abort");
//    public static final Keyword ONCOMMITKEYWORD = Keyword.intern("on-commit");
//    public static final Keyword AFTERCOMMITKEYWORD = Keyword.intern("after-commit");

    static class TCRetryEx extends RetryEx{
    }

    enum Unit{
        UNIT
    }

    static class STMEventException extends RuntimeException{
        public STMEventException(String message) {
            super(message);
        }
    }

    static final RetryEx tcRetryex = new TCRetryEx();

    public IOLockingTransaction() {
//        eventListeners.push(new HashMap<Keyword, ArrayList<EventFn>>());
        gets.push(new HashSet<Ref>());
        actions.push(new ArrayList<Agent.Action>());
        vals.push(new HashMap<Ref, Object>());
        sets.push(new HashSet<Ref>());
        commutes.push(new TreeMap<Ref, ArrayList<CFn>>());
        ensures.push(new HashSet<Ref>());
    }

    // nested transactions
    final ArrayDeque<Unit> orElseRunning = new ArrayDeque<Unit>();
//    private final ArrayDeque<HashMap<Keyword, ArrayList<EventFn>>> eventListeners = new ArrayDeque<HashMap<Keyword, ArrayList<EventFn>>>();
    final ArrayDeque<HashSet<Ref>> gets = new ArrayDeque<HashSet<Ref>>();
    final ArrayDeque<ArrayList<Agent.Action>> actions = new ArrayDeque<ArrayList<Agent.Action>>();
    final ArrayDeque<HashMap<Ref, Object>> vals = new ArrayDeque<HashMap<Ref, Object>>();
    final ArrayDeque<HashSet<Ref>> sets = new ArrayDeque<HashSet<Ref>>();
    final ArrayDeque<TreeMap<Ref, ArrayList<CFn>>> commutes = new ArrayDeque<TreeMap<Ref, ArrayList<CFn>>>();
    final ArrayDeque<HashSet<Ref>> ensures = new ArrayDeque<HashSet<Ref>>();
    private STMBlockingBehavior blockingBehaviors = null;
    private final static Collection<STMBlockingBehavior> sharedBlockingBehaviors =
            Collections.newSetFromMap(new ConcurrentHashMap<STMBlockingBehavior, Boolean>());

    @Override //TODO check everything is cleared correctly // DONE
    void stop(int status) {
        if(info != null)
        {
            synchronized(info)
            {
                info.status.set(status);
                info.latch.countDown();
            }
            info = null;
            vals.clear();
            sets.clear();
            commutes.clear();
            // gets, actions, ensures and blockingBehaviors are cleared in #run
        }
    }

    //returns the most recent val
    @Override
    Object lock(Ref ref){
        //can't upgrade readLock, so release it
        releaseIfEnsured(ref);

        boolean unlocked = true;
        try
        {
            tryWriteLock(ref);
            unlocked = false;

            if(ref.tvals != null && ref.tvals.point > readPoint)
                throw retryex;
            Info refinfo = ref.tinfo;

            //write lock conflict
            if(refinfo != null && refinfo != info && refinfo.running())
            {
                if(!barge(refinfo))
                {
                    ref.lock.writeLock().unlock();
                    unlocked = true;
                    return blockAndBail(refinfo);
                }
            }
            ref.tinfo = info;
            return ref.tvals == null ? null : ref.tvals.val;
        }
        finally
        {
            if(!unlocked)
                ref.lock.writeLock().unlock();
        }
    }

    private Object blockAndBail(Info refinfo){
        // Disables block and bail when doing an or else block
        if(!this.orElseRunning.isEmpty()) {
            throw retryex;
        }

//        //Executes on-abort events before stopping and blocking
//        executeOnAbortEvents();

        //stop prior to blocking
        stop(RETRY);
        try
        {
            refinfo.latch.await(LOCK_WAIT_MSECS, TimeUnit.MILLISECONDS);
        }
        catch(InterruptedException e)
        {
            //ignore
        }
        throw retryex;
    }

    // FIXME manage stacks
    private void releaseIfEnsured(Ref ref){
        if(ensures.contains(ref))
        {
            ensures.remove(ref);
            ref.lock.readLock().unlock();
        }
    }

    @Override
    void abort() throws AbortException{
//        //On-Abort events are executed here and not in exception clause due to stop
//        executeOnAbortEvents();
        super.abort();
    }

    void terminate() throws AbortException{
        //Just a more consistent name according to definitions in dptClojure paper
        abort();
    }

    private boolean bargeTimeElapsed(){
        return System.nanoTime() - startTime > BARGE_WAIT_NANOS;
    }

    private boolean barge(Info refinfo){
        boolean barged = false;
        //if this transaction is older
        //  try to abort the other
        if(bargeTimeElapsed() && startPoint < refinfo.startPoint)
        {
            barged = refinfo.status.compareAndSet(RUNNING, KILLED);
            if(barged)
                refinfo.latch.countDown();
        }
        return barged;
    }

    static IOLockingTransaction getEx(){
        LockingTransaction t = transaction.get();
        if(t.info == null || !(t instanceof IOLockingTransaction))
            throw new IllegalStateException("No IO transaction running");
        return (IOLockingTransaction) t;
    }

    static public boolean isRunning(){
        return getRunning() != null;
    }

    static IOLockingTransaction getRunning(){
        LockingTransaction t = transaction.get();
        if(t.info == null || !(t instanceof IOLockingTransaction))
            return null;
        return (IOLockingTransaction) t;
    }

    static public Object runInTransaction(Callable fn) throws Exception{
        LockingTransaction t = transaction.get();
        Object ret;
        if(t == null) {
            transaction.set(t = new IOLockingTransaction());
            try {
                ret = t.run(fn);
            } finally {
                transaction.remove();
            }
        } else {
            //TODO Promote to IOLockingTransaction? Is it possible?
            // if (! (t instanceof IOLockingTransaction)){
            // }
            if(t.info != null) {
                ret = fn.call();
            } else {
                ret = t.run(fn);
            }
        }

        return ret;
    }

    @Override
    Object run(Callable fn) throws Exception{
        boolean done = false;
        Object ret = null;
        ArrayList<Ref> locked = new ArrayList<Ref>();
        ArrayList<Notify> notify = new ArrayList<Notify>();

        for(int i = 0; !done && i < RETRY_LIMIT; i++)
        {
            //Blocks on any set blocking behaviors and clears the set of read refs
            if (this.blockingBehaviors != null) {
                this.blockingBehaviors.await();
                IOLockingTransaction.sharedBlockingBehaviors.remove(this.blockingBehaviors);
                this.blockingBehaviors = null;
            }
            gets.clear();
            // TODO: Should I clear other stacks here too? The tx is restarting so...
            //   DONE
            //   ==> vals, sets and commutes are cleared in `stop`
            //   ==> actions, ensures and blockingBehaviors are cleared below

            try
            {
                pushRefs();
                getReadPoint();
                if(i == 0)
                {
                    startPoint = readPoint;
                    startTime = System.nanoTime();
                }
                info = new Info(RUNNING, startPoint);
                ret = fn.call();
                // TODO at this point, all nested transactions should be done: all stacks should be of depth 1
                TreeMap<Ref, ArrayList<CFn>> commutes = this.commutes.peek();
                HashSet<Ref> sets = this.sets.peek();
                HashSet<Ref> ensures = this.ensures.peek();
                HashMap<Ref, Object> vals = this.vals.peek();

                //make sure no one has killed us before this point, and can't from now on
                if(info.status.compareAndSet(RUNNING, COMMITTING))
                {
                    for(Map.Entry<Ref, ArrayList<CFn>> e : commutes.entrySet())
                    {
                        Ref ref = e.getKey();
                        if(sets.contains(ref)) continue;

                        boolean wasEnsured = ensures.contains(ref);
                        //can't upgrade readLock, so release it
                        releaseIfEnsured(ref);
                        tryWriteLock(ref);
                        locked.add(ref);
                        if(wasEnsured && ref.tvals != null && ref.tvals.point > readPoint)
                            throw retryex;

                        Info refinfo = ref.tinfo;
                        if(refinfo != null && refinfo != info && refinfo.running())
                        {
                            if(!barge(refinfo))
                                throw retryex;
                        }
                        Object val = ref.tvals == null ? null : ref.tvals.val;
                        vals.put(ref, val);
                        for(CFn f : e.getValue())
                        {
                            vals.put(ref, f.fn.applyTo(RT.cons(vals.get(ref), f.args)));
                        }
                    }
                    for(Ref ref : sets)
                    {
                        tryWriteLock(ref);
                        locked.add(ref);
                    }

                    //validate and enqueue notifications
                    for(Map.Entry<Ref, Object> e : vals.entrySet())
                    {
                        Ref ref = e.getKey();
                        ref.validate(ref.getValidator(), e.getValue());
                    }

//                    //Notify all listeners for "on-commit" event
//                    PersistentHashSet persistentSets = PersistentHashSet.create(RT.seq(this.vals.keySet()));
//                    try {
//                        EventManager.runEvents(IOLockingTransaction.ONCOMMITKEYWORD, this.eventListeners, persistentSets);
//                    } catch(RetryEx ex) {
//                        throw new STMEventException("stm transaction restarted during on-commit event");
//                    }

                    //at this point, all values computed, all refs to be written locked
                    //no more client code to be called
                    long commitPoint = getCommitPoint();
                    for(Map.Entry<Ref, Object> e : vals.entrySet())
                    {
                        Ref ref = e.getKey();
                        Object oldval = ref.tvals == null ? null : ref.tvals.val;
                        Object newval = e.getValue();
                        int hcount = ref.histCount();

                        if(ref.tvals == null)
                        {
                            ref.tvals = new Ref.TVal(newval, commitPoint);
                        }
                        else if((ref.faults.get() > 0 && hcount < ref.maxHistory)
                                || hcount < ref.minHistory)
                        {
                            ref.tvals = new Ref.TVal(newval, commitPoint, ref.tvals);
                            ref.faults.set(0);
                        }
                        else
                        {
                            ref.tvals = ref.tvals.next;
                            ref.tvals.val = newval;
                            ref.tvals.point = commitPoint;
                        }
                        if(ref.getWatches().count() > 0)
                            notify.add(new Notify(ref, oldval, newval));
                    }

                    done = true;
                    info.status.set(COMMITTED);
                }
                else if(info.status.get() == KILLED)
                {
                    //transaction killed, make sure on-abort events are executed
                    throw retryex;
                }
            } catch(RetryEx ex) {
                // Ignore the exception so we retry rather than fall out
//                executeOnAbortEvents();
            } catch(AbortException ae) {
                // We want to terminate the transaction but have nothing to return,
                // on-abort events are executed by abort before it throws this exception
                return null;
//            } catch(Exception exception) {
//                executeOnAbortEvents();
//                throw exception;
            }
            finally
            {
                for(int k = locked.size() - 1; k >= 0; --k)
                {
                    locked.get(k).lock.writeLock().unlock();
                }
                locked.clear();
                for(Ref r : ensures.peek())
                {
                    r.lock.readLock().unlock();
                }
                ensures.clear();
                stop(done ? COMMITTED : RETRY);
                try
                {
                    if(done) //re-dispatch out of transaction
                    {
                        for(Notify n : notify)
                        {
                            n.ref.notifyWatches(n.oldval, n.newval);
                        }
                        for(Agent.Action action : actions.peek())
                        {
                            Agent.dispatchAction(action);
                        }
                        for (STMBlockingBehavior blockingBehavior : IOLockingTransaction.sharedBlockingBehaviors)
                        {
                            blockingBehavior.handleChanged();
                        }
//                        EventManager.runEvents(IOLockingTransaction.AFTERCOMMITKEYWORD, this.eventListeners, null);
                    }
                }
                finally
                {
                    notify.clear();
                    actions.clear();
//                    eventListeners.clear();
                }
            }
        }
        if(!done)
            throw Util.runtimeException("Transaction failed after reaching retry limit");
        return ret;
    }
//
//    HashMap<Keyword, ArrayList<EventFn>> getEventListeners() {
//        return this.eventListeners;
//    }

    @Override
    public void enqueue(Agent.Action action) {//TODO Done
        actions.peek().add(action);
    }

    @Override
    Object doGet(Ref ref){//TODO Done
        if(!info.running())
            throw retryex;
        gets.peek().add(ref);
        for (HashMap<Ref, Object> next : vals)
        {
            if (next.containsKey(ref))
                return next.get(ref);
        }

        try
        {
            ref.lock.readLock().lock();
            if(ref.tvals == null)
                throw new IllegalStateException(ref.toString() + " is unbound.");
            Ref.TVal ver = ref.tvals;
            do
            {
                if(ver.point <= readPoint)
                    return ver.val;
            } while((ver = ver.prior) != ref.tvals);
        }
        finally
        {
            ref.lock.readLock().unlock();
        }
        //no version of val precedes the read point
        ref.faults.incrementAndGet();
        throw retryex;

    }

    @Override
    Object doSet(Ref ref, Object val) {//TODO Done except the FIXME
        if(!info.running())
            throw retryex;
        for (TreeMap<Ref, ArrayList<CFn>> next : commutes)
        {
            if (next.containsKey(ref))
                throw new IllegalStateException("Can't set after commute"); //FIXME is there a problem with orElse catch blocks?
        }
        if(!sets.peek().contains(ref))
        {
            sets.peek().add(ref);
            lock(ref);
        }
        vals.peek().put(ref, val);
        return val;
    }

    @Override
    void doEnsure(Ref ref){//TODO Done
        if(!info.running())
            throw retryex;
        for (HashSet<Ref> next : ensures)
        {
            if (next.contains(ref))
                return;
        }
        ref.lock.readLock().lock();

        //someone completed a write after our snapshot
        if(ref.tvals != null && ref.tvals.point > readPoint) {
            ref.lock.readLock().unlock();
            throw retryex;
        }

        Info refinfo = ref.tinfo;

        //writer exists
        if(refinfo != null && refinfo.running())
        {
            ref.lock.readLock().unlock();

            if(refinfo != info) //not us, ensure is doomed
            {
                blockAndBail(refinfo);
            }
        }
        else
            ensures.peek().add(ref);
    }

    @Override
    Object doCommute(Ref ref, IFn fn, ISeq args) {//TODO Done
        if(!info.running())
            throw retryex;
        HashMap<Ref, Object> found = null;
        for (HashMap<Ref, Object> next : vals)
        {
            if (next.containsKey(ref))
                found = next;
        }
        if(found==null)
        {
            Object val = null;
            try
            {
                ref.lock.readLock().lock();
                val = ref.tvals == null ? null : ref.tvals.val;
            }
            finally
            {
                ref.lock.readLock().unlock();
            }
            vals.peek().put(ref, val);
        }
        ArrayList<CFn> fns = null;
        for (TreeMap<Ref, ArrayList<CFn>> next : commutes) {
            if (next.containsKey(ref))
                fns = next.get(ref);
        }
        if(fns == null)
            commutes.peek().put(ref, fns = new ArrayList<CFn>());
        fns.add(new CFn(fn, args));

        Object ret = null;
        if(found==null)
        {
            ret = fn.applyTo(RT.cons(vals.peek().get(ref), args));
        }
        else
        {
            ret = fn.applyTo(RT.cons(found.get(ref), args));
        }

        vals.peek().put(ref, ret);
        return ret;
    }

    void doBlocking(HashSet<Ref> refs, IFn fn, ISeq args, boolean blockOnAll) throws InterruptedException, RetryEx {
        if ( ! info.running()) {
            throw retryex;
        }

        if (refs == null) {
            refs = new HashSet<Ref>();
            for (HashSet<Ref> next : this.gets)
            {
                refs.addAll(next);
            }
        }

        if (refs.isEmpty()) {
            throw new IllegalArgumentException("The set of Refs cannot be empty");
        }

        if (blockOnAll) {
            if (fn != null) {
                this.blockingBehaviors = new STMBlockingBehaviorFnAll(refs, fn, args, this.readPoint);
            } else {
                this.blockingBehaviors = new STMBlockingBehaviorAll(refs, this.readPoint);
            }
        } else {
            if (fn != null) {
                this.blockingBehaviors = new STMBlockingBehaviorFnAny(refs, fn, args, this.readPoint);
            } else {
                this.blockingBehaviors = new STMBlockingBehaviorAny(refs, this.readPoint);
            }
        }
        IOLockingTransaction.sharedBlockingBehaviors.add(this.blockingBehaviors);
        //Use of tcRetryex allows code to differentiate between a retry/retry-all retry and a "normal" retry
        throw tcRetryex;
    }

    Object doOrElse(boolean orElseOnRetryEx, ArrayList<IFn> fns) {
        if ( ! info.running()) {
            throw retryex;
        }

        Set<Ref> nestedBlockingBehaviors = new HashSet<Ref>();

        //Checks if or-else should run the next function only for retry/retry-all or all retryex
        if(orElseOnRetryEx) {
            for (IFn fn : fns) {
                try {
//                    eventListeners.push(new HashMap<Keyword, ArrayList<EventFn>>());
                    orElseRunning.push(Unit.UNIT);
                    pushRefs();

                    Object ret = fn.invoke();

//                    HashMap<Keyword, ArrayList<EventFn>> altEventListeners = eventListeners.pop();
//                    eventListeners.peek().putAll(altEventListeners);
                    orElseRunning.pop();
                    mergeRefs();

                    return ret;
                } catch (Throwable t) {
                    if (t instanceof RetryEx) {
                        // merge blockingBehaviors here
                        nestedBlockingBehaviors.addAll(this.gets.peek());
                    }

                    //FIXME do this here or unroll at the upmost?
                    // special case of AbortException is handled by abort which executes on-abort events before it throws this exception=
                    // Comment everything related to events and figure that out before uncommenting
//                    try {
//                        // only call listeners for current stack or unroll (descendingIterator) the stack? The later I'd say.
//                        EventManager.runEvents(IOLockingTransaction.ONABORTKEYWORD, this.eventListeners.peek(), null);
//                    } catch(RetryEx e) {
//                        throw new STMEventException("stm transaction restarted during retry");
//                    }

                    // We ignore the exception to allow the next function to execute
//                    eventListeners.pop(); // keep stack in order to execute onAbort?

                    orElseRunning.pop();
                    popRefs();

                    if (! (t instanceof RetryEx)) {
                        // let ex bubble-up and end the enclosing transaction after all the `pop`s
                        throw new RuntimeException("stm nested transaction failed without retrying", t);
                    }
                }
            }
            // all alternatives issued retry
        } else {
            for (IFn fn : fns) {
                // TODO same as above, take care with RetryEx // DONE
                try {
//                    eventListeners.push(new HashMap<Keyword, ArrayList<EventFn>>());
                    orElseRunning.push(Unit.UNIT);
                    pushRefs();

                    Object ret = fn.invoke();

//                    HashMap<Keyword, ArrayList<EventFn>> altEventListeners = eventListeners.pop();
//                    eventListeners.peek().putAll(altEventListeners);
                    orElseRunning.pop();
                    mergeRefs();

                    return ret;
                } catch (RetryEx ex) {
                    if (ex instanceof TCRetryEx) {
                        // merge blockingBehaviors here
                        nestedBlockingBehaviors.addAll(this.gets.peek());

//                        eventListeners.pop(); // keep stack in order to execute onAbort?

                        // We ignore the exception to allow the next function to execute
                    }

                    //FIXME do this here or unroll at the upmost?
                    // special case of AbortException is handled by abort which executes on-abort events before it throws this exception=
                    // Comment everything related to events and figure that out before uncommenting
//                    try {
//                        // only call listeners for current stack or unroll (descendingIterator) the stack? The later I'd say.
//                        EventManager.runEvents(IOLockingTransaction.ONABORTKEYWORD, this.eventListeners.peek(), null);
//                    } catch(RetryEx e) {
//                        throw new STMEventException("stm transaction restarted during retry");
//                    }

                    orElseRunning.pop();
                    popRefs();

                    if (! (ex instanceof TCRetryEx)) {
                        // bubble-up if not a TCRetryEx (aka. a classic stm RetryEx has been thrown)
                        throw ex;
                    }
                } catch (Throwable t) {
                    // let throwable bubble-up and end the enclosing transaction
                    orElseRunning.pop();
                    popRefs();
                    throw new RuntimeException("stm nested transaction failed without retrying", t);
                }
            }
            // all alternatives issued retry
        }

        // wait on the union on the sets of refs that have been accessed
        if (!nestedBlockingBehaviors.isEmpty()) {
            // -- merge blockingBehaviors with inner, if any, in order to trim sharedBlockingBehaviors
            if (blockingBehaviors!=null) {
                IOLockingTransaction.sharedBlockingBehaviors.remove(this.blockingBehaviors);
            }
            nestedBlockingBehaviors.addAll(this.blockingBehaviors.refSet);
            // -- merge blockingBehaviors with inner, if any, in order to trim sharedBlockingBehaviors

            this.blockingBehaviors = new STMBlockingBehaviorAny(nestedBlockingBehaviors, this.readPoint);
            IOLockingTransaction.sharedBlockingBehaviors.add(this.blockingBehaviors);
        }

        throw retryex;
    }

    private void pushRefs() {
        gets.push(new HashSet<Ref>());
        actions.push(new ArrayList<Agent.Action>());
        vals.push(new HashMap<Ref, Object>());
        sets.push(new HashSet<Ref>());
        commutes.push(new TreeMap<Ref, ArrayList<CFn>>());
        ensures.push(new HashSet<Ref>());
    }

    private void mergeRefs() {
        HashSet<Ref> altGets = gets.pop();
        gets.peek().addAll(altGets);
        ArrayList<Agent.Action> altActions = actions.pop();
        actions.peek().addAll(altActions);
        HashMap<Ref, Object> altVals = vals.pop();
        vals.peek().putAll(altVals);
        HashSet<Ref> altSets = sets.pop();
        sets.peek().addAll(altSets);
        TreeMap<Ref, ArrayList<CFn>> altCommutes = commutes.pop();
        commutes.peek().putAll(altCommutes);
        HashSet<Ref> altEnsures = ensures.pop();
        ensures.peek().addAll(altEnsures);
    }

    private void popRefs() {
        gets.pop();
        actions.pop();
        vals.pop();
        sets.pop();
        commutes.pop();
        ensures.pop();
    }
//
//    private void executeOnAbortEvents() {
//        //BlockAndBail stops the transaction before it throws an retryex exception,
//        //so it needs to executes the necessary events as stopping releases ownership of refs
//        if(info == null) {
//            return;
//        }
//
//        synchronized(info) {
//            info.status.set(COMMITTING);
//        }
//        try {
//            // FIXME only call listeners for current stack or unroll (descendingIterator?) the stack? The later I'd say.
//            EventManager.runEvents(IOLockingTransaction.ONABORTKEYWORD, this.eventListeners, null);
//        } catch(RetryEx ex) {
//            throw new STMEventException("stm transaction restarted during on-abort event");
//        }
//    }
}
