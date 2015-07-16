(ns onyx-benchmark.peer
  (:require [clojure.core.async :refer [chan dropping-buffer <!!]]
            [riemann.client :as r]
            [onyx.peer.pipeline-extensions :as p-ext]
            [taoensso.timbre :refer  [info warn trace fatal error] :as timbre]
            [interval-metrics.core :as im]
            [onyx.plugin.bench-plugin]
            [onyx.plugin.core-async]
            [onyx.api]))

(defn inject-no-op-ch [event lifecycle]
  {:core.async/chan (chan (dropping-buffer 1))})

(def messages-tracking (atom {}))
(def rate+latency (im/rate+latency {:rate-unit :nanoseconds 
                                    :latency-unit :nanoseconds}))
(def retry-counter 
  (atom (long 0)))

(def rate (im/rate))

(def retry-calls
  {:lifecycle/after-retry-message (fn retry-count-inc [event message-id rets lifecycle]
                                    (swap! retry-counter (fn [v] (inc ^long v))))})

(def latency-calls 
  {:lifecycle/after-ack-message (fn latency-after-ack [event message-id rets lifecycle]
                                  (when-let [v (@messages-tracking message-id)] 
                                    (im/update! rate+latency (- ^long (System/nanoTime) ^long v))
                                    (swap! messages-tracking dissoc message-id)))

   :lifecycle/after-batch (fn after-batch [event lifecycle]
                            (doseq [m (:onyx.core/batch event)] 
                              (swap! messages-tracking assoc (:id m) (System/nanoTime)))
                            {})})

(def throughput-calls
  {:lifecycle/after-batch (fn after-batch [event lifecycle]
                            (im/update! rate (count (:onyx.core/batch event)))
                            {})})

(def no-op-calls 
  {:lifecycle/before-task-start inject-no-op-ch})

(defn start-sending!
  [riemann-addr]
  (let [client (r/tcp-client {:host riemann-addr})]
    (future
      (try
        (loop [offset 0]
          (let [sleep-time (if (neg? (- 1000 offset))
                             (do
                               ;; loop took too long and measurements are inaccurate
                               ;; throw away results
                               (im/snapshot! rate)   
                               (im/snapshot! rate+latency)      
                               (reset! retry-counter 0)
                               1000)
                             (- 1000 offset))
                _ (Thread/sleep sleep-time)
                throughput (im/snapshot! rate)
                latency (im/snapshot! rate+latency)
                retry-cnt @retry-counter
                _ (reset! retry-counter 0)
                start (System/currentTimeMillis)
                latencies (:latencies latency)
                latency-00 (float (/ (or (get latencies 0.0) 0) 1000000))
                latency-05 (float (/ (or (get latencies 0.5) 0) 1000000))
                latency-095 (float (/ (or (get latencies 0.95) 0) 1000000))
                latency-099 (float (/ (or (get latencies 0.99) 0) 1000000))
                latency-0999 (float (/ (or (get latencies 0.999) 0) 1000000))]
            (info "-> " throughput ", retries: " retry-cnt ", latency " latency " <-")
            (info "-> " latency-00 latency-05 latency-095 latency-099 latency-0999)
            (r/send-event client {:service "onyx-retry" :state "ok" :metric retry-cnt :tags ["benchmark"]})
            (r/send-event client {:service "onyx-throughput" :state "ok" :metric throughput :tags ["benchmark"]})
            (r/send-event client {:service "onyx-ack-latency-0.5" :state "ok" :metric latency-00 :tags ["benchmark"]})
            (r/send-event client {:service "onyx-ack-latency-0.5" :state "ok" :metric latency-05 :tags ["benchmark"]})
            (r/send-event client {:service "onyx-ack-latency-0.95" :state "ok" :metric latency-095 :tags ["benchmark"]})
            (r/send-event client {:service "onyx-ack-latency-0.99" :state "ok" :metric latency-099 :tags ["benchmark"]})
            (r/send-event client {:service "onyx-ack-latency-0.999" :state "ok" :metric latency-0999 :tags ["benchmark"]})
            (recur (- (System/currentTimeMillis) start))))
        (catch Exception e
          (error e))))))

(defn my-inc [{:keys [n] :as segment}]
  (assoc segment :n (inc n)))

(defn -main [zk-addr riemann-addr id n-peers messaging & args]
  (let [peer-config {:zookeeper/address zk-addr
                     :onyx/id id
                     :onyx.messaging/bind-addr (slurp "http://169.254.169.254/latest/meta-data/local-ipv4")
                     :onyx.messaging/peer-ports (vec (range 40000 40200))
                     :onyx.messaging.aeron/offer-idle-strategy :high-restart-latency
                     :onyx.messaging.aeron/poll-idle-strategy :high-restart-latency
                     :onyx.messaging.aeron/embedded-driver? false
                     :onyx.messaging.aeron/subscriber-count 2
                     :onyx.peer/join-failure-back-off 500
                     :onyx.peer/job-scheduler :onyx.job-scheduler/greedy
                     :onyx.messaging/impl (keyword messaging)}
        n-peers-parsed (Integer/parseInt n-peers)
        peer-group (onyx.api/start-peer-group peer-config)
        peers (onyx.api/start-peers n-peers-parsed peer-group)]
    (start-sending! riemann-addr)
    (<!! (chan))))
