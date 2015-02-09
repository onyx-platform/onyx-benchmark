(ns onyx-benchmark.peer
  (:require [clojure.core.async :refer [chan dropping-buffer <!!]]
            [clojure.data.fressian :as fressian]
            [onyx.peer.task-lifecycle-extensions :as l-ext]
            [onyx.peer.pipeline-extensions :as p-ext]
            [onyx.plugin.bench-plugin]
            [onyx.plugin.core-async]
            [onyx.api]))

(defn -main [id zk-addr n-peers & args]
  (def peer-config
    {:zookeeper/address zk-addr
     :onyx/id id
     :onyx.peer/join-failure-back-off 500
     :onyx.peer/job-scheduler :onyx.job-scheduler/greedy
     :onyx.messaging/impl :http-kit})

  (def n-peers (Integer/parseInt n-peers))
  
  (defn my-inc [{:keys [n] :as segment}]
    (prn n)
    (assoc segment :n (inc n)))

  (def ports (atom 49999))

  (defmethod l-ext/inject-lifecycle-resources :in
    [_ _] {:http/port (swap! ports inc)})

  (defmethod l-ext/inject-lifecycle-resources :no-op
    [_ _] {:core.async/out-chan (chan (dropping-buffer 1))})

  (onyx.api/start-peers! (Integer/parseInt n-peers) peer-config)

  (<!! (chan)))

