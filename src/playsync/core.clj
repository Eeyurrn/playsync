(ns playsync.core
  (:require [clojure.core.async
             :as a
             :refer [>! <! >!! <!! go chan buffer close! thread
                     alts! alts!! timeout]])
  (:gen-class))

;;Sends an accumulated message off to the distributors
(def to-distributors (chan (buffer 1)))
;;The limit at which we accumulate the messages
(def accumulate-limit 2)
;;tell the distributor threads to keep consuming
(def consume? (atom true))
(def active-distributors (atom 0))
(def log-agent (agent 0))

(def phonetic ["Alpha" "Bravo" "Charlie" "Delta" "Echo" "Foxtrot" "Golf" "Hotel" "India"])

(def accumulator (ref []))

(defn print-to-log [& s]
  (send-off log-agent
            (fn [_ st]
              (println st)) (clojure.string/join " " s)))

(defn transfer-from-accumulator! []
  (future
    (while consume?
      (dosync
        (when (<= accumulate-limit (count @accumulator))
          (alter (ref to-distributors)
                 (fn [ch]
                   (>!! ch (into [] (take accumulate-limit @accumulator)))))
          (alter accumulator
                 (fn [acc]
                   (drop accumulate-limit acc))))))))

(add-watch active-distributors :update-count
           (fn [_ _ old new]
             (send-off log-agent
                       (fn [_] (print-to-log "old:" old "new:" new)))))

(defn distributor [threadnum channel]
  (go
    (swap! active-distributors inc)
    (print-to-log "Thread number" threadnum)
    (loop [ch channel]
      (let [msg (<!! ch)]
        (if-not msg
          (swap! active-distributors dec)
          (do
            (print-to-log "Processing Thread" threadnum msg)
            (recur ch)))))))

(defn fire!
  "transfers random stuff to the accumulator"
  []
  (do
    (reset! consume? true)
    (future
      (while consume?
        (Thread/sleep 1000)
        (dosync
          (alter accumulator into [(get phonetic (rand-int (count phonetic)))]))))))

(defn stop-consume! []
  (reset! consume? false))

(defn spawn-distributors! [num-threads]
  (dotimes [n num-threads]
    (distributor n to-distributors)))


(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))
