(ns brainiac.plugins.cta-train-tracker
  (:import [java.text SimpleDateFormat]
           [java.util Calendar TimeZone])
  (:require [brainiac.plugin :as brainiac]
            [brainiac.xml-utils :as xml]
            [clojure.contrib.zip-filter.xml :as zf]))

;20120109 20:27:56
(def time-format (SimpleDateFormat. "yyyyMMDD HH:mm:ss"))
(defn now [] (System/currentTimeMillis))

(defn due-in-minutes [due-in-millis]
  (if (< due-in-millis (* 60 1000))
    "due"
    (str (int (/ due-in-millis (* 60 1000))) " min")))

(defn arrival-in-central [arrival-time]
  (let [calendar (Calendar/getInstance)]
    (.setTimeZone calendar (TimeZone/getTimeZone "America/Chicago"))
    (.setTime calendar arrival-time)
    (.getTimeInMillis calendar)))

(defn parse-eta [node]
  (let [arrival-time (.parse time-format (zf/xml1-> node :arrT zf/text))
        due-in-millis (- (arrival-in-central arrival-time) (now))
        destination (zf/xml1-> node :destNm zf/text)]
    (str destination " " (due-in-minutes due-in-millis))))

(defn transform [stream]
  (let [xml-zipper (xml/parse-xml stream)
        stop (zf/xml1-> xml-zipper :eta :staNm zf/text)
        route (zf/xml1-> xml-zipper :eta :rt zf/text)]
    (assoc {}
      :name "train-tracker"
      :type "list"
      :title (format "%s (%s)" stop route)
      :data (zf/xml-> xml-zipper :eta parse-eta))))

(defn tracker-url [map-id api-key]
  (format "http://lapi.transitchicago.com/api/1.0/ttarrivals.aspx?mapid=%s&key=%s&max=4" map-id api-key))

(defn configure [{:keys [map-id api-key]}]
  (brainiac/schedule
    5000
    (brainiac/simple-http-plugin
      {:method :get :url (tracker-url map-id api-key)}
      transform)))

