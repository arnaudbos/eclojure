stm.io
======

This repository is a library version of **[eClojure](https://github.com/skejserjensen/eclojure)**
that extends the Software Transactional Memory (STM) of Clojure.

Rationale
---------

Clojure's STM is a wonderful piece of software, but managing side-effects inside a Clojure transaction
is tedious or impossible, due to unpredictable retries.

As I was experimenting on a side project, I decided to distribute the computation on multiple nodes,
this is how I've found **[dptClojure](https://github.com/eXeDK/dpt1010f15)** and
(related) **[eClojure](https://github.com/skejserjensen/eclojure)**. Both are extended versions of Clojure.

eClojure is the most recent one and includes a benchmark showing increased execution time of the
STM transactions compared to Clojure's version.

In order to provide the extensions to Clojure's STM contributed in eClojure to Clojure projects
on an opt-in basis, I have repackaged eClojure into this library, rather than a language fork.

Original work
-------------

This repository is a forked and packaged-as-a-library version of **[eClojure](https://github.com/skejserjensen/eclojure)**,
which is itself a fork of Clojure 1.8 that extends the Software Transactional Memory (STM) of Clojure.

> **[eClojure](https://github.com/skejserjensen/eclojure)** is the software complementing the paper **[Extending Software Transactional Memory in Clojure with Side-Effects and Transaction Control](http://dl.acm.org/citation.cfm?id=3005729.3005737)** presented at the **[9th European Lisp Symposium](http://www.european-lisp-symposium.org/editions/2016/)**.
> 
> The main contents is our extended version of Clojure 1.8, named eClojure, that extends the Software Transactional Memory (STM) of Clojure 1.8 in two directions. First support for synchronising side-effects using transactions is made possible through three events emitted: *after-commit*, *on-abort*, and *on-commit*. Second an implementation and extension of multiple transaction control methods pioneered by Haskell, *retry* and *orElse*, is provided.
> 
> For an in-depth description of these two sets of extensions see the above mentioned paper.
>
> -- <cite>eClojure authors</cite>

eClojure is built upon **[dptClojure](https://github.com/eXeDK/dpt1010f15)**, developed as part of the master thesis **[Unifying STM and Side Effects in Clojure](http://projekter.aau.dk/projekter/files/213827517/p10DOC.pdf)**.

Structure
---------

- **src/clj/stm/io.clj:** The Clojure source code for eClojure by the eClojure authors with a few contributions for stm.io, see git history for details.
- **src/java/\*:** The Java source code for eClojure by the eClojure authors with a few contributions for stm.io, see git history for details.
- **test/clj/\*:** Additional Clojure unit tests for eClojure by the eClojure authors with a few contributions for stm.io, see git history for details.
- **overhead:** The source code used to benchmark the overhead of the additions made by the eClojure authors as documented in the paper with a few contributions to adapt it to this library.

License
-------
Distributed under the Eclipse Public License, the same as Clojure.
