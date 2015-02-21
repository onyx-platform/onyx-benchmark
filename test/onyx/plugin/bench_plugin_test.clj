(ns onyx.plugin.bench-plugin-test
  (:require [clojure.core.async :refer [chan dropping-buffer]]
            [clojure.data.fressian :as fressian]
            [onyx.peer.task-lifecycle-extensions :as l-ext]
            [onyx.peer.pipeline-extensions :as p-ext]
            [onyx.plugin.bench-plugin]
            [onyx.plugin.core-async]
            [onyx.api]))

(def id (java.util.UUID/randomUUID))

(def scheduler :onyx.job-scheduler/greedy)

(def messaging :http-kit)

(def env-config
  {:zookeeper/address "127.0.0.1:2189"
   :zookeeper/server? true
   :zookeeper.server/port 2189
   :onyx/id id
   :onyx.peer/job-scheduler scheduler
   :onyx.messaging/impl messaging})

(def peer-config
  {:zookeeper/address "127.0.0.1:2189"
   :onyx/id id
   :onyx.peer/join-failure-back-off 500
   :onyx.peer/job-scheduler scheduler
   :onyx.messaging/impl messaging})

(def env (onyx.api/start-env env-config))

(def n-messages 100)

(def batch-size 20)

(defn my-inc [{:keys [n] :as segment}]
  (prn n)
  (assoc segment :n (inc n)))

(def catalog
  [{:onyx/name :in
    :onyx/ident :http/listen
    :onyx/type :input
    :onyx/medium :http
    :onyx/consumption :concurrent
    :onyx/batch-size batch-size
    :onyx/doc "Reads segments from an HTTP endpoint"}

   {:onyx/name :inc
    :onyx/fn :onyx.plugin.bench-plugin-test/my-inc
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

(def workflow [[:in :inc] [:inc :no-op]])

(def ports (atom 49999))

(defmethod l-ext/inject-lifecycle-resources :in
  [_ _] {:http/port (swap! ports inc)})

(defmethod l-ext/inject-lifecycle-resources :no-op
  [_ _] {:core.async/out-chan (chan (dropping-buffer 1))})

(onyx.api/start-peers! 3 peer-config)

(onyx.api/submit-job
 peer-config
 {:catalog catalog :workflow workflow
  :task-scheduler :onyx.task-scheduler/round-robin})


(comment
  
  (doseq [v-peer v-peers]
    (onyx.api/shutdown-peer v-peer))

  (onyx.api/shutdown-env env)

  )



