# Specifications

This repository contains - or rather will, I hope, contain - English and TLA+
specifications for Clojure's built-in STM as well as stm.io's extensions:

- [stm](./stm) - Clojure's Snapshot and Serializable Snapshot Isolation
  MVCC STM
- [stm.io](./stm.io) - Basically a Twilight STM (side-effects) as per Bieniusa
  et al., along with transaction control (Haskell's retry, orElse) as per
  Harris et al. on top of Clojure's STM