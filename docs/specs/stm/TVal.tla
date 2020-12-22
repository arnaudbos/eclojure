---- MODULE TVal ----
(*****************************************************************************)
(* Committed Values                                                          *)
(* Each Ref object maintains a chain of its committed values (history) in    *)
(* its tvals field. They represent versions of values for Refs.              *)
(* They are nodes in a doubly-linked, circular list.                         *)
(*                                                                           *)
(* See http://java.ociweb.com/mark/stm/article.html#TVal                     *)
(*                                                                           *)
(* If a Ref is only read inside a transaction, its value is always retrieved *)
(* from the tvals chain. This requires walking the chain to find the newest  *)
(* value that was committed before the current transaction started.          *)
(*                                                                           *)
(* The length of the chain is controlled by the fields minHistory and        *)
(* maxHistory.                                                               *)
(*                                                                           *)
(* minHistory and maxHistory have not been specified yet.                    *)
(*****************************************************************************)

LOCAL INSTANCE Integers
LOCAL INSTANCE Sequences

(*****************************************************************************)
(* When a Ref object is created, its tvals field is set to a TVal object     *)
(* that describes its initial value, not associated with any transaction.    *)
(*                                                                           *)
(* Not being able to specify a doubly-linked, circular list, in TLA+, I made *)
(* TVal a record with three fields:                                          *)
(*                                                                           *)
(* val         An append-only sequence of committed values for the Ref.      *)
(*             Creating a new version of a val is done by appending to the   *)
(*             val sequence and incrementing the cur field (see below).      *)
(* point       An append-only sequence of ordered identifiers of transaction *)
(*             commits. When creating a new version of a val, the read point *)
(*             of the transaction-try is appended to the point sequence.     *)
(* cur         The position of the current val and point. The prior and next *)
(*             values are given by decrementing and incrementing,            *)
(*             respectively, the value of cur modulo it size so as to make   *)
(*             tval a circular list.                                         *)
(*                                                                           *)
(* The Java class for TVal also holds a msecs field:                         *)
(*                                                                           *)
(* msecs:long  This is the creation system time of this object.              *)
(*                                                                           *)
(* Rather than specifying time, conditions involving this long will be       *)
(* encoded as disjunctions for TLC to execute each branch.                   *)
(* We'll see if it works that way.                                           *)
(*****************************************************************************)
TVal0(val, point) ==
    [val |-> <<val>>, point |-> <<point>>, cur |-> 1]

(*****************************************************************************)
(* Additional TVal objects are created in the run method of                  *)
(* LockingTransaction during a commit.                                       *)
(*****************************************************************************)
TVal1(val, point, prior) ==
    [prior EXCEPT
        !.val   = @ \o <<val>>,
        !.point = @ \o <<point>>,
        !.cur   = @ + 1
    ]

(*****************************************************************************)
(* Equivalent of the val field accessor:                                     *)
(* val:Object  This is a committed value for the Ref.                        *)
(*****************************************************************************)
Val(tval) ==
    tval.val[tval.cur]

(*****************************************************************************)
(* Equivalent of the point field accessor:                                   *)
(* point:long  This is an ordered identifier of a transaction commit. Values *)
(*             that were committed in the same transaction will have the     *)
(*             same value for this field.                                    *)
(*****************************************************************************)
Point(tval) ==
    tval.point[tval.cur]

(*****************************************************************************)
(* Equivalent of the prior field accessor:                                   *)
(* prior:TVal  This is a reference to the TVal object that describes the     *)
(*             next older committed version of the Ref or the beginning of   *)
(*             the chain if this is the last node in it.                     *)
(*****************************************************************************)
Prior(tval) ==
    [tval EXCEPT !.cur = IF @ = 1 THEN Len(tval.val) ELSE @ - 1]

(*****************************************************************************)
(* Equivalent of the next field accessor:                                    *)
(* next:TVal   This is a reference to the TVal object that describes the     *)
(*             next newer committed version of the Ref or the end of the     *)
(*             chain if this is the first node in it.                        *)
(*****************************************************************************)
Next(tval) ==
    [tval EXCEPT !.cur = IF @ = Len(tval.val) THEN 1 ELSE @ + 1]

\* expr == Val(Prior(TVal1("bar", 2, TVal0("foo", 1))))

====