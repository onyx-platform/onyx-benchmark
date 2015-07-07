(ns onyx-benchmark.submit
  (:require [clojure.core.async :refer [chan dropping-buffer <!!]]
            [onyx.peer.pipeline-extensions :as p-ext]
            [onyx.plugin.bench-plugin]
            [onyx.plugin.core-async]
            [onyx.peer.operation :as op ]
            [onyx-benchmark.peer]
            [onyx.api]))

(def lifecycles
  [{:lifecycle/task :in
    :lifecycle/calls :onyx-benchmark.peer/retry-calls}
   {:lifecycle/task :in
    :lifecycle/calls :onyx-benchmark.peer/latency-calls}
   {:lifecycle/task :in
    :lifecycle/calls :onyx-benchmark.peer/throughput-calls}
   {:lifecycle/task :in
    :lifecycle/calls :onyx.plugin.bench-plugin/reader-calls}
   {:lifecycle/task :inc1
    :lifecycle/calls :onyx-benchmark.peer/throughput-calls}
   {:lifecycle/task :inc2
    :lifecycle/calls :onyx-benchmark.peer/throughput-calls}
   {:lifecycle/task :inc3
    :lifecycle/calls :onyx-benchmark.peer/throughput-calls}
   {:lifecycle/task :inc4
    :lifecycle/calls :onyx-benchmark.peer/throughput-calls}
   {:lifecycle/task :no-op
    :lifecycle/calls :onyx.plugin.core-async/writer-calls}
   {:lifecycle/task :no-op
    :lifecycle/calls :onyx-benchmark.peer/throughput-calls}
   {:lifecycle/task :no-op
    :lifecycle/calls :onyx-benchmark.peer/no-op-calls}])


(def batch-timeout 100)

(defn -main [zk-addr id batch-size & args]
  (let [batch-size (Integer/parseInt batch-size)]


    (def peer-config
      {:zookeeper/address zk-addr
       :onyx/id id
       :onyx.messaging/bind-addr "127.0.0.1"
       :onyx.messaging/peer-ports (vec (range 40000 40200))
       :onyx.peer/join-failure-back-off 500
       :onyx.peer/job-scheduler :onyx.job-scheduler/greedy
       :onyx.messaging/impl :netty})

    (def env-config
      (assoc peer-config 
             :zookeeper/server? false
             :zookeeper.server/port 2189))

    (def env (when (:zookeeper/server? env-config) 
               (println "Starting env at " env-config)
               (onyx.api/start-env env-config)))

    (println "Done starting env")

    (def catalog
      [{:onyx/name :in
        :onyx/plugin :onyx.plugin.bench-plugin/generator
        :onyx/type :input
        :onyx/max-pending 50000
        :onyx/medium :generator
        :onyx/batch-timeout batch-timeout
        :onyx/batch-size batch-size}

       {:onyx/name :inc1
        :onyx/fn :onyx-benchmark.peer/my-inc
        :onyx/type :function
        :onyx/batch-timeout batch-timeout
        :onyx/batch-size batch-size}

       {:onyx/name :inc2
        :onyx/fn :onyx-benchmark.peer/my-inc
        :onyx/type :function
        :onyx/batch-timeout batch-timeout
        :onyx/batch-size batch-size}

       {:onyx/name :inc3
        :onyx/fn :onyx-benchmark.peer/my-inc
        :onyx/type :function
        :onyx/batch-timeout batch-timeout
        :onyx/batch-size batch-size}

       {:onyx/name :inc4
        :onyx/fn :onyx-benchmark.peer/my-inc
        :onyx/type :function
        :onyx/batch-timeout batch-timeout
        :onyx/batch-size batch-size}

       {:onyx/name :no-op
        :onyx/plugin :onyx.plugin.core-async/output
        :onyx/type :output
        :onyx/medium :core.async
        :onyx/batch-size batch-size
        :onyx/batch-timeout batch-timeout
        :onyx/doc "Drops messages on the floor"}])

    (def workflow [[:in :inc1] 
                   [:inc1 :inc2] 
                   [:inc2 :inc3]
                   [:inc3 :inc4]
                   [:inc4 :no-op]])

    (onyx.api/submit-job
      peer-config
      {:catalog catalog 
       :workflow workflow
       :lifecycles lifecycles
       ;:acker/percentage 60 
       ;:acker/exempt-input-tasks? true
       :task-scheduler :onyx.task-scheduler/balanced})))
