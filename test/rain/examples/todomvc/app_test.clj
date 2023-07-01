(ns rain.examples.todomvc.app-test
  (:require [rain.examples.todomvc.app :as app]
            [clojure.test :refer [deftest is]]))

(deftest next-position-test
  (let [todos {1 {:position 1}}]
    (is (= 2 (app/next-position todos)))))
