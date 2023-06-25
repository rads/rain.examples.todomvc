(ns rain.examples.todomvc.ui
  (:require [rain.core :as rain]))

(defn layout [ctx content]
  (list
    [:hiccup/raw-html "<!DOCTYPE html>"]
    [:html {:lang "en"}
     [:head
      [:meta {:charset "utf-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
      [rain/meta-tags ctx]
      [:title "Rain • TodoMVC"]
      [:link {:rel "stylesheet" :href "node_modules/todomvc-common/base.css"}]
      [:link {:rel "stylesheet" :href "node_modules/todomvc-app-css/index.css"}]]
     [:body
      [:div#app content]
      [:script {:src "node_modules/todomvc-common/base.js"}]
      [rain/script-tags ctx]]]))
