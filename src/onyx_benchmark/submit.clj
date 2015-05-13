(ns onyx-benchmark.submit
  (:require [clojure.core.async :refer [chan dropping-buffer <!!]]
            [clojure.data.fressian :as fressian]
            [onyx.peer.pipeline-extensions :as p-ext]
            [onyx.plugin.bench-plugin]
            [onyx.plugin.core-async]
            [onyx.peer.operation :as op ]
            [onyx-benchmark.peer]
            [onyx.api]))

(def lifecycles
  [{:lifecycle/task :in
    :lifecycle/calls :onyx-benchmark.peer/measurement-calls}
   {:lifecycle/task :in
    :lifecycle/calls :onyx.plugin.bench-plugin/reader-calls}
   {:lifecycle/task :inc1
    :lifecycle/calls :onyx-benchmark.peer/measurement-calls}
   {:lifecycle/task :inc2
    :lifecycle/calls :onyx-benchmark.peer/measurement-calls}
   {:lifecycle/task :inc3
    :lifecycle/calls :onyx-benchmark.peer/measurement-calls}
   {:lifecycle/task :inc4
    :lifecycle/calls :onyx-benchmark.peer/measurement-calls}
   {:lifecycle/task :no-op
    :lifecycle/calls :onyx.plugin.core-async/writer-calls}
   {:lifecycle/task :no-op
    :lifecycle/calls :onyx-benchmark.peer/measurement-calls}
   {:lifecycle/task :no-op
    :lifecycle/calls :onyx-benchmark.peer/no-op-calls}])

(defn -main [zk-addr id batch-size & args]
  (let [batch-size (Integer/parseInt batch-size)]

    (def peer-config
      {:zookeeper/address zk-addr
       :onyx/id id
       :onyx.messaging/bind-addr "127.0.0.1" ;(slurp "http://169.254.169.254/latest/meta-data/local-ipv4")
       :onyx.messaging/peer-ports (vec (range 40000 40200))
       :onyx.peer/join-failure-back-off 500
       :onyx.peer/job-scheduler :onyx.job-scheduler/greedy
       :onyx.messaging/impl :netty})

    (def catalog
      [{:onyx/name :in
        :onyx/ident :generator
        :onyx/type :input
        :onyx/medium :generator
        :onyx/consumption :concurrent
        :onyx/batch-size batch-size}

       {:onyx/name :inc1
        :onyx/fn :onyx-benchmark.peer/my-inc
        :onyx/type :function
        :onyx/consumption :concurrent
        :onyx/batch-size batch-size}

       {:onyx/name :inc2
        :onyx/fn :onyx-benchmark.peer/my-inc
        :onyx/type :function
        :onyx/consumption :concurrent
        :onyx/batch-size batch-size}
       
       {:onyx/name :inc3
        :onyx/fn :onyx-benchmark.peer/my-inc
        :onyx/type :function
        :onyx/consumption :concurrent
        :onyx/batch-size batch-size}

       {:onyx/name :inc4
        :onyx/fn :onyx-benchmark.peer/my-inc
        :onyx/type :function
        :onyx/consumption :concurrent
        :onyx/batch-size batch-size}

       {:onyx/name :no-op
        :onyx/ident :core.async/write-to-chan
        :onyx/type :output
        :onyx/medium :core.async
        :onyx/consumption :concurrent
        :onyx/batch-size batch-size
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
       :task-scheduler :onyx.task-scheduler/balanced})))

