# Clojure STM

Software Transactional Memory is a concurrency control mechanism to
ensure safe coordination over shared mutable state.

Clojure's STM is an optimistic, lock-free, serializable STM which
uses MVCC to provide Snapshot Isolation (SI) and optional Serializable
Snapshot Isolation (SSI).

It also provides performance optimizations for commutative operations.

> Clojure transactions should be easy to understand if you’ve ever used
> database transactions - they ensure that all actions on Refs are atomic,
> consistent, and isolated. Atomic means that every change to Refs made within
> a transaction occurs or none do. Consistent means that each new value can be
> checked with a validator function before allowing the transaction to commit.
> Isolated means that no transaction sees the effects of any other transaction
> while it is running. Another feature common to STMs is that, should a
> transaction have a conflict while running, it is automatically retried.
> — https://clojure.org/reference/refs

## Outline

- [Part I](#part-i---clojure-stm): Introduction of STM terms that
are relevant for Clojure STM protocol.

# References

<!--
> links to other specifications/ADRs this document refers to
---->