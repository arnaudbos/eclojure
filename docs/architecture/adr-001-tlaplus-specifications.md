# ADR 001: TLA+ Specifications

## Status

* 2020/11/14: Accepted

## Context

When a retry branch of a transaction causes side effects on some
references, those side effects are visible from a subsequent branch
execution in the same transaction.  
This is a violation of Harris et al.'s semantic.

During my attemps at fixing this bug, I discovered two things:

1. I don't have so much time to work on this

Which means that I periodically leave the work in progress. When I
come back to it (many days/weeks/months later) I have to wrap my head
around the whole thing once again.  
Considering the nature of this library and the bug itself, this process
costs me too much.

2. I'm not sure that a fix for this bug wouldn't break another safety
   property

During my latest attempt at fixing this I did deactivate other parts
of the extended STM in order to focus on the retry property.  
Not only am I not sure that the fix I was attempting would not break
another property of the STM's "foundations", but I also wasn't sure
that simply re-enabling the other features would work.

## Decision

The decision is twofold.

### Use TLA+

I have decided to put my trust in TLA+, which I've had success with
at least once prior, in order to specify Clojure's STM.

Once this is done, I will proceed to specify the extended STM as a
refinement of Clojure's STM.

This will bring at least four benefits. I will:

1. have fun specifying Clojure's STM
2. have a better (formal) understanding of the properties
   that this extended version should exhibit
3. be able to reproduce the bug that I have found and design
   a solution "above the code"
4. be confident that this extended STM satisfies the properties
   of the STM it is built upon

A very nice corollary would be to complement the specifications
with a formal proof using TLAPS in some distant future.

### Layout

[Verification-Driven Development](https://github.com/informalsystems/vdd) by
[Informal Systems](https://informal.systems/) is the only attempt (for lack
of a better word) I've seen so far at organizing specifications, so I've
decided to try that.

## Status

Accepted

## Consequences

### Positive

* Gain a much better understanding of the algorithm of both Clojure's
  STM and the extension built on top of it
* A test bed for design experimentations
* Confidence in their design and correctness
* Get more experience in TLA+

### Negative

* A TLA+ specification is a statement of correctness of the design,
  not the code, and only for as far as the model checker/state space
  has been expanded, it is not a proof of correctness
