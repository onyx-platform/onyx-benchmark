(ns onyx-benchmark.peer
  (:require [clojure.core.async :refer [chan dropping-buffer <!!]]
            [clojure.data.fressian :as fressian]
            [riemann.client :as r]
            [onyx.peer.pipeline-extensions :as p-ext]
            [onyx.plugin.bench-plugin]
            [onyx.plugin.core-async]
            [onyx.api]))

(defn inject-no-op-ch [event lifecycle]
  {:core.async/chan (chan (dropping-buffer 1))})

(defn close-batch-inc
  [event _]
  (swap! (:bench/state event) + (count (:onyx.core/batch event)))
  {})

(defn inject-inc
  [event _]
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

(def in-calls 
  {:lifecycle/before-task inject-no-op-ch})

(def inc-calls 
  {:lifecycle/after-batch close-batch-inc
   :lifecycle/before-task inject-inc})

(defn my-inc [{:keys [n] :as segment}]
  (assoc segment :n (inc n)))

(defn -main [zk-addr id n-peers & args]
  (let [peer-config {:zookeeper/address zk-addr
                     :onyx/id id
                     :onyx.messaging/bind-addr (slurp "http://169.254.169.254/latest/meta-data/local-ipv4")
                     :onyx.messaging/peer-ports (vec (range 40000 40200))
                     :onyx.peer/join-failure-back-off 500
                     :onyx.peer/job-scheduler :onyx.job-scheduler/greedy
                     :onyx.messaging/impl :netty}
        n-peers-parsed (Integer/parseInt n-peers)
        peer-group (onyx.api/start-peer-group peer-config)
        peers (onyx.api/start-peers n-peers-parsed peer-group)]
    (<!! (chan))))

