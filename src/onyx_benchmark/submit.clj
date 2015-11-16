(ns onyx-benchmark.submit
  (:require [clojure.core.async :refer [chan dropping-buffer <!!]]
            [onyx.peer.pipeline-extensions :as p-ext]
            [clojure.core.async :refer [chan dropping-buffer <!!]]
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
    :lifecycle/calls :onyx.plugin.core-async/writer-calls}
   {:lifecycle/task :all ; or :task-name for an individual task
    :lifecycle/calls :onyx.lifecycle.metrics.metrics/calls
    :metrics/buffer-capacity 10000
    :metrics/workflow-name "your-workflow-name"
    :metrics/sender-fn :onyx.lifecycle.metrics.timbre/timbre-sender
    :lifecycle/doc "Instruments a task's metrics to timbre"}
   #_{:lifecycle/task :all
    :lifecycle/calls :onyx.lifecycle.metrics.metrics/calls
    :metrics/buffer-capacity 10000
    :metrics/workflow-name "bench-workflow"
    :metrics/sender-fn :onyx.lifecycle.metrics.riemann/riemann-sender
    :riemann/address riemann-host
    :riemann/port riemann-port
    :lifecycle/doc "Instruments a task's metrics and sends via riemann"}])

;; FIXME REMEMBER RIEMANN IN SUBMITSUBMITSUMBIT

(defn -main [zk-addr riemann-addr riemann-port id batch-size & args]
  (let [batch-size (Integer/parseInt batch-size)
        peer-config {:zookeeper/address zk-addr
                     :onyx/id id
                     :onyx.messaging/bind-addr "127.0.0.1"
                     :onyx.messaging/peer-port 40000
                     :onyx.peer/join-failure-back-off 500
                     :onyx.peer/job-scheduler :onyx.job-scheduler/greedy
                     :onyx.messaging/impl :aeron}
        env-config (assoc peer-config 
                          :zookeeper/server? (= zk-addr "127.0.0.1:2189")
                          :zookeeper.server/port 2189)
        env (when (:zookeeper/server? env-config) 
              (println "Starting env at " env-config)
              (onyx.api/start-env env-config))
        catalog [{:onyx/name :in
                  :onyx/plugin :onyx.plugin.bench-plugin/generator
                  :onyx/type :input
                  :onyx/max-pending 10000
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
                  :onyx/max-peers 1
                  :onyx/type :output
                  :onyx/medium :core.async
                  :onyx/batch-size batch-size
                  :onyx/doc "Drops messages on the floor"}]
        workflow [[:in :inc1] 
                  [:inc1 :inc2] 
                  [:inc2 :inc3]
                  [:inc3 :inc4]
                  [:inc4 :no-op]]
        lifecycles (build-lifecycles riemann-addr (Integer/parseInt riemann-port))]

    (onyx.api/submit-job peer-config
                         {:catalog catalog 
                          :workflow workflow
                          :lifecycles lifecycles
                          :acker/percentage 20 
                          :acker/exempt-input-tasks? true
                          :task-scheduler :onyx.task-scheduler/balanced})
    (<!! (chan))
    ))
