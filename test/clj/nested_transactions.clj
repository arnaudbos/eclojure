;   Copyright (c) Arnaud Bos. All rights reserved.
;   The use and distribution terms for this software are covered by the Eclipse
;   Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which
;   can be found in the file epl-v10.html at the root of this distribution. By
;   using this software in any fashion, you are agreeing to be bound by the
;   terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns nested-transactions
  "When an alternative M1 (as in `M1 orElse M2`) retries, its effects on the heap
  should be discarded and M2 should be evaluated in the context of the heap
  prior to M1's execution.
  See Harris et al. on STM Haskell `OR3` rule."
  (:use clojure.test)
  (:require [stm.io :refer [dosync or-else or-else-all retry]])
  (:require [stm-io-test-helper :refer [stm-io-test-ref retry-fixture alter-retry-ref]])
  (:refer-clojure :exclude [dosync]))

;;;
;;; isolation
;;;

;; When an alt M1 retries in an `or-else` block
;; due to a `RetryEx` then the alternative M2
;; should be executed in a context (heap) in
;; which M1's effects have not occurred.
;; >>> This test should pass even on dev.
(deftest alternative-should-be-isolated-in-orElse-due-to-RetryEx
  (let [exit-counter (atom 0) ; atoms are out of stm so can be used for side effects
        local-ref    (ref 0)]
    (dosync
      (or-else
        (fn m1 [] (when (< @exit-counter 10) ; exit infinite loop after 10 attempts
                    (swap! exit-counter inc)
                    (retry-fixture
                      #(dosync
                         (alter stm-io-test-ref inc)
                         ;; Doom this alternative
                         (alter-retry-ref)
                         ; Ensure fixture has committed before trying to commit
                         (Thread/sleep 10)))))
        ; never runs because RetryEx not caught by or-else,
        ; so IOLockingTransaction/run catches it and retries plenty,
        ; until exit-counter short-circuits and m1 finally commits
        ; without side effects on stm-io-test-ref
        (fn m2 [] (dosync
                    (alter local-ref inc)))))
    (is (== @exit-counter 10)   "exit-counter should have been incremented 10 times to prove that m1 did retry 10 times")
    (is (== @local-ref 0)       "local-ref shouldn't have been incremented because m2 should not have run at all")
    (is (== @stm-io-test-ref 0) "stm-io-test-ref increments should be \"undone\" because m1 committed but not the inner dosync call")))

;; When an alt M1 retries in an `or-else-all` block
;; due to a `RetryEx` then the alternative M2
;; should be executed in a context (heap) in
;; which M1's effects have not occurred.
;; >>> This test should fail on dev because the effects
;; are not contained properly inside each alternative.
(deftest alternative-should-be-isolated-in-orElseAll-due-to-RetryEx
  (let [local-ref (ref 0)]
    (dosync
      (or-else-all
        (fn m1 [] (retry-fixture
                    #(dosync
                       (alter stm-io-test-ref inc)
                       ;; Doom this alternative
                       (alter-retry-ref)
                       ; Ensure fixture has committed before trying to commit
                       (Thread/sleep 10))))
        ; or-else-all catches `RetryEx` so m2 is executed
        (fn m2 [] (dosync
                    (alter local-ref inc)))))
    (is (== @local-ref 1)       "local-ref should have been incremented because m2 should have committed")
    (is (== @stm-io-test-ref 0) "stm-io-test-ref increments should be \"undone\" because m1 should't have committed")))

;; When an alt M1 retries in an `or-else` block
;; due to a `TCRetryEx` then the alternative M2
;; should be executed in a context (heap) in
;; which M1's effects have not occurred.
;; >>> This test should fail on dev branch because the effects
;; are not contained properly inside each alternative.
(deftest alternative-should-be-isolated-in-orElse-due-to-TxRetryEx
  (let [m1-counter (ref 0)
        m2-counter (ref 0)]
    (dosync
      (or-else
        (fn m1 [] (dosync
                    (alter m1-counter inc)
                    (retry)))
        (fn m2 [] (dosync
                    (alter m2-counter inc)))))
    (is (== @m1-counter 0) "m1-counter increment should be \"undone\" because m1 should't have committed")
    (is (== @m2-counter 1) "m2-counter should have been incremented because m2 should have committed")))

;; When an alt M1 retries in an `or-else-all` block
;; due to a `TCRetryEx` then the alternative M2
;; should be executed in a context (heap) in
;; which M1's effects have not occurred.
;; >>> This test should fail on dev branch because the effects
;; are not contained properly inside each alternative.
(deftest alternative-should-be-isolated-in-orElseAll-due-to-TxRetryEx
  (let [m1-counter (ref 0)
        m2-counter (ref 0)]
    (dosync
      (or-else-all
        (fn m1 [] (dosync
                    (alter m1-counter inc)
                    (retry)))
        (fn m2 [] (dosync
                    (alter m2-counter inc)))))
    (is (== @m1-counter 0) "m1-counter increment should be \"undone\" because m1 should't have committed")
    (is (== @m2-counter 1) "m2-counter should have been incremented because m2 should have committed")))


;; TODO ABOVE: test behavior on random (non-retry) exceptions

;;;
;;; merge
;;;

;; TODO BELOW: fix it, how is orElse supposed to behave?

;; When an alt M1 retries in an `or-else` block
;; due to a `RetryEx` **and** the alternative M2
;; retries due to a `RetryEx` too, then
;; the whole transaction should be retried and
;; executed in a context (heap) in
;; which neither M1 nor M2 effects have occurred.
;; >>> This test should fail on dev because the effects
;; are not contained properly inside each alternative
;; and merge/propagation of retry is not handled.
(deftest transaction-should-be-retried-on-ref-change-when-all-alternative-have-retried-in-orElse-due-to-RetryEx
  (let [m1-exit-counter  (atom 0) ; atoms are out of stm so can be used for side effects
        local-ref      (ref 0)
        m1-locker      (ref false) ; prove merged blocking behaviors unlock and retry tx on change to ref "monitored" in m1
        m2-locker      (ref false) ; prove merged blocking behaviors unlock and retry tx on change to ref "monitored" in m2
        ; unlock a ref but with a slight delay
        delayed-unlock (fn [l]
                         (.start (Thread.
                                   #(dosync
                                      ; Ensure alt of interest has proceeded before unlocking
                                      (Thread/sleep 100)
                                      (ref-set l true)))))]
    (dosync
      (or-else
        (fn m1 [] (when (< @m1-exit-counter 10) ; exit infinite loop after 10 attempts
                    (swap! m1-exit-counter inc)
                    (retry-fixture
                      #(dosync
                         @m1-locker
                         (alter stm-io-test-ref inc)
                         ;; Doom this alternative to ensure m2 runs
                         (alter-retry-ref)
                         ; Ensure fixture has committed before trying to commit
                         (Thread/sleep 10)))))
        (fn m2 [] (dosync
                    (case @retry-counter
                      ; on first attempt, force RetryEx so the whole tx should be retried, and unlock m1-locker so it can actually retry
                      0 (retry-fixture
                          #(dosync
                             (swap! retry-counter inc)
                             (delayed-unlock m1-locker)
                             ;; Doom this alternative to ensure tx retries
                             (alter-retry-ref)
                             ; Ensure fixture has committed before trying to commit
                             (Thread/sleep 10)))
                      ; on second attempt, force RetryEx so the whole tx should be retried, and unlock m2-locker so it can actually retry
                      1 (retry-fixture
                          #(dosync
                             (swap! retry-counter inc)
                             @m2-locker
                             (delayed-unlock m2-locker)
                             ;; Doom this alternative to ensure tx retries
                             (alter-retry-ref)
                             ; Ensure fixture has committed before trying to commit
                             (Thread/sleep 10)))
                      ; on third attempt, commit
                      (alter local-ref inc))))))
    (is @m1-locker              "m1-locker should have been switched after `delayed-unlock` has finished, which in turn should have caused the \"blocking behavior\" to allow the whole tx to retry for the first time")
    (is @m2-locker              "m2-locker should have been switched after `delayed-unlock` has finished, which in turn should have caused the \"blocking behavior\" to allow the whole tx to retry for the second time")
    (is (== @local-ref 1)       "local-ref should have been incremented because m2 should have committed on third attempt")
    (is (== @stm-io-test-ref 0) "stm-io-test-ref increments should be \"undone\" because m1 shouldn't have committed and m2 should have committed only in the default case")))

;; When an alt M1 retries in an `or-else-all` block
;; due to a `RetryEx` **and** the alternative M2
;; retries due to a `RetryEx` too, then
;; the whole transaction should be retried and
;; executed in a context (heap) in
;; which neither M1 nor M2 effects have occurred.
;; >>> This test should fail on dev because the effects
;; are not contained properly inside each alternative
;; and merge/propagation of retry is not handled.
(deftest transaction-should-be-retried-on-ref-change-when-all-alternative-have-retried-in-orElseAll-due-to-RetryEx
  (let [retry-counter  (atom 0) ; atoms are out of stm so can be used for side effects
        local-ref      (ref 0)
        m1-locker      (ref false) ; prove merged blocking behaviors unlock and retry tx on change to ref "monitored" in m1
        m2-locker      (ref false) ; prove merged blocking behaviors unlock and retry tx on change to ref "monitored" in m2
        ; unlock a ref but with a slight delay
        delayed-unlock (fn [l]
                         (.start (Thread.
                                   #(dosync
                                      ; Ensure alt of interest has proceeded before unlocking
                                      (Thread/sleep 100)
                                      (ref-set l true)))))]
    (dosync
      (or-else
        (fn m1 [] (retry-fixture
                    #(dosync
                       @m1-locker
                       (alter stm-io-test-ref inc)
                       ;; Doom this alternative to ensure m2 runs
                       (alter-retry-ref)
                       ; Ensure fixture has committed before trying to commit
                       (Thread/sleep 10))))
        (fn m2 [] (dosync
                    (case @retry-counter
                      ; on first attempt, force RetryEx so the whole tx should be retried, and unlock m1-locker so it can actually retry
                      0 (retry-fixture
                          #(dosync
                             (swap! retry-counter inc)
                             (delayed-unlock m1-locker)
                             ;; Doom this alternative to ensure tx retries
                             (alter-retry-ref)
                             ; Ensure fixture has committed before trying to commit
                             (Thread/sleep 10)))
                      ; on second attempt, force RetryEx so the whole tx should be retried, and unlock m2-locker so it can actually retry
                      1 (retry-fixture
                          #(dosync
                             (swap! retry-counter inc)
                             @m2-locker
                             (delayed-unlock m2-locker)
                             ;; Doom this alternative to ensure tx retries
                             (alter-retry-ref)
                             ; Ensure fixture has committed before trying to commit
                             (Thread/sleep 10)))
                      ; on third attempt, commit
                      (alter local-ref inc))))))
    (is @m1-locker              "m1-locker should have been switched after `delayed-unlock` has finished, which in turn should have caused the \"blocking behavior\" to allow the whole tx to retry for the first time")
    (is @m2-locker              "m2-locker should have been switched after `delayed-unlock` has finished, which in turn should have caused the \"blocking behavior\" to allow the whole tx to retry for the second time")
    (is (== @local-ref 1)       "local-ref should have been incremented because m2 should have committed on third attempt")
    (is (== @stm-io-test-ref 0) "stm-io-test-ref increments should be \"undone\" because m1 shouldn't have committed and m2 should have committed only in the default case")))

;; TODO ABOVE: do it with real conflicts, this is just a copy/paste

;; When an alt M1 retries in an `or-else` block
;; due to a `TCRetryEx` **and** the alternative M2
;; retries due to a `TCRetryEx` too, then
;; the whole transaction should be retried and
;; executed in a context (heap) in
;; which neither M1 nor M2 effects have occurred.
;; >>> This test should fail on dev because the effects
;; are not contained properly inside each alternative
;; and merge/propagation of retry is not handled.
(deftest transaction-should-be-retried-on-ref-change-when-all-alternative-have-retried-in-orElse-due-to-TcRetryEx
  (let [tx-retry-counter (atom 0) ; atoms are out of stm so can be used for side effects
        local-ref        (ref 0)
        m1-locker        (ref false) ; prove merged blocking behaviors unlock and retry tx on change to ref "monitored" in m1
        m2-locker        (ref false) ; prove merged blocking behaviors unlock and retry tx on change to ref "monitored" in m2
        ; unlock a ref but with a slight delay
        delayed-unlock   (fn [l]
                           (.start (Thread.
                                     #(dosync
                                        ; Ensure alt of interest has proceeded before unlocking
                                        (Thread/sleep 100)
                                        (ref-set l true)))))]
    (dosync
      (or-else
        (fn m1 [] (dosync
                    @m1-locker
                    (alter stm-io-test-ref inc)
                    ; doom this alternative to ensure m2 runs
                    (retry)))
        (fn m2 [] (dosync
                    (case @tx-retry-counter
                      ; on first attempt, force TcRetryEx so the whole tx should be retried, and unlock m1-locker so it can actually retry
                      0 (do (swap! tx-retry-counter inc)
                            (delayed-unlock m1-locker)
                            (retry))
                      ; on second attempt, force TcRetryEx so the whole tx should be retried, and unlock m2-locker so it can actually retry
                      1 (do (swap! tx-retry-counter inc)
                            @m2-locker
                            (delayed-unlock m2-locker)
                            (retry))
                      ; on third attempt, commit
                      (alter local-ref inc))))))
    (is @m1-locker              "m1-locker should have been switched after `delayed-unlock` has finished, which in turn should have caused the \"blocking behavior\" to allow the whole tx to retry for the first time")
    (is @m2-locker              "m2-locker should have been switched after `delayed-unlock` has finished, which in turn should have caused the \"blocking behavior\" to allow the whole tx to retry for the second time")
    (is (== @local-ref 1)       "local-ref should have been incremented because m2 should have committed on third attempt")
    (is (== @stm-io-test-ref 0) "stm-io-test-ref increments should be \"undone\" because m1 shouldn't have committed and m2 should have committed only in the default case")))

;; When an alt M1 retries in an `or-else-all` block
;; due to a `TCRetryEx` **and** the alternative M2
;; retries due to a `TCRetryEx` too, then
;; the whole transaction should be retried and
;; executed in a context (heap) in
;; which neither M1 nor M2 effects have occurred.
;; >>> This test should fail on dev because the effects
;; are not contained properly inside each alternative
;; and merge/propagation of retry is not handled.
(deftest transaction-should-be-retried-on-ref-change-when-all-alternative-have-retried-in-orElseAll-due-to-TcRetryEx
  (let [tx-retry-counter (atom 0) ; atoms are out of stm so can be used for side effects
        local-ref        (ref 0)
        m1-locker        (ref false) ; prove merged blocking behaviors unlock and retry tx on change to ref "monitored" in m1
        m2-locker        (ref false) ; prove merged blocking behaviors unlock and retry tx on change to ref "monitored" in m2
        ; unlock a ref but with a slight delay
        delayed-unlock   (fn [l]
                           (.start (Thread.
                                     #(dosync
                                        ; Ensure alt of interest has proceeded before unlocking
                                        (Thread/sleep 100)
                                        (ref-set l true)))))]
    (dosync
      (or-else-all
        (fn m1 [] (dosync
                    @m1-locker
                    (alter stm-io-test-ref inc)
                    ; doom this alternative to ensure m2 runs
                    (retry)))
        (fn m2 [] (dosync
                    (case @tx-retry-counter
                      ; on first attempt, force TcRetryEx so the whole tx should be retried, and unlock m1-locker so it can actually retry
                      0 (do (swap! tx-retry-counter inc)
                            (delayed-unlock m1-locker)
                            (retry))
                      ; on second attempt, force TcRetryEx so the whole tx should be retried, and unlock m2-locker so it can actually retry
                      1 (do (swap! tx-retry-counter inc)
                            @m2-locker
                            (delayed-unlock m2-locker)
                            (retry))
                      ; on third attempt, commit
                      (alter local-ref inc))))))
    (is @m1-locker              "m1-locker should have been switched after `delayed-unlock` has finished, which in turn should have caused the \"blocking behavior\" to allow the whole tx to retry for the first time")
    (is @m2-locker              "m2-locker should have been switched after `delayed-unlock` has finished, which in turn should have caused the \"blocking behavior\" to allow the whole tx to retry for the second time")
    (is (== @local-ref 1)       "local-ref should have been incremented because m2 should have committed on third attempt")
    (is (== @stm-io-test-ref 0) "stm-io-test-ref increments should be \"undone\" because m1 shouldn't have committed and m2 should have committed only in the default case")))


;; TODO ABOVE: test behavior on random (non-retry) exceptions

;; TODO BELOW: OMG...

; contrived plot to simulate some deeply nested
; transactions as one could obtain by arbitrarily
; composing atomic (dosync) functions
(deftest deeply-nested                                      ;should fail without nested-transactions fix
  (let [outer-counter (atom 0)
        m1-counter (atom 0)
        m1-test-ref (ref 0)
        test-ref1 (ref 0)
        test-ref2 (ref 0)]
    (dosync
      (swap! outer-counter inc)
      (or-else-all
        (fn m1 []
          (let [m1-retry-counter (atom 0)
                m1-unblock (ref false)
                m1-thread (Thread.
                            #(dosync
                               ; Ensure main txn has committed before unlocking
                               (Thread/sleep 100)
                               (alter m1-unblock not)))]
            (dosync
              (swap! m1-counter inc)
              (or-else
                (fn m1.1 []                               ;will retry and m1.2 will run
                  (dosync
                    @m1-unblock
                    (alter m1-test-ref inc)
                    (retry)))
                (fn m1.2 []
                  (dosync
                    @m1-unblock
                    (if (= 0 @m1-retry-counter)
                      ; Force m1 to block and restart both m1.1 and m1.2 (so m1-counter goes up to 2)
                      (do (swap! m1-retry-counter inc)
                          (alter m1-test-ref inc)
                          (.start m1-thread)
                          (retry))
                      ; Exit m1 orElse with a uncaught RetryEx, a TCRetryEx would cause m1 to retry on and on
                      (retry-fixture
                        #(dosync
                           (alter test-ref1 inc)
                           (alter stm-io-test-ref inc)
                           ;; Doom this alternative
                           (alter-retry-ref)
                           ; Ensure fixture txn has committed before trying to commit
                           (Thread/sleep 10))))))))))
        (fn m2 []
          (alter test-ref2 inc))))
    (is (== @outer-counter 1))
    (is (== @m1-counter 2))
    (is (== @test-ref1 0))
    (is (== @test-ref2 1))))