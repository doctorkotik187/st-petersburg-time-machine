(ns app.core
  (:require
   ["leaflet" :as L]
   ["react-dom/client" :refer [createRoot]]
   [reagent.core :as r]
   [cljs.core.async :refer [<! go]]
   [cljs-http.client :as http]))

;; --- State ---

(defonce app-state
  (r/atom {:year 2024
           :landmarks []
           :selected nil
           :map nil
           :markers {}}))

(def min-year 1700)
(def max-year 2024)

(def category-colors
  {"fortress"   "#e74c3c"
   "palace"     "#c9a96e"
   "church"     "#8e44ad"
   "government" "#2980b9"
   "monument"   "#e67e22"
   "theatre"    "#1abc9c"
   "museum"     "#27ae60"})

;; --- Data ---

(defn load-landmarks []
  (go (let [response (<! (http/get "/data/landmarks.json"))]
        (swap! app-state assoc :landmarks (:body response)))))

;; --- Map ---

(defn create-marker [landmark]
  (let [color (get category-colors (:category landmark) "#c9a96e")
        ^js icon (.divIcon L
                  #js {:className "custom-marker"
                       :html (str "<div style='width:14px;height:14px;border-radius:50%;"
                                  "background:" color ";"
                                  "border:2px solid rgba(255,255,255,0.7);"
                                  "box-shadow:0 0 6px " color ";'></div>")
                       :iconSize #js [14 14]
                       :iconAnchor #js [7 7]})
        ^js marker (.marker L
                    #js [(:lat landmark) (:lon landmark)]
                    #js {:icon icon
                         :title (:name landmark)})]
    (.bindPopup marker
                (str "<b>" (:name landmark) "</b><br>"
                     "Built: " (:yearBuilt landmark)))
    (.on marker "click"
         #(swap! app-state assoc :selected landmark))
    marker))

(defn update-markers! []
  (let [{:keys [map markers landmarks year]} @app-state]
    (when (and map (seq landmarks))
      ;; Remove old markers
      (doseq [[_ ^js marker] markers]
        (.removeLayer map marker))
      ;; Create new markers for visible landmarks
      (let [visible (filter #(<= (:yearBuilt %) year) landmarks)
            new-markers (persistent!
                         (reduce (fn [acc l]
                                   (assoc! acc (:id l) (create-marker l)))
                                 (transient {})
                                 visible))]
        ;; Add to map
        (doseq [[_ ^js m] new-markers]
          (.addTo m map))
        (swap! app-state assoc :markers new-markers)))))

(defn init-map []
  (let [^js map (.map L "map"
                      #js {:center #js [59.9343 30.3351]
                           :zoom 12
                           :zoomControl true
                           :attributionControl true})]
    (.addTo (.tileLayer L
             "https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png"
             #js {:attribution "&copy; OpenStreetMap &copy; CARTO"
                  :subdomains "abcd"
                  :maxZoom 19})
            map)
    map))

;; --- Components ---

(defn detail-panel []
  (when-let [selected (:selected @app-state)]
    [:div.detail-panel.visible
     [:button.close-btn
      {:on-click #(swap! app-state assoc :selected nil)}
      "\u00D7"]
     [:h3 (:name selected)]
     [:div.detail-year
      "Built in " (:yearBuilt selected)]
     [:div.detail-desc (:description selected)]]))

(defn sidebar []
  (let [landmarks (:landmarks @app-state)
        year (:year @app-state)
        selected (:selected @app-state)
        visible (filter #(<= (:yearBuilt %) year) landmarks)
        sorted (sort-by :yearBuilt visible)]
    [:div.sidebar
     [:h2 "Landmarks"]
     (cond
       (empty? landmarks)
       [:div {:style {:color "#666" :font-size "0.85rem" :padding "8px 0"}}
        "Loading..."]

       (empty? sorted)
       [:div {:style {:color "#666" :font-size "0.85rem" :padding "8px 0"}}
        "No landmarks built yet. Drag the timeline forward."]

       :else
       (for [l sorted]
         ^{:key (:id l)}
         [:div.landmark-card
          {:class (when (= (:id selected) (:id l)) "selected")
           :on-click #(swap! app-state assoc :selected l)}
          [:div.name (:name l)]
          [:div.year "Built: " (:yearBuilt l)]
          [:div.category (:category l)]]))]))

(defn timeline []
  (let [visible (filter #(<= (:yearBuilt %) (:year @app-state))
                         (:landmarks @app-state))
        cnt (count visible)
        total (count (:landmarks @app-state))]
    [:div.timeline-container
     [:div.timeline-labels
      [:span (str min-year)]
      [:span (str max-year)]]
     [:input {:type "range"
              :min min-year
              :max max-year
              :value (:year @app-state)
              :on-change (fn [e]
                           (let [new-year (js/parseInt (.. e -target -value))]
                             (swap! app-state assoc :year new-year)
                             (update-markers!)))}]
     [:div.landmark-count
      cnt " of " total " landmarks visible"]]))

(defn header []
  [:header
   [:h1 "St. Petersburg Time Machine"]
   [:span.year-display (:year @app-state)]])

(defn app []
  (r/create-class
   {:component-did-mount
    (fn [_]
      (let [map (init-map)]
        (swap! app-state assoc :map map)
        (load-landmarks)
        ;; Watch for landmarks loading, then place markers
        (add-watch app-state :init-markers
                   (fn [_ _ _ new-state]
                     (when (and (:map new-state)
                                (seq (:landmarks new-state)))
                       (update-markers!)
                       (remove-watch app-state :init-markers))))))

    :reagent-render
    (fn []
      [:div.app-wrapper
       [header]
       [:div.main-content
        [:div#map]
        [sidebar]]
       [timeline]
       [detail-panel]])}))

;; --- Init ---

(defn init []
  (let [root (createRoot (.getElementById js/document "app"))]
    (.render root (r/as-element [app]))))
