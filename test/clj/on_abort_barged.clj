;   Copyright (c) Arnaud Bos. All rights reserved.
;   The use and distribution terms for this software are covered by the Eclipse
;   Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which
;   can be found in the file epl-v10.html at the root of this distribution. By
;   using this software in any fashion, you are agreeing to be bound by the
;   terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns on-abort-barged
  (:use clojure.test stm.io stm-io-test-helper)
  (:refer-clojure :exclude [dosync]))

(def event-var 0)
(def locked? false)

(use-fixtures :once (fn [f]
                      (alter-var-root #'event-var (constantly 0))
                      (alter-var-root #'locked? (constantly false))
                      (f)))

;; When a transaction T2 barges in on T1, T1 will retry silently in a new loop
;; iteration, then the on-abort events are not executed and two things can
;; happen unpredictably:
;; -> if T2 is not quick enough to commit and release the ref locks then T1
;;    will fail to acquire the locks, throw a RetryEx and run the on-abort
;;    event handlers before retrying again
;; -> if T2 is quick enough, T1 will succeed and the on-abort event handlers
;;    would not have been run
;; The patch made in IOLockingTransaction which explicitly throws a RetryEx
;; when a transaction has been killed rather than retying silently provides
;; a more consistent behaviour.
(deftest on-abort-event-when-barged
  (let [lockbject (Object.)
        thread (Thread.
                 #(dosync
                    (on-abort
                      (alter-var-root #'event-var (constantly 5)))
                    (on-commit
                      ; Let the following tests run
                      (locking lockbject
                        (.notify lockbject)))
                    ; Acquire lock before other thread/txn
                    (alter stm-io-alter-ref identity)
                    ; Cause the main thread to kill this txn and retry
                    (when locked?
                      (locking lockbject
                        (.notify lockbject)
                        (.wait lockbject)
                        ; Ensure main txn has committed before failing to commit
                        (Thread/sleep 10)))))]
    (is (assert-not-retry
          (dosync
            ; This txn starts first
            (Thread/sleep 10)
            ; Start other thread/txn and wait for it to lock the ref first
            (when-not locked?
              (locking lockbject
                (alter-var-root #'locked? (constantly true))
                (.start thread)
                (.wait lockbject)
                ; Prevent the other thread from locking again when retrying
                (alter-var-root #'locked? (constantly false))))
            ; Try to acquire the lock and cause the other txn to abort (barge)
            (alter stm-io-alter-ref inc)
            ; Unlock other thread and commit
            (locking lockbject
              (.notify lockbject)))))
    ; Wait for sub thread/txn to commit before running the remaining tests
    (locking lockbject
      (.wait lockbject))
    (is (== event-var 5))
    (is (== @stm-io-alter-ref 1))))