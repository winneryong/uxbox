;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) 2016 Andrey Antukh <niwi@niwi.nz>

(ns uxbox.services.queries.profile
  (:require
   [clojure.spec.alpha :as s]
   [promesa.core :as p]
   [promesa.exec :as px]
   [uxbox.common.exceptions :as ex]
   [uxbox.common.spec :as us]
   [uxbox.db :as db]
   [uxbox.images :as images]
   [uxbox.services.queries :as sq]
   [uxbox.services.util :as su]
   [uxbox.util.blob :as blob]))

;; --- Helpers & Specs

(declare strip-private-attrs)

(s/def ::email ::us/email)
(s/def ::fullname ::us/string)
(s/def ::metadata any?)
(s/def ::old-password ::us/string)
(s/def ::password ::us/string)
(s/def ::path ::us/string)
(s/def ::user ::us/uuid)
(s/def ::profile-id ::us/uuid)

;; --- Query: Profile (own)

(defn retrieve-profile
  [conn id]
  (let [sql "select * from profile where id=$1 and deleted_at is null"]
    (db/query-one db/pool [sql id])))

(s/def ::profile
  (s/keys :req-un [::profile-id]))

(sq/defquery ::profile
  [{:keys [profile-id] :as params}]
  (-> (retrieve-profile db/pool profile-id)
      (p/then' su/raise-not-found-if-nil)
      (p/then' strip-private-attrs)
      (p/then' #(images/resolve-media-uris % [:photo :photo-uri]))))



;; --- Attrs Helpers

(defn strip-private-attrs
  "Only selects a publicy visible profile attrs."
  [profile]
  (select-keys profile [:id :fullname :lang :email :created-at :photo]))
