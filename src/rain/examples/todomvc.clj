(ns rain.examples.todomvc
  (:require [babashka.fs :as fs]
            [com.biffweb :as biff]
            [rain.core :as rain]
            [rain.biff :as rain-biff]
            [rain.examples.todomvc.app :as app]
            [clojure.tools.logging :as log]
            [clojure.tools.namespace.repl :as tn-repl]
            [nrepl.cmdline :as nrepl-cmd]
            [rain.examples.todomvc.ui :as ui]
            [reitit.ring :as ring]
            [reitit.swagger-ui :as swagger-ui]
            [todo-backend.core :as todo-backend]))

(defn generate-assets! [{:keys [biff/plugins] :as ctx}]
  (let [routes (keep :routes @plugins)]
    (let [src "node_modules/todomvc-common"
          dest "target/resources/public/node_modules/todomvc-common"]
      (when (seq (fs/modified-since dest src))
        (fs/copy-tree src dest {:replace-existing true})))
    (let [src "node_modules/todomvc-app-css"
          dest "target/resources/public/node_modules/todomvc-app-css"]
      (when (seq (fs/modified-since dest src))
        (fs/copy-tree src dest {:replace-existing true})))
    (rain/export-pages (rain/static-pages routes ctx)
                       "target/resources/public")
    (biff/delete-old-files {:dir "target/resources/public"
                            :exts [".html"]})))

(def plugins
  [app/plugin])

(defn server-routes [plugins]
  [["" {:middleware [biff/wrap-site-defaults]}
    (rain/site-routes (keep :routes plugins))]

   ["" {:middleware [biff/wrap-api-defaults]}
    (keep :api-routes plugins)]])

(def handler
  (ring/routes
    (ring/ring-handler todo-backend/router)
    (swagger-ui/create-swagger-ui-handler {:path "/swagger-ui"})
    (-> (biff/reitit-handler {:routes (server-routes plugins)})
        biff/wrap-base-defaults)))

(defn on-save [ctx]
  (biff/add-libs)
  (biff/eval-files! ctx)
  (generate-assets! ctx))

(def initial-system
  {:biff/plugins #'plugins
   :biff/handler #'handler
   :biff.beholder/on-save #'on-save
   :rain/layout ui/layout})

(defn partial-build [& _]
  (generate-assets! initial-system))

(defonce system (atom {}))

(def components
  [biff/use-config
   biff/use-secrets
   rain-biff/use-shadow-cljs
   rain-biff/use-jetty
   biff/use-beholder])

(defn start []
  (let [new-system (reduce (fn [system component]
                             (log/info "starting:" (str component))
                             (component system))
                           initial-system
                           components)]
    (reset! system new-system)
    (generate-assets! new-system)))

(defn -main [& args]
  (start)
  (apply nrepl-cmd/-main args))

(defn refresh []
  (doseq [f (:biff/stop @system)]
    (log/info "stopping:" (str f))
    (f))
  (tn-repl/refresh :after `start))

(comment
  ;; Evaluate this if you make a change to initial-system, components, :tasks,
  ;; :queues, or config.edn. If you update secrets.env, you'll need to restart
  ;; the app.
  (refresh)

  ;; If that messes up your editor's REPL integration, you may need to use this
  ;; instead:
  (biff/fix-print (refresh)))
