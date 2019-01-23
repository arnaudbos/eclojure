# stm.io

This repository is a library version of **[eClojure](https://github.com/skejserjensen/eclojure)**
that extends the Software Transactional Memory (STM) of Clojure.

> WIP NOTICE
> Please note that this branch is currently a work in progress mainly related to documentation, I will definitely commit stuff here and there that I will **mercilessly** squash in order to clean the commit history when finished.
> You can identify the unfinished sections easily, there will be a big fat **TODO** in front.

## Rationale

Clojure's STM is a wonderful piece of software, but managing side-effects inside a Clojure transaction
is tedious or impossible, due to unpredictable retries.

As I was experimenting on a side project, I decided to distribute the computation on multiple nodes,
this is how I've found **[dptClojure](https://github.com/eXeDK/dpt1010f15)** and
(related) **[eClojure](https://github.com/skejserjensen/eclojure)**.  
Both are extended versions of Clojure, see [Original work](#original-work).

eClojure is the most recent one and includes a benchmark showing increased execution time of the
STM transactions compared to Clojure's version.

In order to provide the extensions to Clojure's STM contributed in eClojure to Clojure projects
on an opt-in basis, I have repackaged it into this library, rather than a language fork.

<img src="/images/hat.svg" align="right" height="120" />

## Original work

This repository is a forked and packaged-as-a-library version of **[eClojure](https://github.com/skejserjensen/eclojure)**,
which is itself a fork of Clojure 1.8 that extends the Software Transactional Memory (STM) of Clojure.

> **[eClojure](https://github.com/skejserjensen/eclojure)** is the software complementing the paper **[Extending Software Transactional Memory in Clojure with Side-Effects and Transaction Control](http://dl.acm.org/citation.cfm?id=3005729.3005737)** presented at the **[9th European Lisp Symposium](http://www.european-lisp-symposium.org/editions/2016/)**.
> 
> The main contents is our extended version of Clojure 1.8, named eClojure, that extends the Software Transactional Memory (STM) of Clojure 1.8 in two directions. First support for synchronising side-effects using transactions is made possible through three events emitted: *after-commit*, *on-abort*, and *on-commit*. Second an implementation and extension of multiple transaction control methods pioneered by Haskell, *retry* and *orElse*, is provided.
> 
> For an in-depth description of these two sets of extensions see the above mentioned paper.
>
> -- <cite>Source **[eClojure](https://github.com/skejserjensen/eclojure)**</cite>

eClojure is built upon **[dptClojure](https://github.com/eXeDK/dpt1010f15)**, developed as part of the master thesis **[Unifying STM and Side Effects in Clojure](http://projekter.aau.dk/projekter/files/213827517/p10DOC.pdf)**.

## Definitions

The following definitions will be used in this README.

> **Transaction Control**
> Functionality allowing a developer to manually control a transaction for example to block, abort or terminate a transaction.
> 
> **Abort**
> A transaction that stop execution, for example due to conflicts with a another transaction and allows the implementation to re-execute.
> 
> **Terminate**
> A transaction that aborts and is prevented from being reexecuted through some means.
> 
> -- <cite>Source [Unifying STM and Side Effects in Clojure](http://projekter.aau.dk/projekter/files/213827517/p10DOC.pdf)</cite>

## Usage

[![Clojars Project](http://clojars.org/stm.io/latest-version.svg)](https://clojars.org/stm.io)

In order to use stm.io transactions you must import the `stm.io` namespace and
use its `dosync` or `sync` macros, just like you would do to use Clojure's built-in
STM. In fact stm.io's STM is just a layer on top of Clojure's.

### Clojure STM

```clojure
(ns banking)

;; Create 2 bank accounts
(def acc1 (ref 100))
(def acc2 (ref 200))

;; How much money is there?
(println @acc1 @acc2)
;; => 100 200

;; Either both accounts will be changed or none
(defn transfer-money [a1 a2 amount]
  (dosync
    (alter a1 - amount)
    (alter a2 + amount)
    amount)) ; return amount from dosync block and function (just for fun)

;; Now transfer $20
(transfer-money acc1 acc2 20)
;; => 20

;; Check account balances again
(println @acc1 @acc2)
;; => 80 220

;; => We can see that transfer was successful

;; Example by Dmitry Kakurin on ClojureDocs: http://clojuredocs.org/clojure.core/dosync#example-54d1f076e4b081e022073c5b
```

This is the canonical example of a transaction implemented with Clojure's built-in
`clojure.core/dosync`.

Let's see what happens with side effects when a transaction restarts.

```clojure
(ns banking)

;; Synchronization thingy
(def lockbject (Object.))

;; Create 2 bank accounts
(def acc1 (ref {:name "acc1"
                :amount 100}))
(def acc2 (ref {:name "acc2"
                :amount 200}))

;; How much money is there?
(println (:amount @acc1) (:amount @acc2))
;; => 100 200

(defn update-account [op amount]
  #(update % :amount op amount))

(defn nemesis-withdraw [acc amount]
  (Thread.
    #(dosync
       (alter acc (update-account - amount))
       ; Nemesis thread waits for transfer thread to sleep before commit
       (locking lockbject
         (.notify lockbject)))))

;; Either both accounts will be changed or none but this time
;; introduce a conflicting schedule
(defn transfer-money [a1 a2 amount]
  (with-local-vars [is-retry? false]
    (dosync
      (println "Transferring" amount "from" (:name @a1) "to" (:name @a2))
      ; For demo purposes: cause this thread to wait for nemesis thread to alter first
      (when-not (var-get is-retry?)
        (var-set is-retry? true)
        (locking lockbject
          (.start (nemesis-withdraw a1 amount))
          (.wait lockbject)))
      ; Actual transfer code
      (alter a1 (update-account - amount))
      (alter a2 (update-account + amount))
      amount))) ; return amount from dosync block and function (just for fun)

;; Now transfer $20
(transfer-money acc1 acc2 20)
;; Transferring 20 from acc1 to acc2
;; Transferring 20 from acc1 to acc2
;; => 20

;; Check account balances again
(println (:amount @acc1) (:amount @acc2))
;; => 60 220

;; We can see that transfer was successful eventually but only after a retry
```

There's a little bit more to unpack because of the boilerplate necessary to ensure
our `transfer-money` transaction retries due to another operation happening on account
"acc1": `nemesis-withdraw`.

The gist is that due to the other transaction committing first, our transfer is retried
and the side effect of printing "Transferring 20 from acc1 to acc2" is executed twice.

The idea of inserting database records in there gives goosebumps.

### Side effects

stm.io provides several macros and functions you can use to execute side effects inside
an STM transaction.

We will now revisit the previous example with stm.io's `dosync` and `on-commit` macros.

```clojure
(ns banking
  (:require [stm.io :as io]))

(def lockbject (Object.))

;; Create 2 bank accounts
(def acc1 (ref {:name "acc1"
                :amount 100}))
(def acc2 (ref {:name "acc2"
                :amount 200}))

;; How much money is there?
(println (:amount @acc1) (:amount @acc2))
;; => 100 200

(defn update-account [op amount]
  #(update % :amount op amount))

(defn nemesis-withdraw [acc amount]
  (Thread.
    #(io/dosync
       (alter acc (update-account - amount))
       ; Nemesis thread waits for transfer thread to sleep before commit
       (locking lockbject
         (.notify lockbject)))))

;; Either both accounts will be changed or none but this time
;; introduce a conflicting schedule
(defn transfer-money [a1 a2 amount]
  (with-local-vars [is-retry? false]
    (io/dosync
      (io/on-commit (println "Transferring" amount "from" (:name @a1) "to" (:name @a2)))
      ; For demo purposes: cause this thread to wait for nemesis thread to alter
      (when-not (var-get is-retry?)
        (var-set is-retry? true)
        (locking lockbject
          (.start (nemesis-withdraw a1 amount))
          (.wait lockbject)))
      ; Actual transfer code
      (alter a1 (update-account - amount))
      (alter a2 (update-account + amount))
      amount))) ; return amount from dosync block and function (just for fun)

;; Now transfer $20
(transfer-money acc1 acc2 20)
;; Transferring 20 from acc1 to acc2
;; => 20

;; Check account balances again
(println (:amount @acc1) (:amount @acc2))
;; => 60 220

;; => We can see that transfer was successful eventually but only after a retry
```

This time, `println` is only executed once: as soon as we are guaranteed that the
transaction will succeed, stm.io's transaction will execute listeners attached to
its "on-commit" events. If it so happens that the transaction should retry, this
point is not reached and the handler is not run.

stm.io event manager includes three built-in event types allowing you to trigger
side effects from within a transaction at different points in time and with different
gotchas you need to be aware of.

```clojure
(stm.io/on-abort (println "Transaction abort"))

;; => Will be executed each time a transaction is aborted (which may happen
;; more than once) before a retry or without any subsequent retry if too
;; many abort occurred.
;; Don't worry, at this point you have serious problems and the transaction
;; will terminate with a beautiful RuntimeException anyway.
;; There is also the `io/on-abort-fn` variant, which takes a function and
;; arguments to be called on transaction abort.

(io/on-commit (println "Transferring" amount "from" (:name @a1) "to" (:name @a2)))

;; => As we've seen, on-commit will be executed as soon as stm.io is guaranteed
;; that the transaction will commit.
;; All the refs to be written are locked by the transaction and the commit will
;; happen after the on-commit expressions have been run.
;; There is also the `stm.io/on-commit-fn` variant, which takes a function and
;; arguments to be called on transaction commit.

(io/after-commit (println "Done transferring" amount "from" (:name @a1) "to" (:name @a2)))

;; => Will be executed after the transaction has been committed, when all the
;; read and write locks have been released, the transaction marked as committed,
;; ref watches notified and agents actions dispatched.
;; There is also the `stm.io/after-commit-fn` variant, which takes a function and
;; arguments to be called after transaction commit.
```

### Transaction control

<img src="/images/tap-to-retry.gif" align="right" height="195" />

#### Thread synchronization using `retry`

As we've seen with the simple banking example above, Clojure's STM provides
an "auto-retry" mechanism for conflicting transactions: any doomed or barged
transaction will automatically restart from the beginning and all its effects
will be unseen from the outside world.

This is very handy, but sometimes we need to especially wait for a condition to
be met on a ref before taking actions (aka. synchronization: a value to be
reached, a queue to be filled, etc).

Let's see with an example:

```clojure
(ns banking
  (:refer-clojure :exclude [println])
  (:require [stm.io :as io]))

;; Just a utility for multi-threaded printing
(defn println [& args]
  (locking *out*
    (apply clojure.core/println args)
    ))

;; Create 2 bank accounts
(def acc1 (ref {:name "acc1"
                :amount 100}))
(def acc2 (ref {:name "acc2"
                :amount 0}))

;; How much money is there?
(println (:amount @acc1) (:amount @acc2))
;; => 100 0

(defn update-account [op amount]
  #(update % :amount op amount))

;; Withdraw only if not overdraws the account
(defn limited-withdraw [acc amount]
  (io/dosync
    (println "Trying to withdraw" amount "from" (:name @acc))
    (io/on-commit (println "Withdrawn" amount "from" (:name @acc)))
    (if (>= (:amount @acc) amount 0)
      (do
        (alter acc (update-account - amount)
        true)
      false)))

;; Either both accounts will be changed or none
(defn transfer-money [a1 a2 amount]
  (io/dosync
    (io/on-commit (println "Transferred" amount "from" (:name @a1) "to" (:name @a2)))
    (alter a1 (update-account - amount))
    (alter a2 (update-account + amount))
    amount)) ; return amount from dosync block and function (just for fun)

;; Repeatedly try to withdraw $50 from acc2
(.start
  (Thread.
    #(while (not (limited-withdraw acc2 50)))))
;; => nil
;; Trying to withdraw 50 from acc2
;; Trying to withdraw 50 from acc2
;; Trying to withdraw 50 from acc2
;; ...

;; Now transfer $100
(.start
  (Thread.
    #(transfer-money acc1 acc2 100)))
;; => nil
;; Transferred 100 from acc1 to acc2
;; Trying to withdraw 50 from acc2
;; Withdrawn 50 from acc2

;; Check account balances again
(println (:amount @acc1) (:amount @acc2))
;; => 0 50

;; => We can see that withdraw was successful eventually, but after
;;    a seemingly infinite loop of retries
```

We implemented a `limited-withdraw` operation that only withdraw money from an
account if the funds are sufficient and returns a boolean.

This is ok but as a user of this function we might want our withdraw operation
to make progress and succeed eventually, so we wrap it into a loop.  
Once the predicate on `acc1` (balance >= 50) will be met, we will exit the loop
and the thread.

What we have here is a thread that is busy waiting, and a waste of processor
time.  
How can we make better use of our CPU and avoid busy-waiting on the account's
balance?

We could make use of a watch function on the `acc1` ref, but it would be
awkward (try it).

We will now revisit the previous example with stm.io's `retry` macro flavors.

```clojure
(ns banking
  (:refer-clojure :exclude [println])
  (:require [stm.io :as io]))

;; Just a utility for multi-threaded printing
(defn println [& args]
  (locking *out*
    (apply clojure.core/println args)
    ))

;; Create 2 bank accounts
(def acc1 (ref {:name "acc1"
                :amount 100}))
(def acc2 (ref {:name "acc2"
                :amount 0}))

;; How much money is there?
(println (:amount @acc1) (:amount @acc2))
;; => 100 0

(defn update-account [op amount]
  #(update % :amount op amount))

;; Check if withdraw is more than current balance
(defn check-not-overdraw [acc amount]
  (>= (:amount @acc) amount 0))

;; Withdraw only if not overdraws the account
(defn limited-withdraw [acc amount]
  (io/dosync
    (println "Trying to withdraw" amount "from" (:name @acc))
    (io/on-abort (println "Withdraw aborted"))
    (io/on-commit (println "Withdrawn" amount "from" (:name @acc)))
    (if (check-not-overdraw acc amount)
      (alter acc (update-account - amount))
      (io/retry [acc]))))

;; Either both accounts will be changed or none
(defn transfer-money [a1 a2 amount]
  (io/dosync
    (io/on-commit (println "Transferred" amount "from" (:name @a1) "to" (:name @a2)))
    (alter a1 (update-account - amount))
    (alter a2 (update-account + amount))
    amount)) ; return amount from dosync block and function (just for fun)

;; Try to withdraw $50 from acc2
(.start
  (Thread.
    #(limited-withdraw acc2 50)))
;; => nil
;; Withdraw aborted

;; Now transfer $100
(.start
  (Thread.
    #(transfer-money acc1 acc2 100)))
;; => nil
;; Transferred 100 from acc1 to acc2
;; Trying to withdraw 50 from acc2
;; Withdrawn 50 from acc2

;; Check account balances again
(println (:amount @acc1) (:amount @acc2))
;; => 0 50

;; => We can see that withdraw was blocked until `transfer-money` successfully
;;    updated acc2's balance.
```

The addition of `(io/retry [acc])` spares us the need to expose the result of
the operation with a boolean and to busy-wait on the account's balance.

As you can see, the call to `limited-withdraw`, even though it blocks until the
account has changed, still prints the following line:

```clojure
;; Withdraw aborted
```

This log comes from the `stm.io/on-abort` macro inside `limited-withdraw`.  

Under the hood, `stm.io/retry` will effectively cause the current transaction
to abort (cancelling any previous modification) before retrying.  
The retry will then block the transaction's thread, before executing any client
code from the `dosync` expression until any of the refs given to `stm.io/retry`
have been updated by **another successful transaction started after the aborted
transaction**.  
When the `acc1` ref changes, the blocked transaction will resume.

There are two variants of retry with multiple arities and slightly different
behaviors.


```clojure
(io/retry)

;; => Will abort the current transaction and block the retry until any ref
;; read during the aborted transaction has changed.

(io/retry [ref1 ref2 ... refN])

;; => Will abort the current transaction and block the retry until any ref from
;; the vector (`ref1`, `ref2`, ..., refN) has changed.

(io/retry [ref1 ref2 ... refN] fn arg1 arg2 ... argM)

;; => Will abort the current transaction and block the retry until any ref from
;; the vector (`ref1`, `ref2`, ..., `refN`) has changed and function `f` called
;; with arguments `arg1` to `argM` returns a truthy value.

(io/retry-all)

;; => Will abort the current transaction and block the retry until all refs
;; read during the aborted transaction have changed.

(io/retry-all [ref1 ref2 ... refN])

;; => Will abort the current transaction and block the retry until all refs from
;; the vector (`ref1`, `ref2`, ..., refN) have changed.

(io/retry-all [ref1 ref2 ... refN] fn arg1 arg2 ... argM)

;; => Will abort the current transaction and block the retry until all refs from
;; the vector (`ref1`, `ref2`, ..., `refN`) have changed and function `f` called
;; with arguments `arg1` to `argM` returns a truthy value.
```

In the previous example, `(io/retry [acc])` could be replaced by `(io/retry)` or
`(io/retry-all)` because a single ref was involved in the transaction: `acc`.  
But `(io/retry [acc] #(check-not-overdraw acc 0))` would have been more
appropriate: preventing unlock/retry/lock cycles of the transaction if the
account's balance were to be updated by different amounts but not enough to
fulfill the withdraw requested.  
N. B. `(io/retry-all [acc] #(>= (:amount @acc) amount 0))` would work too.

<img src="/images/time-travel-stuff.gif" align="right" height="220" />

#### Choosing/composing alternatives with `or-else`

**TODO** Found a bug in encapsulation of nested transactions, fix it first
**TODO** Explain how to provide alternative paths with `or-else`.  
**TODO** Explain how to explicitly `terminate` (see [definition](#definitions)).

<img src="/images/rainbow-unicorn.gif" align="right" height="300" />

### Gotchas

stm.io's built-in event handlers provide a consistent way to trigger side effects
from within a Clojure STM transaction at well defined points in time, block
transactions for synchronization purposes or provide alternatives. But the
library does not prevent you from shooting yourself in the foot if you don't use
it with caution.

#### `dosync` uncaught exception

Clojure's `sync` and `dosync` docstrings are very explicit:
> Any uncaught exception will abort the transaction and flow out of sync.

In fact, any exceptions not related to the STM internal behaviour
(`LockingTransaction.RetryEx`) thrown by an expression inside a transaction will
**terminate** the transaction.

stm.io is not different here and does not try to change this behaviour, it will
just ensure that on-abort events are executed before the transaction terminates
if any `LockingTransaction.RetryEx` or any other uncaught `Exception` happens.

Clojure also provides a `LockingTransaction#abort()` methods which cleanly
terminates a running transaction and throws a `LockingTransaction.AbortException`,
but there is no facility to call it from Clojure.

stm.io provides `stm.io/terminate` that will call `IOLockingTransaction#terminate()`
internally (which is the same as the extended `IOLockingTransaction#abort()`) which
executes the on-abort events before cleanly terminating the running transaction and
throwing a `LockingTransaction.AbortException` as Clojure would do.  
> The name `AbortException` is a bit misleading because it looks more like the
[definition](#definitions) of "terminate" given above, but you shouldn't ever
need it, so it has been kept internally to be consistent with Clojure's STM
implementation and the more appropriate (in the way of stm.io) `stm.io/terminate`
facility has been provided instead.

Keep in mind that `LockingTransaction.AbortException` is not an exception for
you to use. stm.io will just catch it in order to return `null` (because
there is nothing else to return) after the transaction has been terminated.

#### Uncaught exception during events execution

stm.io events are executed as part of a "running" transaction at different point
in time and you should take care of which exception you let slip through your
call stack to the transaction:

* During on-abort

on-abort events are executed either after a transaction attempt has failed and
before a retry occurs, or right after an explicit `stm.io/retry` (or
`stm.io/retry-all`) before the retry is executed, or when an `AbortException`
is thrown using `stm.io/terminate`.

stm.io will catch any `RetryEx` thrown during the execution of an on-abort event
handler and will throw a `STMEventException`.  
Any other exception will 

* During on-commit

**TODO** Explain gotchas when using `on-commit`

* During after-commit

**TODO** Explain gotchas when using `after-commit`

#### Transaction control

**TODO** Explain gotchas when using `retry`

* coarse vs fine-grained retry
* deadlock yourself
* context switching
* starvation
* uncaught exceptions during unlocking predicate execution :boom:

**TODO** Explain gotchas when using `or-else`  
**TODO** Explain gotchas when using `terminate`

#### Order matters

**TODO** /!\ "nested" dosync

## Structure

- **src/clj/stm/io.clj:** The Clojure source code for eClojure by the eClojure authors with a few contributions for stm.io, see git history for details.
- **src/java/\*:** The Java source code for eClojure by the eClojure authors with a few contributions for stm.io, see git history for details.
- **test/clj/\*:** Additional Clojure unit tests for eClojure by the eClojure authors with a few contributions for stm.io, see git history for details.
- **overhead:** The source code used to benchmark the overhead of the additions made by the eClojure authors as documented in the paper with a few contributions to adapt it to this library.

## License

Distributed under the Eclipse Public License, the same as Clojure.
