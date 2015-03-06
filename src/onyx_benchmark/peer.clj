(ns onyx-benchmark.peer
  (:require [clojure.core.async :refer [chan dropping-buffer <!!]]
            [clojure.data.fressian :as fressian]
            [riemann.client :as r]
            [onyx.peer.task-lifecycle-extensions :as l-ext]
            [onyx.peer.pipeline-extensions :as p-ext]
            [onyx.plugin.bench-plugin]
            [onyx.plugin.core-async]
            [onyx.api]))

(defn -main [zk-addr id n-peers & args]
  
  (def peer-config
    {:zookeeper/address zk-addr
     :onyx/id id
     :onyx.peer/join-failure-back-off 500
     :onyx.peer/job-scheduler :onyx.job-scheduler/greedy
     :onyx.messaging/impl :http-kit})

  (def n-peers (Integer/parseInt n-peers))

  (defn my-inc [{:keys [n] :as segment}]
    (assoc segment :n (inc n)))

  (defmethod l-ext/inject-lifecycle-resources :no-op
    [_ _] {:core.async/out-chan (chan (dropping-buffer 1))})

  (defmethod l-ext/inject-lifecycle-resources :inc
    [_ event]
    (let [state (atom 0)
          client (r/tcp-client {:host (:bench/riemann (:onyx.core/task-map event))})]
      (future
        (try
          (loop []
            (Thread/sleep 1000)
            (prn "-> " @state " <-")
            (r/send-event client {:service "onyx" :state "ok" :metric @state :tags ["benchmark"]})
            (reset! state 0)
            (recur))
          (catch Exception e
            (.printStackTrace e))))
      {:bench/state state
       :bench/riemann client}))

  (defmethod l-ext/close-batch-resources :inc
    [_ event]
    (swap! (:bench/state event) + (count (:onyx.core/decompressed event)))
    {})

  (onyx.api/start-peers! (Integer/parseInt n-peers) peer-config)

  (<!! (chan)))

