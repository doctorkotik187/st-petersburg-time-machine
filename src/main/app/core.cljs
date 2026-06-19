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
           :secret nil}))

(defonce ^:private map-ref (atom nil))
(defonce ^:private markers-ref (atom {}))
(defonce ^:private click-counts (atom {}))

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

;; --- Easter eggs (hidden love messages) ---
;; Triggered by: triple-clicking landmarks, visiting specific years, clicking header

(def secrets
  {:header-click "Every time you look at this city, remember: somewhere in it, someone is thinking of you."
   :triple-click "You found a secret. St. Petersburg has 152 bridges, but only one leads to my heart."
   :year-1812 "1812 — the year Russia defeated Napoleon. The year that proved some things are worth fighting for. Like love."
   :year-1945 "1945 — Victory Day. The day the world learned that light always wins over darkness. Like you in my life."
   :year-1991 "1991 — the city got its name back. Some things are too beautiful to stay hidden."
   :fortress "Peter and Paul Fortress — where the city began. You are where my everything begins."
   :winter-palace "The Winter Palace survived revolutions. Real love survives everything."
   :peterhof "Peterhof's fountains flow upward — against nature. Like loving you — against all logic."
   :marinsky "At the Mariinsky, ballerinas dance on their toes. I dance on my words thinking of you."
   :bronze-horseman "The Bronze Horseman never moves. But my heart moves every time I see you."
   :church-blood "Built on a place of tragedy, it became the most beautiful church. Pain can become beauty. You proved that."
   :isaac "St. Isaac's dome is covered in pure gold. But no gold shines brighter than your eyes."})

;; --- Helpers ---

(defn- color-style [color]
  (str "display:inline-block;width:12px;height:12px;border-radius:50%;"
       "background:" color ";"
       "border:2px solid rgba(255,255,255,0.8);"
       "box-shadow:0 0 8px " color ";"))

(defn- reveal-secret! [key]
  (swap! app-state assoc :secret (get secrets key)))

(defn- handle-landmark-click [landmark]
  (let [id (:id landmark)
        clicks (swap! click-counts update id (fnil inc 0))]
    (when (>= clicks 3)
      (reveal-secret! :triple-click)
      (swap! click-counts assoc id 0))
    (case id
      "peter-paul-fortress" (reveal-secret! :fortress)
      "winter-palace" (reveal-secret! :winter-palace)
      "peterhof" (reveal-secret! :peterhof)
      "mariinsky-theatre" (reveal-secret! :marinsky)
      "bronze-horseman" (reveal-secret! :bronze-horseman)
      "church-savior-blood" (reveal-secret! :church-blood)
      "isaac-cathedral" (reveal-secret! :isaac)
      nil)))

(defn- handle-year-change [year]
  (cond
    (= year 1812) (reveal-secret! :year-1812)
    (= year 1945) (reveal-secret! :year-1945)
    (= year 1991) (reveal-secret! :year-1991)
    :else nil))

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
    (.on marker "click" #(handle-landmark-click landmark))
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

(defn- secret-toast []
  (when-let [msg (:secret @app-state)]
    [:div.secret-toast
     {:on-click #(swap! app-state assoc :secret nil)}
     [:span.secret-close "x"]
     [:p msg]]))

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
           :on-click #(do
                        (handle-landmark-click l)
                        (swap! app-state assoc :selected l))}
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
                             (handle-year-change new-year)
                             (refresh-markers)))}]
     [:div.landmark-count
      (str cnt " of " total " landmarks visible")]]))

(defn- header []
  [:header
   {:on-click #(reveal-secret! :header-click)}
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
   [detail-panel]
   [secret-toast]])

;; --- Init ---

(defn init []
  (let [root (createRoot (.getElementById js/document "app"))]
    (.render root (r/as-element [app]))))
