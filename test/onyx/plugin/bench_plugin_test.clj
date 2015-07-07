(ns onyx.plugin.bench-plugin-test
  (:require [clojure.core.async :refer [chan dropping-buffer put! >! <! <!! go >!!]]
            [taoensso.timbre :refer [info warn trace fatal level-compile-time] :as timbre]
            [onyx.peer.pipeline-extensions :as p-ext]
            [onyx.plugin.bench-plugin]
            [taoensso.timbre.appenders.rotor :as rotor]
            [onyx.static.logging-configuration :as log-config]
            [onyx.plugin.core-async]
            [interval-metrics.core :as im]
            [onyx.api])
  (:import [onyx.plugin RandomInputPlugin]
           [onyx.plugin JavaFn]))

(def id (java.util.UUID/randomUUID))

(def scheduler :onyx.job-scheduler/balanced)

(def messaging :aeron)

(def env-config
  {:zookeeper/address "127.0.0.1:2189"
   :zookeeper/server? true
   :zookeeper.server/port 2189
   :onyx/id id
   :onyx.log/config {:appenders {:standard-out {:enabled? false}
                                 :spit {:enabled? false}
                                 :rotor {:min-level :trace
                                         :enabled? true
                                         :async? false
                                         :max-message-per-msecs nil
                                         :fn rotor/appender-fn}}
                     :shared-appender-config {:rotor {:path "onyx.log"
                                                      :max-size (* 512 102400) :backlog 5}}}
   :onyx.peer/job-scheduler scheduler})

(def pending-timeout 60000)

(def peer-config
  {:zookeeper/address "127.0.0.1:2189"
   :onyx/id id
   :onyx.messaging/ack-daemon-timeout pending-timeout
   ;:onyx.messaging.aeron/offer-idle-strategy :low-restart-latency
   ;:onyx.messaging.aeron/poll-idle-strategy :low-restart-latency
   :onyx.messaging/bind-addr "127.0.0.1"
   :onyx.messaging/peer-ports (vec (range 40000 40200))
   :onyx.peer/job-scheduler scheduler
   :onyx.messaging/impl messaging})


(println "Starting env")
(def env (onyx.api/start-env env-config))

(def peer-group 
  (onyx.api/start-peer-group peer-config))

(def batch-size 20)
(def batch-timeout 100)

; (defn test-input [segment]
;   (println "Input called on " segment)
;   segment)

(defn my-inc [{:keys [n] :as segment}]
  ;(info "Received segment " segment)
  #_(when (zero? (rand-int 100000))
    (Thread/sleep 2000))
  (assoc segment :n (inc n)))

(def catalog
  [{:onyx/name :in
    :onyx/plugin :onyx.plugin.bench-plugin/generator
    ;:onyx/ident :onyx.plugin.RandomInputPlugin
    :onyx/type :input
    :onyx/medium :generator
    :onyx/max-pending 50000
    :onyx/pending-timeout pending-timeout
    :onyx/batch-timeout batch-timeout
    :onyx/batch-size batch-size}

   {:onyx/name :inc1
    :onyx/fn :onyx.plugin.bench-plugin-test/my-inc
    ;:onyx/fn :onyx.plugin.JavaFn/testFn
    :onyx/type :function
    :onyx/batch-timeout batch-timeout
    :onyx/batch-size batch-size}

   {:onyx/name :inc2
    :onyx/fn :onyx.plugin.bench-plugin-test/my-inc
    ;:onyx/fn :onyx.plugin.JavaFn/testFn
    :onyx/type :function
    :onyx/batch-timeout batch-timeout
    :onyx/batch-size batch-size}

   {:onyx/name :inc3
    :onyx/fn :onyx.plugin.bench-plugin-test/my-inc
    ;:onyx/fn :onyx.plugin.JavaFn/testFn
    :onyx/type :function
    :onyx/batch-timeout batch-timeout
    :onyx/batch-size batch-size}

   {:onyx/name :inc4
    :onyx/fn :onyx.plugin.bench-plugin-test/my-inc
    :onyx/type :function
    :onyx/batch-timeout batch-timeout
    :onyx/batch-size batch-size}

   {:onyx/name :no-op
    :onyx/plugin :onyx.plugin.core-async/output
    :onyx/type :output
    :onyx/medium :core.async
    :onyx/batch-timeout batch-timeout
    :onyx/batch-size batch-size
    :onyx/doc "Drops messages on the floor"}])


;; TO TEST

(def workflow [[:in :inc1] 
               [:inc1 :inc2] 
               [:inc2 :inc3]
               [:inc3 :inc4]
               [:inc4 :no-op]])


(println "starting Vpeers")
(def v-peers (onyx.api/start-peers 6 peer-group))

(println "Started vpeers")
(def bench-length 300000)

(def messages-tracking (atom {}))
(def rate+latency (im/rate+latency {:rate-unit :nanoseconds 
                                    :latency-unit :nanoseconds}))
(def retry-counter 
  (atom (long 0)))

(Thread/sleep 10000)

(def retry-calls
  {:lifecycle/before-task-start (fn inject-counter-before-task [event lifecycle]
                                  {:generator/rate-fut (future
                                                         (while (not (Thread/interrupted))
                                                           (Thread/sleep 10000)
                                                           (.println (System/out) (str (:onyx.core/id event) " RETRY COUNTER: " @retry-counter))))})
   :lifecycle/after-retry-message (fn retry-count-inc [event message-id rets lifecycle]
                                    (swap! retry-counter (fn [v] (inc ^long v))))})

(def latency-calls 
  {:lifecycle/after-ack-message (fn latency-after-ack [event message-id rets lifecycle]
                                  (when-let [v (@messages-tracking message-id)] 
                                    (im/update! rate+latency (- ^long (System/nanoTime) ^long v))
                                    (swap! messages-tracking dissoc message-id)))
   :lifecycle/before-task-start (fn [event lifecycle]
                                  {:latency-printer-fut 
                                   (future 
                                     (while (not (Thread/interrupted))
                                       (Thread/sleep 10000)
                                       (.println (System/out) (str (:onyx.core/id event) " LATENCY: " (im/snapshot! rate+latency)))))})
   :lifecycle/after-batch (fn after-batch [event lifecycle]
                            (doseq [m (:onyx.core/batch event)] 
                              (swap! messages-tracking assoc (:id m) (System/nanoTime)))
                            {})})

(def throughput-calls
  {:lifecycle/before-task-start (fn inject-counter-before-task [event lifecycle]
                                  (let [rate (im/rate)] 
                                    {:generator/rate rate
                                     :generator/rate-fut (future
                                                           (while (not (Thread/interrupted))
                                                             (Thread/sleep 1000)
                                                             (.println (System/out) (str (:onyx.core/id event) " RATE: " (im/snapshot! rate)))))}))
   :lifecycle/after-batch (fn after-batch [event lifecycle]
                            (im/update! (:generator/rate event) (count (:onyx.core/batch event)))
                            {})})

(def in-calls 
  {:lifecycle/before-task-start (fn inject-no-op-ch [event lifecycle]
                                  {:core.async/chan (chan (dropping-buffer 1))})})

(def lifecycles
  [{:lifecycle/task :no-op
    :lifecycle/calls :onyx.plugin.bench-plugin-test/in-calls}
   {:lifecycle/task :no-op
    :lifecycle/calls :onyx.plugin.core-async/writer-calls}
   {:lifecycle/task :in
    :lifecycle/calls :onyx.plugin.bench-plugin-test/retry-calls}
   {:lifecycle/task :in
    :lifecycle/calls :onyx.plugin.bench-plugin-test/throughput-calls}
   {:lifecycle/task :in
    :lifecycle/calls :onyx.plugin.bench-plugin-test/latency-calls}
   {:lifecycle/task :in
    :lifecycle/calls :onyx.plugin.bench-plugin/reader-calls}
   {:lifecycle/task :inc1
    :lifecycle/calls :onyx.plugin.bench-plugin-test/throughput-calls}
   {:lifecycle/task :inc2
    :lifecycle/calls :onyx.plugin.bench-plugin-test/throughput-calls}
   {:lifecycle/task :inc3
    :lifecycle/calls :onyx.plugin.bench-plugin-test/throughput-calls}
   {:lifecycle/task :inc4
    :lifecycle/calls :onyx.plugin.bench-plugin-test/throughput-calls}
   {:lifecycle/task :no-op
    :lifecycle/calls :onyx.plugin.bench-plugin-test/throughput-calls}])

(onyx.api/submit-job
  peer-config
  {:catalog catalog 
   :workflow workflow
   :lifecycles lifecycles
   ;:acker/percentage 50
   :task-scheduler :onyx.task-scheduler/balanced})

(Thread/sleep 5000)

(Thread/sleep bench-length)

(doseq [v-peer v-peers]
  (onyx.api/shutdown-peer v-peer))

(onyx.api/shutdown-peer-group peer-group)

(onyx.api/shutdown-env env)
