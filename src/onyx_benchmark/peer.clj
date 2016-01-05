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

(defn -main [zk-addr peer-config-file riemann-addr riemann-port id n-peers & args]
  (let [env-config {:onyx/id id
                    :onyx.bookkeeper/server? true
                    :onyx.bookkeeper/local-quorum? (= zk-addr "127.0.0.1:2189")
                    :zookeeper/address zk-addr
                    :zookeeper/server? false}

        peer-cfg (read-string (slurp peer-config-file))

        peer-config (merge
                     {:onyx.messaging/bind-addr
                      (slurp "http://169.254.169.254/latest/meta-data/local-ipv4")}
                     peer-cfg)

        n-peers-parsed (Integer/parseInt n-peers)
        peer-group (onyx.api/start-peer-group peer-config)
        env (onyx.api/start-env env-config)

        m-cfg (monitoring/monitoring-config 10000)
        riemann-config {:riemann/address riemann-addr 
                        :riemann/batch-size 1
                        :riemann/port (Integer/parseInt riemann-port)} 
        monitoring-thread (riemann/riemann-sender riemann-config (:monitoring/ch m-cfg))
        peers (onyx.api/start-peers n-peers-parsed peer-group m-cfg)]
    @(promise)))
