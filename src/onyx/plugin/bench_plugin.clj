(ns onyx.plugin.bench-plugin
  (:require [clojure.core.async :refer [chan >!! <!! close! alts!! timeout]]
            [clojure.data.fressian :as fressian]
            [onyx.peer.task-lifecycle-extensions :as l-ext]
            [onyx.peer.pipeline-extensions :as p-ext]
            [org.httpkit.server :as server]))

(defmethod l-ext/inject-lifecycle-resources :generator/generator
  [_ event]
  {})

(defmethod p-ext/read-batch [:input :generator]
  [{:keys [onyx.core/task-map] :as event}]
  (let [batch-size (:onyx/batch-size task-map)]
    (Thread/sleep 100)
    {:onyx.core/batch (map (fn [i] {:id (java.util.UUID/randomUUID)
                                    :input :generator
                                    :message {:n i}})
                           (range batch-size))}))

(defmethod p-ext/decompress-batch [:input :generator]
  [{:keys [onyx.core/batch]}]
  {:onyx.core/decompressed batch})

(defmethod p-ext/apply-fn [:input :generator]
  [event segment]
  segment)

(defmethod p-ext/ack-message [:input :generator]
  [{:keys [core.async/pending-messages]} message-id]
  ;; We want to go as fast as possible, so we're going
  ;; to ignore message acknowledgment for now.
  )

(defmethod p-ext/replay-message [:input :generator]
  [{:keys [core.async/pending-messages core.async/replay-ch]} message-id]
  ;; Same as above.
  )

(defmethod p-ext/pending? [:input :generator]
  [{:keys [core.async/pending-messages]} message-id]
  ;; Same as above.
  false)

(defmethod p-ext/drained? [:input :generator]
  [event]
  ;; Infinite stream of messages, never drained.
  false)

(defmethod l-ext/close-batch-resources :generator
  [_ event] 
  event)
