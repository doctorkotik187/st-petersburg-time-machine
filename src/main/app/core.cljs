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
           :selected nil}))

(defonce ^:private map-ref (atom nil))
(defonce ^:private markers-ref (atom {}))

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

;; --- Helpers ---

(defn- color-style [color]
  (str "display:inline-block;width:12px;height:12px;border-radius:50%;"
       "background:" color ";"
       "border:2px solid rgba(255,255,255,0.8);"
       "box-shadow:0 0 8px " color ";"))

;; --- Map operations ---

(defn- create-marker [landmark]
  (let [color (get category-colors (:category landmark) "#c9a96e")
        ^js icon (.divIcon L
                  #js {:className "custom-marker-icon"
                       :html (str "<div style='" (color-style color) "'></div>")
                       :iconSize #js [12 12]
                       :iconAnchor #js [6 6]
                       :popupAnchor #js [0 -8]})
        ^js marker (.marker L
                    #js [(:lat landmark) (:lon landmark)]
                    #js {:icon icon
                         :title (:name landmark)})]
    (.bindPopup marker
                (str "<b>" (:name landmark) "</b><br>"
                     "Built: " (:yearBuilt landmark))
                #js {:className "dark-popup"})
    marker))

(defn- clear-markers []
  (when-let [^js map @map-ref]
    (doseq [[_ ^js marker] @markers-ref]
      (when marker
        (.removeLayer map marker))))
  (reset! markers-ref {}))

(defn- place-markers [landmarks year]
  (when-let [^js map @map-ref]
    (let [visible (filter #(<= (:yearBuilt %) year) landmarks)
          new-markers (loop [vs (seq visible)
                             acc {}]
                        (if vs
                          (let [l (first vs)
                                ^js m (create-marker l)]
                            (.addTo m map)
                            (recur (next vs) (assoc acc (:id l) m)))
                          acc))]
      (reset! markers-ref new-markers))))

(defn- refresh-markers []
  (clear-markers)
  (let [{:keys [landmarks year]} @app-state]
    (when (seq landmarks)
      (place-markers landmarks year))))

(defn- ensure-map! [el]
  (when (and el (nil? @map-ref))
    (let [^js map (.map L el
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
      (reset! map-ref map)
      (js/console.log "Map initialized"))))

(defn- load-landmarks []
  (go (let [response (<! (http/get "/data/landmarks.json"))]
        (swap! app-state assoc :landmarks (:body response))
        (js/console.log "Loaded" (count (:body response)) "landmarks")
        (refresh-markers))))

;; --- Components ---

(defn- detail-panel []
  (when-let [selected (:selected @app-state)]
    [:div.detail-panel
     [:button.close-btn
      {:on-click #(swap! app-state assoc :selected nil)}
      "\u00D7"]
     [:h3 (:name selected)]
     [:div.detail-year
      "Built in " (:yearBuilt selected)]
     [:div.detail-desc (:description selected)]]))

(defn- sidebar []
  (let [landmarks (:landmarks @app-state)
        year (:year @app-state)
        selected (:selected @app-state)
        visible (filter #(<= (:yearBuilt %) year) landmarks)
        sorted (sort-by :yearBuilt visible)]
    [:div.sidebar
     [:h2 (str "Landmarks (" (count sorted) ")")]
     (cond
       (empty? landmarks)
       [:div {:style {:color "#666" :font-size "0.85rem" :padding "8px 0"}}
        "Loading..."]

       (empty? sorted)
       [:div {:style {:color "#666" :font-size "0.85rem" :padding "8px 0"}}
        "No landmarks for this year yet."]

       :else
       (for [l sorted]
         ^{:key (:id l)}
         [:div.landmark-card
          {:class (when (= (:id selected) (:id l)) "selected")
           :on-click #(swap! app-state assoc :selected l)}
          [:div.name (:name l)]
          [:div.year (str "Built: " (:yearBuilt l))]
          [:div.category (:category l)]]))]))

(defn- timeline []
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
                             (refresh-markers)))}]
     [:div.landmark-count
      (str cnt " of " total " landmarks visible")]]))

(defn- header []
  [:header
   [:h1 "St. Petersburg Time Machine"]
   [:span.year-display (:year @app-state)]])

(defn app []
  [:div.app-wrapper
   [header]
   [:div.main-content
    [:div#map {:ref (fn [el]
                      (ensure-map! el)
                      (load-landmarks))}]
    [sidebar]]
   [timeline]
   [detail-panel]])

;; --- Init ---

(defn init []
  (let [root (createRoot (.getElementById js/document "app"))]
    (.render root (r/as-element [app]))))
