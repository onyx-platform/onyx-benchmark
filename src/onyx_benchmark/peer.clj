(ns onyx-benchmark.peer
  (:require [clojure.core.async :refer [chan dropping-buffer <!!]]
            [clojure.data.fressian :as fressian]
            [riemann.client :as r]
            [onyx.peer.pipeline-extensions :as p-ext]
            [taoensso.timbre.appenders.rotor :as rotor]
            [taoensso.timbre :refer  [info warn trace fatal error] :as timbre]
            [onyx.plugin.bench-plugin]
            [onyx.plugin.core-async]
            [yeller-timbre-appender]
            [onyx.api]))

(defn inject-no-op-ch [event lifecycle]
  {:core.async/chan (chan (dropping-buffer 1))})

(def counter (atom 0))
(def retry-counter (atom 0))

(defn close-batch-inc
  [event _]
  (swap! (:bench/state event) + (count (:onyx.core/batch event)))
  {})

(defn inject-state
  [event _]
  {:retry-counter retry-counter
   :bench/state counter})

(def no-op-calls 
  {:lifecycle/before-task inject-no-op-ch})

(def measurement-calls 
  {:lifecycle/before-task inject-state
   :lifecycle/after-batch close-batch-inc})

(defn start-sending!
  [riemann-addr]
  (let [client (r/tcp-client {:host riemann-addr})]
    (future
      (try
        (loop []
          (Thread/sleep 1000)
          (let [cnt @counter
                _ (reset! counter 0)
                retry-cnt @retry-counter
                _ (reset! retry-counter)]
            (info "-> " cnt " <-")
            (r/send-event client {:service "onyx-retry" :state "ok" :metric retry-cnt :tags ["benchmark"]})
            (r/send-event client {:service "onyx" :state "ok" :metric cnt :tags ["benchmark"]}))
          (recur))
        (catch Exception e
          (error e))))))

(defn my-inc [{:keys [n] :as segment}]
  (assoc segment :n (inc n)))

(def yeller-token (System/getenv "YELLER_TOKEN"))

(def logging-config 
  (cond-> {:appenders {:standard-out {:enabled? false}
                       :spit {:enabled? false}
                       :rotor {:min-level :trace
                               :enabled? true
                               :async? false
                               :max-message-per-msecs nil
                               :fn rotor/appender-fn}}
           :shared-appender-config {:rotor {:path "onyx-benchmark.log"
                                            :max-size (* 512 102400) :backlog 5}}}
    (not-empty yeller-token) 
    (assoc :yeller (yeller-timbre-appender/make-yeller-appender
                     {:token yeller-token
                      :environment "production"}))))

(defn -main [zk-addr riemann-addr id n-peers & args]
  (let [peer-config {:zookeeper/address zk-addr
                     :onyx/id id
                     :onyx.messaging/bind-addr (slurp "http://169.254.169.254/latest/meta-data/local-ipv4")
                     :onyx.messaging/peer-ports (vec (range 40000 40200))
                     :onyx.peer/join-failure-back-off 500
                     :onyx.peer/job-scheduler :onyx.job-scheduler/greedy
                     :onyx.peer/inbox-capacity 100
                     :onyx.messaging/impl :netty
                     :onyx.log/config logging-config}
        n-peers-parsed (Integer/parseInt n-peers)
        peer-group (onyx.api/start-peer-group peer-config)
        peers (onyx.api/start-peers n-peers-parsed peer-group)]
    (start-sending! riemann-addr)
    (<!! (chan))))
