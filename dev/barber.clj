(ns barber
  (:refer-clojure :exclude [println])
  (:require [stm.io :as io])
  (:import (clojure.lang PersistentQueue)))

;; Just a utility for multi-threaded printing
(defn println [& args]
  (locking *out*
    (apply clojure.core/println args)))


;; Opening hours
(def open (atom true))

;; Waiting room
(def capacity 9)
(def waiting-room (ref (PersistentQueue/EMPTY)))

;; Utility counter for customers
(def c-counter (atom 0))

;; Start the barber
(defn barber []
  (future
    ; Stop when the shop closes
    (while @open
      (Thread/sleep 100)
      (println "Barber working.")
      (io/dosync
        ; Will be logged at every txn attempt
        (println "Trying to shave.")
        (if-let [customer (first @waiting-room)]
          (do
            (println "Let's shave!")
            ;; Register commit handler first
            (io/on-commit
              (println "Barber is cutting hair of customer" customer "."))
            (alter waiting-room pop))
          (do
            (println "Bummer, waiting room is empty...")
            (io/retry [waiting-room])))))))

;; The universe causes beard to grow
(defn grow-beard []
  (future
    ; But only when the shop is open, bizarrely
    (while @open
      (Thread/sleep 1000)
      (let [customer (swap! c-counter inc)]
        ; Log that a customer is passing through the door
        (println "Customer" customer "is entering the shop.")
        (io/dosync
          (println "Customer" customer "tries to sit.")
          (if (< (count @waiting-room) capacity)
            (do
              (io/on-commit
                (println "Customer" customer "sat in the waiting room."))
              (alter waiting-room conj customer))
            (println "Customer" customer "ragequit: waiting room is full!")))))))

(do (reset! open true)
    (reset! c-counter 0)
    (dosync (alter waiting-room empty))
    (barber)
    (grow-beard)
    (Thread/sleep 10000)
    (reset! open false))

;; Adapted from https://gist.github.com/kachayev/3160721