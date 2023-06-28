(ns rain.examples.todomvc.app
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [reitit.core :as r]
            [rain.re-frame :as rrf]
            #?@(:clj [[todo-backend.handlers :as todo-backend]])))

(declare href)

(defn next-position [todos]
  (inc (apply max (or (seq (map :position (vals todos))) [0]))))

(defn parse-json [s]
  #?(:cljs (-> s js/JSON.parse (js->clj :keywordize-keys true))))

(rrf/reg-event-fx
  ::create-todo
  (fn [{:keys [db]} [_ {:keys [title] :as _params}]]
    (let [{:keys [todos]} db
          todo (merge {:temp-id (random-uuid)
                       :title (str/trim title)
                       :completed false
                       :position (next-position todos)})]
      {:db (assoc-in db [:todos (:temp-id todo)] todo)
       :fx [[:fetch {:method "POST"
                     :request-content-type :json
                     :url "/todos"
                     :body (dissoc todo :temp-id)
                     :on-success [::create-todo-response todo]
                     :on-failure [::create-todo-response todo]}]]})))

(rrf/reg-event-db
  ::create-todo-response
  (fn [db [_ {:keys [temp-id] :as _todo} {:keys [body] :as _response}]]
    (let [{:keys [id]} (parse-json body)]
      (-> db
          (update :todos dissoc temp-id)
          (assoc-in [:todos id] (-> (get-in db [:todos temp-id])
                                    (dissoc :temp-id)
                                    (assoc :id id)))))))

(rrf/reg-event-fx
  ::toggle-todo-completed
  (fn [{:keys [db]} [_ {:keys [id]}]]
    (let [todo (-> (get-in db [:todos id])
                   (update :completed not))]
      {:db (update-in db [:todos id :completed] not)
       :fx [[:fetch {:method "PATCH"
                     :request-content-type :json
                     :url (str "/todos/" (:id todo))
                     :body (select-keys todo [:completed])
                     :on-success [::toggle-todo-completed-response]
                     :on-failure [::toggle-todo-completed-response]}]]})))

(defn delete-todo-request [{:keys [id]}]
  [:fetch {:method "DELETE"
           :url (str "/todos/" id)
           :on-success [::destroy-todo-response]
           :on-failure [::destroy-todo-response]}])

(rrf/reg-event-fx
  ::destroy-todo
  (fn [{:keys [db]} [_ {:keys [id]}]]
    {:db (update db :todos dissoc id)
     :fx [(delete-todo-request {:id id})]}))

(defn apply-filter [todos filter-key]
  (->> todos
       (filter (fn [[_ {:keys [completed]}]]
                 (case filter-key
                   nil true
                   :active (not completed)
                   :completed completed)))
       (into {})))

(rrf/reg-event-fx
  ::clear-completed
  (fn [{:keys [db]} _]
    (let [{:keys [todos]} db
          completed (apply-filter todos :completed)]
      {:db (update db :todos #(apply dissoc % (keys completed)))
       :fx (map (fn [[id _]] (delete-todo-request {:id id})) completed)})))

(rrf/reg-sub
  ::current-filter
  (fn [{:keys [current-filter]} _]
    current-filter))

(rrf/reg-sub
  ::all-todos
  (fn [{:keys [todos current-filter]} _]
    (->> (apply-filter todos current-filter)
         vals
         (sort-by :position))))

(rrf/reg-sub
  ::remaining-count
  (fn [{:keys [todos]} _]
    (count (apply-filter todos :active))))

(rrf/reg-event-fx
  ::start-editing-todo
  (fn [{:keys [db]} [_ {:keys [id]}]]
    {:db (assoc-in db [:todos id :edit] true)}))

(rrf/reg-event-fx
  ::stop-editing-todo
  (fn [_ [_ params]]
    {:fx [[:dispatch [::update-todo params]]]}))

(rrf/reg-event-fx
  ::update-todo
  (fn [{:keys [db]} [_ {:keys [id] :as params}]]
    {:db (-> db
             (update-in [:todos id] dissoc :edit)
             (update-in [:todos id] merge params))
     :fx [[:fetch {:method "PATCH"
                   :request-content-type :json
                   :url (str "/todos/" id)
                   :body (dissoc params :id)
                   :on-success [::update-todo-response params]
                   :on-failure [::update-todo-response params]}]]}))

(rrf/reg-event-fx
  ::toggle-all-todos-completed
  (fn [{:keys [db]} _]
    (let [{:keys [todos]} db
          completed (apply-filter todos :completed)]
      (if (= (keys completed) (keys todos))
        {:db (update db :todos update-vals #(assoc % :completed false))
         :fx (map (fn [[_ todo]]
                    [:fetch {:method "PATCH"
                             :request-content-type :json
                             :url (str "/todos/" (:id todo))
                             :body {:completed false}
                             :on-success [::toggle-all-todos-completed-response]
                             :on-failure [::toggle-all-todos-completed-response]}])
                  todos)}
        {:db (update db :todos update-vals #(assoc % :completed true))
         :fx (map (fn [[_ todo]]
                    [:fetch {:method "PATCH"
                             :request-content-type :json
                             :url (str "/todos/" (:id todo))
                             :body {:completed true}
                             :on-success [::toggle-all-todos-completed-response]
                             :on-failure [::toggle-all-todos-completed-response]}])
                  (select-keys todos (set/difference (set (keys todos))
                                                     (set (keys completed)))))}))))

(rrf/reg-sub
  ::all-completed
  (fn [{:keys [todos]} _]
    (every? :completed (vals todos))))

(defn edit-input [{:keys [title]}]
  (let [v (rrf/atom title)]
    (fn [{:keys [id]}]
      [:input.edit {:value @v
                    :autoFocus true
                    :on-change (fn [e]
                                 (.preventDefault e)
                                 (reset! v (.-value (.-target e))))
                    :on-blur (fn [_]
                               (rrf/dispatch [::stop-editing-todo
                                              {:id id
                                               :title @v}]))}])))

(defn todo-page [_]
  (let [remaining-count (rrf/subscribe [::remaining-count])
        current-filter (rrf/subscribe [::current-filter])
        all-todos (rrf/subscribe [::all-todos])
        all-completed (rrf/subscribe [::all-completed])
        fields (rrf/atom {})]
    (fn [_]
      [:<>
       [:section.todoapp
        [:header.header
         [:h1 "todos"]
         [:form
          {:on-submit (fn [e]
                        (.preventDefault e)
                        (when-not (str/blank? (:title @fields))
                          (rrf/dispatch [::create-todo @fields])
                          (swap! fields dissoc :title)))}
          [:input.new-todo
           {:placeholder "What needs to be done?"
            :autoFocus true
            :value (:title @fields)
            :on-change (fn [e]
                         (.preventDefault e)
                         (swap! fields assoc :title (.-value (.-target e))))}]]]
        [:section.main
         [:input#toggle-all.toggle-all
          {:type "checkbox"
           :on-change (rrf/dispatcher [::toggle-all-todos-completed])
           :checked (boolean (and (seq @all-todos) @all-completed))}]
         [:label {:for "toggle-all"} "Mark all as complete"]
         [:ul.todo-list
          (for [{:keys [id temp-id completed edit title] :as todo} @all-todos]
            [:li {:class (when (or completed edit)
                           (str/join " " [(when completed "completed")
                                          (when edit "editing")]))
                  :on-click (fn [e]
                              (.preventDefault e)
                              (when (= 2 (.-detail e))
                                (rrf/dispatch [::start-editing-todo {:id id}])))
                  #?@(:cljs [:key (or id temp-id)])}
             (if edit
               [edit-input todo]
               [:div.view
                [:input.toggle
                 {:type "checkbox"
                  :on-change (rrf/dispatcher [::toggle-todo-completed {:id id}])
                  :checked completed}]
                [:label title]
                [:button.destroy
                 {:on-click (rrf/dispatcher [::destroy-todo {:id id}])}]])])]]
        [:footer.footer
         [:span.todo-count
          [:strong @remaining-count]
          (str " "
               (if (= 1 @remaining-count) "item" "items")
               " left")]
         [:ul.filters
          [:li
           [:a {:href (href ::index)
                :class (when-not @current-filter "selected")}
            "All"]]
          [:li
           [:a {:href (href ::active)
                :class (when (= @current-filter :active) "selected")}
            "Active"]]
          [:li
           [:a {:href (href ::completed)
                :class (when (= @current-filter :completed) "selected")}
            "Completed"]]]
         [:button.clear-completed
          {:on-click (rrf/dispatcher [::clear-completed])}
          "Clear completed"]]]
       [:footer.info
        [:p "Double-click to edit a todo"]
        [:p "Template by" [:a {:href "http://sindresorhus.com"} "Sindre Sorhus"]]
        [:p "Created by" [:a {:href "http://todomvc.com"} "you"]]
        [:p "Part of" [:a {:href "http://todomvc.com"} "TodoMVC"]]]])))

(rrf/reg-event-fx
  ::index
  (fn [{:keys [db]} _]
    {:db (assoc db :current-filter nil)}))

(rrf/reg-event-fx
  ::active
  (fn [{:keys [db]} _]
    {:db (assoc db :current-filter :active)}))

(rrf/reg-event-fx
  ::completed
  (fn [{:keys [db]} _]
    {:db (assoc db :current-filter :completed)}))

#?(:clj
   (defn get-todos [ctx]
     (->> (:body (todo-backend/list-all-todos ctx))
          (map (fn [todo] [(:id todo) todo]))
          (into {}))))

(def routes
  ["" {:middleware [rrf/wrap-rf]}
   [["/"
     {:name ::index
      :get todo-page
      #?@(:clj [:server-props
                (fn [ctx]
                  {:current-filter nil
                   :todos (get-todos ctx)})]
          :cljs [:fx [[:dispatch [::index]]]])}]

    ["/active"
     {:name ::active
      :get todo-page
      #?@(:clj [:server-props
                (fn [ctx]
                  {:current-filter :active
                   :todos (get-todos ctx)})]
          :cljs [:fx [[:dispatch [::active]]]])}]

    ["/completed"
     {:name ::completed
      :get todo-page
      #?@(:clj [:server-props
                (fn [ctx]
                  {:current-filter :completed
                   :todos (get-todos ctx)})]
          :cljs [:fx [[:dispatch [::completed]]]])}]]])

(def router (r/router routes))
(def href #(apply rrf/href-alpha router %&))

(def plugin
  {:routes routes})
