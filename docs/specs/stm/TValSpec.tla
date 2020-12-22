---- MODULE TValSpec ----
(*****************************************************************************)
(* Specification of Clojure STM "committed values" TVal.                     *)
(* Verify that a tvals chain behaves as a doubly-linked, circular list.      *)
(*****************************************************************************)
EXTENDS TVal, Integers, FiniteSets, Sequences, TLC

CONSTANT Values

(*****************************************************************************)
(* Model values for the set of values which will be stored in the tvals      *)
(*****************************************************************************)
ASSUME Assumptions ==
    /\ IsFiniteSet(Values) \* should be a finite set so that TLC could actually check the tvals chain
    /\ Values # {}         \* should not be empty otherwise there is no "chain"

VARIABLE
    values, \* the set of values to take th next tval from
    tval    \* the tvals chain

AnyOf(S) ==
    CHOOSE val \in S : TRUE

(*****************************************************************************)
(* Let any value from the set of model values be the initial val of the      *)
(* tvals chain.                                                              *)
(*****************************************************************************)
TValInit ==
    LET val == AnyOf(Values) IN
    /\ values = Values \ {val}
    /\ tval = TVal0(val, 1)

(*****************************************************************************)
(* There should either be a next value to append to the tvals chain          *)
(* or the chain is complete.                                                 *)
(*****************************************************************************)
TValNext ==
    \/ /\ values # {}
       /\ LET val == AnyOf(values) ttval == TVal1(val, Point(tval) + 1, tval) IN
          /\ values' = values \ {val}
          /\ tval' = ttval
    \/ /\ values = {}
       /\ UNCHANGED <<values, tval>>

RECURSIVE IsaRingAux(_,_,_,_)
IsaRingAux(Dir(_), cur, target, iter) ==
    \/ /\ iter = Len(cur.val)
       /\ Assert(FALSE, "Iterated over the entire chain without finding target. TVal is not circular!")
    \/ Dir(cur) = target
    \/ IsaRingAux(Dir, Dir(cur), target, iter + 1)

IsaRing(cur) ==
    /\ IsaRingAux(Next, cur, cur, 0)
    /\ IsaRingAux(Prior, cur, cur, 0)

(*****************************************************************************)
(* Invariant: TVal should be circular.                                       *)
(*****************************************************************************)
RING_INV == IsaRing(tval)
====