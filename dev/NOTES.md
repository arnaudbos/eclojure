## What is the desired behavior of `orElse`?

The underlying question is: does it make sense to have different behaviors for `orElse` and `orElseAll`?

`orElseAll` will "park" the failure of an inner alternative (nested transaction) by undoing its observable side-effects
and remembering the refs it is observing until either another alternative succeeds or all alternatives inside the
transaction fail (`TCRetryEx` or `RetryEx`). In the second case, it will block the transaction (and will block the
thread too but it shouldn't last long, and is another problem entirely anyways) from retrying until one of the refs of any of the
transactions (or all) are touched.

Wait... Actually the whole remembering and block the transaction thing is true only in case `(retry)` has been called explicitly,
otherwise the next alternative will just execute and if all fail the `TCRetryEx` thrown will be caught by `run`
just like a regular `RetryEx` and the transaction will retry, just without blocking.

So running `orElse` without throwing a `TCRetryEx` via `(retry)` is actually the same as not using `orElse`, at all.
Running `orElse` and throwing a `TCRetryEx` will go to the next alternative and block on retry of the transaction
if all alternatives fail.
I'm really wondering if it makes sense at all to have to explicitly call retry. Anyways.

Currently, in an `orElse`, if an alternative calls `(retry)` and the next alternative doesn't but is forced to restart,
the whole transaction
will be restarted and will block only on the refs touched by