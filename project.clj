(defproject stm.io "0.1.0-SNAPSHOT"
  :description "Extending Software Transactional Memory in Clojure with Side-Effects and Transaction Control."
  :url "https://github.com/arnaudbos/stm.io"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"}
  :scm {:connection "scm:git:https://github.com/arnaudbos/stm.io.git"
        :url "https://github.com/arnaudbos/stm.io"}
  :deploy-repositories  [["releases" :clojars]]
  :lein-release {:deploy-via :clojars}
  :dependencies [[org.clojure/clojure "1.8.0"]]
  :source-paths ["src/clj"]
  :java-source-paths ["src/java"]
  :test-paths ["test/clj"])
