(ns onyx-benchmark.peer
  (:require [clojure.core.async :refer [chan dropping-buffer <!!]]
            [onyx.lifecycle.metrics.metrics]
            [onyx.lifecycle.metrics.riemann]
            [onyx.lifecycle.metrics.timbre]
            [onyx.peer.pipeline-extensions :as p-ext]
            [taoensso.timbre :refer  [info warn trace fatal error] :as timbre]
            [onyx.plugin.bench-plugin]
            [onyx.plugin.core-async]
            [onyx.api]))

(defn inject-no-op-ch [event lifecycle]
  {:core.async/chan (chan (dropping-buffer 1))})

(def no-op-calls
  {:lifecycle/before-task-start inject-no-op-ch})

(defn my-inc [{:keys [n] :as segment}]
  (assoc segment :n (inc n)))

(defn -main [zk-addr id n-peers messaging & args]
  (let [local? (= zk-addr "127.0.0.1:2189")
        peer-config {:zookeeper/address zk-addr
                     :onyx/id id
                     :onyx.messaging/bind-addr (if local? 
                                                 "127.0.0.1"
                                                 (slurp "http://169.254.169.254/latest/meta-data/local-ipv4")) 
                     :onyx.messaging/peer-port 40000
                     :onyx.messaging.aeron/write-buffer-size 200000
                     :onyx.messaging.aeron/offer-idle-strategy :high-restart-latency
                     :onyx.messaging.aeron/poll-idle-strategy :high-restart-latency
                     :onyx.messaging.aeron/embedded-driver? false
                     :onyx.messaging.aeron/subscriber-count 4
                     ;; more accurate benching locally
                     :onyx.messaging/allow-short-circuit? (if local? false true)
                     :onyx.peer/join-failure-back-off 500
                     :onyx.peer/job-scheduler :onyx.job-scheduler/greedy
                     :onyx.messaging/impl (keyword messaging)}
        n-peers-parsed (Integer/parseInt n-peers)
        peer-group (onyx.api/start-peer-group peer-config)
        peers (onyx.api/start-peers n-peers-parsed peer-group)]
    (<!! (chan))))
