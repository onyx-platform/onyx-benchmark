(ns onyx.plugin.bench-plugin
  (:require [onyx.plugin.protocols.input :as i]
            [onyx.plugin.protocols.output :as o]
            [onyx.plugin.protocols.plugin :as p]
            [onyx.static.default-vals :refer [arg-or-default]]
            [onyx.types :as t]
            [taoensso.timbre :refer [info warn trace fatal] :as timbre]))

(def hundred-bytes 
  (into-array Byte/TYPE (range 100)))

(defn inject-reader
  [event lifecycle]
  {})

(def reader-calls
  {:lifecycle/before-task-start inject-reader})

(defn new-segment-small []
  {:start-time (System/nanoTime)
   :n (rand-int 10000) :data hundred-bytes})

;; Backported so we can test old versions of onyx
(defn random-uuid []
  (let [local-random (java.util.concurrent.ThreadLocalRandom/current)]
    (java.util.UUID. (.nextLong local-random)
                     (.nextLong local-random))))

(defn new-grouping-segment []
  {:id (random-uuid)
   :event-time (java.util.Date.)
   :group-key (rand-int 10000)
   :value (rand-int 500)})

(defrecord BenchmarkInput [segment-generator-fn]
  p/Plugin

  (start [this event]
    this)

  (stop [this event] 
    this)

  i/Input

  (checkpoint [this]
    nil)

  (checkpointed! [this cp-epoch])

  (recover! [this _ checkpoint]
    this)

  (poll! [this _]
    ; (when (zero? (rand-int 10000))
    ;   (Thread/sleep 500))
    (segment-generator-fn))

  (synced? [this epoch]
    true)

  (completed? [this]
    false))

(defn input [event]
  (map->BenchmarkInput {:event event}))

(defn generator [{:keys [onyx.core/task-map] :as event}]
  (let [segment-generator-fn (case (:benchmark/segment-generator task-map)
                               :hundred-bytes new-segment-small
                               :grouping-fn new-grouping-segment)]
    (->BenchmarkInput segment-generator-fn))) 
