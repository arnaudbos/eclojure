(ns banking
  (:refer-clojure :exclude [println])
  (:require [stm.io :as io]))

;; Just a utility for multi-threaded printing
(defn println [& args]
  (locking *out*
    (apply clojure.core/println args)
    ))

;; Create 2 bank accounts
(def acc1 (ref {:name "acc1"
                :amount 100}))
(def acc2 (ref {:name "acc2"
                :amount 0}))

;; Create an overdraft fee accumulator
(def overdraft-fee (ref 0))

;; How much money is there?
(println (:amount @acc1) (:amount @acc2))
;; => 100 0

;; How much overdraft fee?
(println @overdraft-fee)

(defn update-account [op amount]
  #(update % :amount op amount))

;; Check if withdraw is more than current balance
(defn check-not-overdraw [acc amount]
  (>= (:amount @acc) amount 0))

;; Withdraw only if not overdraws the account
(defn limited-withdraw [acc amount]
  (io/dosync
    (println "Trying to withdraw" amount "from" (:name @acc) "without overdraft")
    (io/on-abort (println "Withdraw aborted"))
    (io/on-commit (println "Withdrawn" amount "from" (:name @acc)))
    (if (check-not-overdraw acc amount)
      (alter acc (update-account - amount))
      (io/retry [acc] #(check-not-overdraw acc amount)))))

;; Withdraw anyways but apply overdraft-fee if withdraw is more than current balance
(defn unlimited-withdraw [acc amount]
  (io/dosync
    (println "Trying to withdraw" amount "from" (:name @acc))
    (io/on-abort (println "Withdraw aborted"))
    (io/on-commit (println "Withdrawn" amount "from" (:name @acc)))
    (alter acc (update-account - amount))
    (when (< (:amount @acc) 0)
      (println "Overdrawn")
      (io/on-commit (println "Overdrawn"))
      (commute overdraft-fee + 10))))

;; Safely withdraw from account 1 or withdraw from account 2 with risk of overdraft
(defn withdraw [a1 a2 amount]
  (io/dosync
    (io/or-else
      #(limited-withdraw a1 amount)
      #(unlimited-withdraw a2 amount))
    amount)) ; return amount from dosync block and function (just for fun)

;; Try to withdraw $50
(withdraw acc2 acc1 50)
;; => nil
;; Withdraw aborted

;; Now transfer $100
(.start
  (Thread.
    #(transfer-money acc1 acc2 30)))
;; => nil
;; Transferred 100 from acc1 to acc2
;; Trying to withdraw 50 from acc2
;; Withdrawn 50 from acc2

;; Check account balances again
(println (:amount @acc1) (:amount @acc2))
;; => 0 50

;; Check overdraft fee?
(println @overdraft-fee)

;; => We can see that withdraw was blocked until `transfer-money` successfully
;;    updated acc2's balance.
