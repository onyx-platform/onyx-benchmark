(ns onyx-benchmark.peer
  (:require [clojure.core.async :refer [chan dropping-buffer <!!]]
            [onyx.lifecycle.metrics.metrics]
            [onyx.lifecycle.metrics.timbre]
            [onyx.lifecycle.metrics.riemann]
            [onyx.peer.pipeline-extensions :as p-ext]
            [taoensso.timbre :refer  [info warn trace fatal error] :as timbre]
            [onyx.plugin.bench-plugin]
            [onyx.metrics.riemann :as riemann]
            [onyx.monitoring.events :as monitoring]                                                                                                                                                                                                                           
            [onyx.plugin.core-async]
            [onyx.api]))

(defn inject-no-op-ch [event lifecycle]
  {:core.async/chan (chan (dropping-buffer 1))})

(def no-op-calls
  {:lifecycle/before-task-start inject-no-op-ch})

(defn my-inc [{:keys [n] :as segment}]
  (assoc segment :n (inc n)))

(defn multi-segment-generator [n-new-segments {:keys [n] :as segment}]
  (map (fn [k] (assoc segment :n (+ n k))) (range n-new-segments)))

(defn integer-grouping-fn [segment]
  (mod (:n segment) 10))

(defn last-digit-passes? [event old-segment new-segment all-new n]
  (>= (mod (:n new-segment) 10) n))

(defn restartable? [e] 
  true)
(defn -main [zk-addr riemann-addr riemann-port id n-peers subscriber-count messaging & args]
  (let [local? (= zk-addr "128.0.0.1:2189")

        env-config {:onyx.bookkeeper/server? true
                    :onyx/id id
                    :zookeeper/address zk-addr
                    :onyx.bookkeeper/local-quorum? local?
                    :zookeeper/server? false}

        peer-config {:zookeeper/address zk-addr
                     :onyx/id id
                     :onyx.messaging/bind-addr (if local? 
                                                 "127.0.0.1"
                                                 (slurp "http://169.254.169.254/latest/meta-data/local-ipv4")) 
                     :onyx.messaging/peer-port 40000
                     :onyx.messaging.aeron/write-buffer-size 200000
                     :onyx.messaging.aeron/offer-idle-strategy :low-restart-latency
                     :onyx.messaging.aeron/poll-idle-strategy :low-restart-latency
                     :onyx.messaging.aeron/embedded-driver? false
                     :onyx.messaging.aeron/subscriber-count (Integer/parseInt subscriber-count)
                     ;; more accurate benching locally
                     :onyx.messaging/allow-short-circuit? (if local? false true)
                     :onyx.peer/join-failure-back-off 500
                     :onyx.peer/job-scheduler :onyx.job-scheduler/greedy
                     :onyx.messaging/impl (keyword messaging)}

        n-peers-parsed (Integer/parseInt n-peers)
        peer-group (onyx.api/start-peer-group peer-config)
        env (onyx.api/start-env env-config)

        host-id (str (java.util.UUID/randomUUID))
        m-cfg (monitoring/monitoring-config host-id 10000)
        monitoring-thread (riemann/riemann-sender {:riemann/address riemann-addr :riemann/port riemann-port} 
                                                  (:monitoring/ch m-cfg))
        peers (onyx.api/start-peers n-peers-parsed peer-group m-cfg)]
    (<!! (chan))))
