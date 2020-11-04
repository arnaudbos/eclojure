# Clojure STM Specification

This directory contains English and TLA+ specifications for the Clojure
STM as it is currently implemented in the Clojure codebase.

These are, obviously, unofficial specifications written after the fact and
inferred from the existing codebase and other [resources](#references).

## English Specification

The [English Specification](stm.md) provides a detailed description of the
STM problem and the properties a correct protocol must satisfy. It also
includes a detailed description of the protocol as currently implemented in
Clojure, and an analysis of the implementation with respect to the properties.

## TLA+ Specification

## References

[[Mark Volkmann's Software Transactional Memory (STM) Page]]

[[clojure.lang.LockingTransaction (source code)]]

[Mark Volkmann's Software Transactional Memory (STM) Page]: http://java.ociweb.com/mark/stm/

[clojure.lang.LockingTransaction (source code)]: https://github.com/clojure/clojure/blob/4ef4b1ed7a2e8bb0aaaacfb0942729252c2c3091/src/jvm/clojure/lang/LockingTransaction.java