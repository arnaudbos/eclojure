#!/bin/bash
#   Copyright (c) Daniel Rune Jensen, Thomas Stig Jacobsen and
#   Søren Kejser Jensen. All rights reserved.
#   The use and distribution terms for this software are covered by the Eclipse
#   Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which
#   can be found in the file epl-v10.html at the root of this distribution. By
#   using this software in any fashion, you are agreeing to be bound by the
#   terms of this license.
#   You must not remove this notice, or any other, from this software.

stm_io="$root/target/stm.io-0.1.0-SNAPSHOT.jar"
root=$(git rev-parse --show-toplevel)

# Sets the path of the Clojure jar and the Criterium jar
clojure="$root/overhead/libraries/clojure-1.8.0.jar"
criterium="$root/overhead/libraries/criterium-0.4.3.jar"

# Sets the path of the two benchmarks
clojure_bench="$root/overhead/benchmark_overhead_clojure.clj"
stm_io_bench="$root/overhead/benchmark_overhead_stm_io.clj"

# Verifies that the necessary jar files are available
if [[ ! -f "$clojure" || ! -f $criterium ]]
then
    echo "ERROR: please ensure the necessary jars are available"
    exit -1
fi
if [[ ! -f "$stm_io" ]]
then
    echo "ERROR: please ensure stm.io jar is available (use lein jar)"
    exit -1
fi

# Stores time stamp for grouping the two experiments in the folder results
timestamp=$(date -u +"%Y-%m-%dT%H-%M-%SZ")
mkdir -p "$root/overhead/results"

# Executes the benchmarks using Clojure STM
echo "Running Clojure STM Benchmarks: $clojure_bench"
java -cp "$clojure:$criterium":. clojure.main "$clojure_bench" > "$root/overhead/results/$timestamp-clojure-1.8.0.txt"

# Executes the benchmarks using stm.io STM
echo "Running stm.io STM Benchmarks: $stm_io_bench"
java -cp "$clojure:$stm_io:$criterium":. clojure.main "$stm_io_bench" > "$root/overhead/results/$timestamp-stm-io-1.8.0.txt"
