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

(def category-names
  {"fortress"   "Крепость"
   "palace"     "Дворец"
   "church"     "Храм"
   "government" "Здание"
   "monument"   "Памятник"
   "theatre"    "Театр"
   "museum"     "Музей"})

;; --- Easter eggs ---
(def secrets
  {:header-click "Каждый раз, когда смотришь на этот город, помни: где-то в нём кто-то думает о тебе."
   :triple-click "Ты нашла секрет. В Петербурге 152 моста, но только один ведёт к моему сердцу."
   :year-1812 "1812 — год, когда Россия победила Наполеона. Год, доказавший: некоторые вещи стоят того, чтобы за них сражаться. Например, любовь."
   :year-1945 "1945 — День Победы. День, когда мир узнал: свет всегда побеждает тьму. Как ты в моей жизни."
   :year-1991 "1991 — город вернул своё имя. Некоторые вещи слишком прекрасны, чтобы их скрывать."
   :fortress "Петропавловская крепость — здесь родился город. Ты — то, от чего у меня замирает сердце."
   :winter-palace "Зимний дворец пережил революции. Настоящие чувства переживают всё."
   :peterhof "Фонтаны Петергофа текут вверх — против природы. Как любовь к тебе — против логики."
   :marinsky "В Мариинском балерины танцуют на кончиках пальцев. А я хожу по краю, думая о тебе."
   :bronze-horseman "Медный всадник никогда не двигается. Но моё сердце — каждый раз, когда я думаю о тебе."
   :church-blood "Построен на месте трагедии — стал самым красивым храмом. Боль может стать красотой. Ты это доказала."
   :isaac "Купол Исаакия покрыт чистым золотом. Но ничто не сияет ярче твоей улыбки."
   :admiralty "Золотой шпиль Адмиралтейства — компас города. Ты — мой компас."
   :kazan "Казанский собор обнимает Невский своей колоннадой. Как хочется обнять тебя."
   :trinity "Троицкий собор хранит тишину. В этой тишине можно услышать, как бьётся сердце."
   :general-staff "Через арку Главного штаба виден весь Невский. С тобой видно весь мир."
   :russian-museum "В Русском музее — души художников. В моём сердце — только ты."})

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
      "admiralty" (reveal-secret! :admiralty)
      "kazan-cathedral" (reveal-secret! :kazan)
      "trinity-cathedral" (reveal-secret! :trinity)
      "general-staff" (reveal-secret! :general-staff)
      "russian-museum" (reveal-secret! :russian-museum)
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
                     "Построено: " (:yearBuilt landmark))
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
      (reset! map-ref map))))

(defn- load-landmarks []
  (go (let [response (<! (http/get "/data/landmarks.json"))]
        (swap! app-state assoc :landmarks (:body response))
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
     [:div.detail-year (str "Построено в " (:yearBuilt selected) " году")]
     [:div.detail-desc (:description selected)]]))

(defn- sidebar []
  (let [landmarks (:landmarks @app-state)
        year (:year @app-state)
        selected (:selected @app-state)
        visible (filter #(<= (:yearBuilt %) year) landmarks)
        sorted (sort-by :yearBuilt visible)]
    [:div.sidebar
     [:h2 (str "Места (" (count sorted) ")")]
     (cond
       (empty? landmarks)
       [:div {:style {:color "#666" :font-size "0.9rem" :padding "8px 0"}}
        "Загружаем..."]

       (empty? sorted)
       [:div {:style {:color "#666" :font-size "0.9rem" :padding "8px 0"}}
        "В этот год ещё ничего не построили."]

       :else
       (for [l sorted]
         ^{:key (:id l)}
         [:div.landmark-card
          {:class (when (= (:id selected) (:id l)) "selected")
           :on-click #(do
                        (handle-landmark-click l)
                        (swap! app-state assoc :selected l))}
          [:div.name (:name l)]
          [:div.year (str "Построено: " (:yearBuilt l))]
          [:div.category (get category-names (:category l))]]))]))

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
      (str cnt " из " total " мест видно")]]))

(defn- header []
  [:header
   {:on-click #(reveal-secret! :header-click)}
   [:h1 "Машина времени: Санкт-Петербург"]
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
