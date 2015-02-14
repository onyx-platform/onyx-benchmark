(ns onyx.plugin.bench-plugin
  (:require [clojure.core.async :refer [chan >!! <!! close! alts!! timeout]]
            [clojure.data.fressian :as fressian]
            [onyx.peer.task-lifecycle-extensions :as l-ext]
            [onyx.peer.pipeline-extensions :as p-ext]
            [org.httpkit.server :as server]))

(defn async-handler-http [ch ring-request]
  (server/with-channel ring-request channel
    (do (>!! ch (fressian/read (.bytes (:body ring-request))))
        (server/send! channel {:status 200
                               :headers {"Content-Type" "text/plain"}
                               :thread 4
                               :queue-size 100000
                               :body ""}))))

(defmethod l-ext/inject-lifecycle-resources :http/listen
  [_ event]
  (let [ch (chan 100000)
        s (server/run-server (partial async-handler-http ch) {:port (:http/port event)})]
    (prn (meta s))
    {:http/read-ch ch
     :http/server s
     :http/port (:local-port (meta s))}))

(defmethod p-ext/read-batch [:input :http]
  [{:keys [onyx.core/task-map http/read-ch] :as event}]
  (let [batch-size (:onyx/batch-size task-map)
        ms (or (:onyx/batch-timeout task-map) 50)
        batch (->> (range batch-size)
                   (map (fn [_] {:id (java.util.UUID/randomUUID)
                                :input :http
                                :message (first (alts!! [read-ch (timeout ms)] :priority true))}))
                   (filter (comp not nil? :message)))]
    {:onyx.core/batch batch}))

(defmethod p-ext/decompress-batch [:input :http]
  [{:keys [onyx.core/batch]}]
  {:onyx.core/decompressed batch})

(defmethod p-ext/apply-fn [:input :http]
  [event segment]
  segment)

(defmethod p-ext/ack-message [:input :http]
  [{:keys [core.async/pending-messages]} message-id]
  ;; We want to go as fast as possible, so we're going
  ;; to ignore message acknowledgment for now.
  )

(defmethod p-ext/replay-message [:input :http]
  [{:keys [core.async/pending-messages core.async/replay-ch]} message-id]
  ;; Same as above.
  )

(defmethod p-ext/pending? [:input :http]
  [{:keys [core.async/pending-messages]} message-id]
  ;; Same as above.
  false)

(defmethod p-ext/drained? [:input :http]
  [event]
  ;; Infinite stream of messages, never drained.
  false)

(defmethod l-ext/close-temporal-resources :http/listen
  [_ event] event)

(defmethod l-ext/close-lifecycle-resources :http/listen
  [_ event]
  ((:http/server event))
  event)

