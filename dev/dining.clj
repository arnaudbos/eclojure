(ns dining
  (:refer-clojure :exclude [println])
  (:require [stm.io :as io]))

(def n-philosophers 5)
(def txn-attempts (atom 0))
(def txn-success (ref 0))
(def running (atom true))
(def forks (for [_ (range n-philosophers)]
             (ref false)))

(defn println [& args]
  (locking *out*
    (apply clojure.core/println args)))

(declare get-forks eat think)
(defn philosopher [n]
  (while @running
    (when (get-forks n)
      (eat n)
      (think n)))
  (Thread/sleep 100))

(defn think [n]
  (println "Philosopher" n "is thinking...")
  (Thread/sleep 200))

(declare left-fork right-fork)
(defn get-forks [n]
  (let [l (left-fork n)
        r (right-fork n)]
    (io/dosync
      (swap! txn-attempts inc)
      (println "Philosopher" n "is trying to eat")
      (if-not (and @l @r)
        (do (commute txn-success inc)
        (ref-set l true)
        (ref-set r true))
        (io/retry [l r])))))

(defn left-fork [n]
  (nth forks (mod (dec n) n-philosophers)))

(defn right-fork [n]
  (nth forks n))

(declare release-forks)
(defn eat [n]
  (println "Philosopher" n "is eating...")
  (Thread/sleep 200)
  (release-forks n))

(declare release-fork)
(defn release-forks [n]
  (io/dosync
    (release-fork (left-fork n))
    (release-fork (right-fork n))))

(defn release-fork [fork]
  (ref-set fork false))

(defn start! []
  (dotimes [i n-philosophers]
    (.start (Thread. #(philosopher i)))))

(do (reset! running true)
    (reset! txn-attempts 0)
    (dosync (ref-set txn-success 0))
    (dosync (dorun (map #(ref-set % false) forks)))
    (start!)
    (Thread/sleep 30000)
    (reset! running false))
@txn-attempts
@txn-success

;; Example adapted from work by "biellls" on Github: https://gist.github.com/biellls/e81e3d09ed48b32acefc
