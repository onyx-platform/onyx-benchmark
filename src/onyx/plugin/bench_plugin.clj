(ns onyx.plugin.bench-plugin
  (:require [clojure.core.async :refer [chan >!! <!! close! alts!! timeout]]
            [clojure.data.fressian :as fressian]
            [onyx.peer.pipeline-extensions :as p-ext]))

(def hundred-bytes 
  (into-array Byte/TYPE (range 100)))

(defn inject-reader
  [event lifecycle]
  (assert (:core.async/chan event) ":core.async/chan not found - add it via inject-lifecycle-resources.")
  {:generator/pending-messages (atom {})
   :generator/retry (atom [])})

(def reader-calls
  {:lifecycle/before-task inject-reader})

(defn flush-swap! [a f-read f-swap]
  (loop []
    (let [v (deref a)]
      (if (compare-and-set! a v (f-swap v))
        (f-read v)
        (recur)))))

(defmethod p-ext/read-batch :generator
  [{:keys [onyx.core/task-map
           generator/pending-messages
           generator/retry] :as event}]
  (let [batch-size (:onyx/batch-size task-map)
        segments (->> (flush-swap! retry 
                                   #(take batch-size %)
                                   #(subvec % (min batch-size (count %))))
                      (map (fn [m] {:id (java.util.UUID/randomUUID)
                                    :input :generator
                                    :message m})))
        batch (loop [n (count segments) 
                     sgs segments]
                (if (= n batch-size)
                  sgs
                  (recur (inc n)
                         (conj sgs {:id (java.util.UUID/randomUUID)
                                    :input :generator
                                    :message {:n n 
                                              :data hundred-bytes}}))))]
    (Thread/sleep 500)
    (doseq [m batch] 
      (swap! pending-messages assoc (:id m) (:message m)))
    {:onyx.core/batch batch}))

(defmethod p-ext/ack-message :generator
  [{:keys [generator/pending-messages]} message-id]
  (swap! pending-messages dissoc message-id))

(defmethod p-ext/retry-message :generator
  [{:keys [generator/pending-messages generator/retry]} message-id]
  (when-let [msg (get @pending-messages message-id)]
    (swap! retry conj msg)
    (swap! pending-messages dissoc message-id)))

(defmethod p-ext/pending? :generator
  [{:keys [generator/pending-messages]} message-id]
  (get @pending-messages message-id))

(defmethod p-ext/drained? :generator
  [event]
  ;; Infinite stream of messages, never drained.
  false)
