(ns onyx-benchmark.submit-linear
  (:require [clojure.core.async :refer [chan dropping-buffer <!!]]
            [onyx.peer.pipeline-extensions :as p-ext]
            [onyx.plugin.bench-plugin]
            [onyx.plugin.core-async]
            [onyx.peer.operation :as op]
            [onyx-benchmark.peer]
            [onyx.api]))

(defn build-lifecycles [riemann-host riemann-port]
  [{:lifecycle/task :in
    :lifecycle/calls :onyx.plugin.bench-plugin/reader-calls}
   {:lifecycle/task :no-op
    :lifecycle/calls :onyx-benchmark.peer/no-op-calls}
   {:lifecycle/task :no-op
    :lifecycle/calls :onyx.plugin.core-async/writer-calls
    :core.async/allow-unsafe-concurrency? true}
   {:lifecycle/task :all
    :lifecycle/calls :onyx.lifecycle.metrics.metrics/calls
    :metrics/buffer-capacity 10000
    :metrics/workflow-name "bench-workflow"
    :metrics/sender-fn :onyx.lifecycle.metrics.riemann/riemann-sender
    :riemann/address riemann-host
    :riemann/port riemann-port
    :lifecycle/doc "Instruments a task's metrics and sends via riemann"}])

(defn -main
  [peer-config-file job-config-file zk-addr riemann-addr riemann-port id batch-size max-pending & args]
  (let [batch-size (Integer/parseInt batch-size)
        max-pending (Integer/parseInt max-pending)
        peer-config (read-string (slurp peer-config-file))
        job-config (read-string (slurp job-config-file))
        catalog [{:onyx/name :in
                  :onyx/plugin :onyx.plugin.bench-plugin/generator
                  :onyx/type :input
                  :onyx/max-pending max-pending
                  :benchmark/segment-generator :hundred-bytes
                  :onyx/medium :generator
                  :onyx/batch-size batch-size}

                 {:onyx/name :inc1
                  :onyx/fn :onyx-benchmark.peer/my-inc
                  :onyx/type :function
                  :onyx/batch-size batch-size}

                 {:onyx/name :inc2
                  :onyx/fn :onyx-benchmark.peer/my-inc
                  :onyx/type :function
                  :onyx/batch-size batch-size}

                 {:onyx/name :inc3
                  :onyx/fn :onyx-benchmark.peer/my-inc
                  :onyx/type :function
                  :onyx/batch-size batch-size}

                 {:onyx/name :inc4
                  :onyx/fn :onyx-benchmark.peer/my-inc
                  :onyx/type :function
                  :onyx/batch-size batch-size}

                 {:onyx/name :no-op
                  :onyx/plugin :onyx.plugin.core-async/output
                  :onyx/batch-size batch-size
                  :onyx/type :output
                  :onyx/medium :core.async
                  :onyx/doc "Drops messages on the floor"}]
        workflow [[:in :inc1] 
                  [:inc1 :inc2] 
                  [:inc2 :inc3]
                  [:inc3 :inc4]
                  [:inc4 :no-op]]
        lifecycles (build-lifecycles riemann-addr (Integer/parseInt riemann-port))]

    (onyx.api/submit-job peer-config
                         (merge
                          {:catalog catalog 
                           :workflow workflow
                           :lifecycles lifecycles}
                          job-config))
    (println "Job successfully submitted")
    (shutdown-agents)))
