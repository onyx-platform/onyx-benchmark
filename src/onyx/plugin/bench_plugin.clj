(ns onyx.plugin.bench-plugin
  (:require [clojure.core.async :refer [chan >!! <!! close! alts!! timeout]]
            [onyx.peer.function :as function]
            [onyx.static.default-vals :refer [defaults]]
            [onyx.types :as t]
            [taoensso.timbre :refer [info warn trace fatal] :as timbre]
            [onyx.peer.pipeline-extensions :as p-ext]))

(def hundred-bytes 
  (into-array Byte/TYPE (range 100)))

(defn flush-swap! [a f-read f-swap]
  (loop []
    (let [v (deref a)]
      (if (compare-and-set! a v (f-swap v))
        (f-read v)
        (recur)))))

(defn inject-reader
  [event lifecycle]
  (let [pipeline (:onyx.core/pipeline event)] 
    {:generator/pending-messages (:pending-messages pipeline)}))

(def reader-calls
  {:lifecycle/before-task-start inject-reader})

(defn new-segment-small [batch-index]
  {:n batch-index :data hundred-bytes})

;; Backported so we can test old versions of onyx
(defn random-uuid []
  (let [local-random (java.util.concurrent.ThreadLocalRandom/current)]
    (java.util.UUID. (.nextLong local-random)
                     (.nextLong local-random))))

(defn new-grouping-segment [batch-index]
  {:id (random-uuid)
   :event-time (java.util.Date.)
   :group-key (rand-int 10000)
   :value (rand-int 500)})

(defrecord BenchmarkInput [pending-messages retry max-pending batch-size new-segment-fn]
  p-ext/Pipeline
  (write-batch [this event]
    (function/write-batch event))

  (read-batch [_ event]
    (let [_ (while (< (- max-pending 
                         (count @pending-messages)) 
                      batch-size)
              (Thread/sleep 100))
          max-segments batch-size
          segments (->> (flush-swap! retry 
                                     #(take max-segments %)
                                     #(subvec % (min max-segments (count %))))
                        (map (fn [m] (t/input (random-uuid) m))))
          batch (loop [n (count segments) 
                       sgs segments]
                  (if (= n max-segments)
                    sgs
                    (recur (inc n)
                           (conj sgs (t/input (random-uuid) (new-segment-fn n))))))]
      (doseq [m batch] 
        (swap! pending-messages assoc (:id m) (:message m)))
      {:onyx.core/batch batch}))

  (seal-resource [this event])

  p-ext/PipelineInput
  (ack-segment [_ _ segment-id]
    (swap! pending-messages dissoc segment-id))

  (retry-segment 
    [_ _ segment-id]
    (when-let [msg (get @pending-messages segment-id)]
      (swap! retry conj msg)
      (swap! pending-messages dissoc segment-id)))

  (pending?
    [_ _ segment-id]
    (get @pending-messages segment-id))

  (drained?
    [_ _]
    false))

(defn generator [pipeline-data]
  (let [task-map (:onyx.core/task-map pipeline-data)
        max-pending (or (:onyx/max-pending task-map) (:onyx/max-pending defaults))
        batch-size (:onyx/batch-size task-map)
        segment-generator-fn (case (:benchmark/segment-generator task-map)
                               :hundred-bytes new-segment-small
                               :grouping-fn new-grouping-segment)]
    (->BenchmarkInput (atom {}) (atom []) max-pending batch-size segment-generator-fn))) 
