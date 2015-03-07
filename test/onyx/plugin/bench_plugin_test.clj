(ns onyx.plugin.bench-plugin-test
  (:require [clojure.core.async :refer [chan dropping-buffer put! >! <! <!! go >!!]]
            [onyx.peer.task-lifecycle-extensions :as l-ext]
            [onyx.peer.pipeline-extensions :as p-ext]
            [onyx.plugin.bench-plugin]
            [onyx.plugin.core-async]
            [onyx.api]))

(def id (java.util.UUID/randomUUID))

(def scheduler :onyx.job-scheduler/greedy)

(def messaging :aleph-tcp)

(def env-config
  {:zookeeper/address "127.0.0.1:2189"
   :zookeeper/server? true
   :zookeeper.server/port 2189
   :onyx/id id
   :onyx.peer/job-scheduler scheduler})

(def peer-config
  {:zookeeper/address "127.0.0.1:2189"
   :onyx/id id
   ;:onyx.peer/join-failure-back-off 500
   :onyx.peer/job-scheduler scheduler
   :onyx.messaging/impl messaging})

(def n-messages 100)

(def batch-size 200)
(def batch-timeout 50)

(def counter (atom 0))

(defn my-inc [{:keys [n] :as segment}]
  (swap! counter inc)
  (assoc segment :n (inc n)))

(def catalog
  [{:onyx/name :in
    :onyx/ident :generator/generator
    :onyx/type :input
    :onyx/medium :generator
    :onyx/consumption :concurrent
    :onyx/batch-timeout batch-timeout
    :onyx/batch-size batch-size}

   {:onyx/name :inc
    :onyx/fn :onyx.plugin.bench-plugin-test/my-inc
    :onyx/type :function
    :onyx/consumption :concurrent
    :onyx/batch-timeout batch-timeout
    :onyx/batch-size batch-size}

   {:onyx/name :no-op
    :onyx/ident :core.async/write-to-chan
    :onyx/type :output
    :onyx/medium :core.async
    :onyx/consumption :concurrent
    :onyx/batch-timeout batch-timeout
    :onyx/batch-size batch-size
    :onyx/doc "Drops messages on the floor"}])

;; TO TEST

(defmethod l-ext/inject-lifecycle-resources :no-op
  [_ _] {:core.async/out-chan (chan (dropping-buffer 1))})

(def workflow [[:in :inc] [:inc :no-op]])

(println "Starting env")
(def env (onyx.api/start-env env-config))
(println "starting Vpeers")

(def v-peers (onyx.api/start-peers 3 peer-config))
(println "Started vpeers")

(Thread/sleep 10000)

(def start-time (java.util.Date.))

(println "Started at " (java.util.Date.))
(onyx.api/submit-job
  peer-config
  {:catalog catalog 
   :workflow workflow
   :task-scheduler :onyx.task-scheduler/round-robin})

(Thread/sleep 45000)
(println "Done at " start-time (java.util.Date.) @counter)

(doseq [v-peer v-peers]
    (onyx.api/shutdown-peer v-peer))

(onyx.api/shutdown-env env)
