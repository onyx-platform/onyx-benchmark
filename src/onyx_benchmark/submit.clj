(ns onyx-benchmark.submit
  (:require [clojure.core.async :refer [chan dropping-buffer <!!]]
            [clojure.data.fressian :as fressian]
            [onyx.peer.task-lifecycle-extensions :as l-ext]
            [onyx.peer.pipeline-extensions :as p-ext]
            [onyx.plugin.bench-plugin]
            [onyx.plugin.core-async]
            [onyx.api]))

(defn -main [zk-addr riemann-host id batch-size & args]
  (let [batch-size (Integer/parseInt batch-size)]

    (def peer-config
      {:zookeeper/address zk-addr
       :onyx/id id
       :onyx.messaging/impl :aleph-tcp})

    (def catalog
      [{:onyx/name :in
        :onyx/ident :generator/generator
        :onyx/type :input
        :onyx/medium :generator
        :onyx/consumption :concurrent
        :onyx/batch-size batch-size}

       {:onyx/name :inc
        :onyx/fn :onyx-benchmark.peer/my-inc
        :onyx/type :function
        :onyx/consumption :concurrent
        :bench/riemann riemann-host
        :onyx/batch-size batch-size}

       {:onyx/name :no-op
        :onyx/ident :core.async/write-to-chan
        :onyx/type :output
        :onyx/medium :core.async
        :onyx/consumption :concurrent
        :onyx/batch-size batch-size
        :onyx/doc "Drops messages on the floor"}])

    (def workflow [[:in :inc] [:inc :no-op]])

    (onyx.api/submit-job
     peer-config
     {:catalog catalog :workflow workflow
      :task-scheduler :onyx.task-scheduler/round-robin})))

