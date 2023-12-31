(ns malli.dev
  (:require [malli.clj-kondo :as clj-kondo]
            [malli.core :as m]
            [malli.dev.pretty :as pretty]
            [malli.instrument :as mi]))

(defn -capture-fail! []
  (alter-var-root
   #'m/-fail!
   (let [report (pretty/reporter)]
     (fn [f] (-> (fn -fail!
                   ([type] (-fail! type nil))
                   ([type data] (let [e (m/-exception type data)]
                                  (report type data)
                                  (throw e))))
                 (with-meta {::original f}))))))

(defn -uncapture-fail! []
  (alter-var-root #'m/-fail! (fn [f] (-> f meta ::original (or f)))))

;;
;; Public API
;;

(defn stop!
  "Stops instrumentation for all functions vars and removes clj-kondo type annotations."
  []
  (remove-watch @#'m/-function-schemas* ::watch)
  (->> (mi/unstrument!)
       count
       (format "unstrumented %d vars")
       println)
  (clj-kondo/save! {})
  (-uncapture-fail!)
  (println "malli development mode disabled"))

(defn start!
  "Collects defn schemas from all loaded namespaces and starts instrumentation for
   a filtered set of function Vars (e.g. `defn`s). See [[malli.core/-instrument]]
   for possible options. Re-instruments if the function schemas change. Also emits
   clj-kondo type annotations."
  ([] (start! {:report (pretty/reporter)}))
  ([options]
   (with-out-str (stop!))
   (-capture-fail!)
   (mi/collect! {:ns (all-ns)})
   (let [watch (bound-fn [_ _ old new]
                 (->> (for [[n d] (:clj new)
                            :let [no (get-in old [:clj n])]
                            [s d] d
                            :when (not= d (get no s))]
                        [[n s] d])
                      (into {})
                      (reduce-kv assoc-in {})
                      (assoc options :data)
                      (mi/instrument!)
                      (count)
                      (format "instrumented %d vars")
                      (println))
                 (clj-kondo/emit!))]
     (add-watch @#'m/-function-schemas* ::watch watch))
   (mi/instrument! options)
   (clj-kondo/emit!)
   (println "malli development mode enabled")))
