;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; This Source Code Form is "Incompatible With Secondary Licenses", as
;; defined by the Mozilla Public License, v. 2.0.
;;
;; Copyright (c) 2019 Andrey Antukh <niwi@niwi.nz>

(ns uxbox.services.queries.icons
  (:require
   [clojure.spec.alpha :as s]
   [promesa.core :as p]
   [promesa.exec :as px]
   [uxbox.common.exceptions :as ex]
   [uxbox.common.spec :as us]
   [uxbox.db :as db]
   [uxbox.media :as media]
   [uxbox.images :as images]
   [uxbox.services.queries :as sq]
   [uxbox.services.util :as su]
   [uxbox.util.blob :as blob]
   [uxbox.util.data :as data]
   [uxbox.util.uuid :as uuid]
   [vertx.core :as vc]))

;; --- Helpers & Specs

(s/def ::id ::us/uuid)
(s/def ::profile-id ::us/uuid)
(s/def ::collection-id (s/nilable ::us/uuid))

(defn decode-row
  [{:keys [metadata] :as row}]
  (when row
    (cond-> row
      metadata (assoc :metadata (blob/decode metadata)))))



;; --- Query: Collections

(def ^:private sql:collections
  "select *,
          (select count(*) from icon where collection_id = ic.id) as num_icons
     from icon_collection as ic
    where (ic.profile_id = $1 or
           ic.profile_id = '00000000-0000-0000-0000-000000000000'::uuid)
      and ic.deleted_at is null
    order by ic.created_at desc")

(s/def ::icon-collections
  (s/keys :req-un [::profile-id]))

(sq/defquery ::icon-collections
  [{:keys [profile-id] :as params}]
  (let [sqlv [sql:collections profile-id]]
    (db/query db/pool sqlv)))



;; --- Icons By Collection ID

(def ^:private sql:icons
  "select *
     from icon as i
    where (i.profile_id = $1 or
           i.profile_id = '00000000-0000-0000-0000-000000000000'::uuid)
      and i.deleted_at is null
      and i.collection_id = $2
    order by i.created_at desc")

(s/def ::icons
  (s/keys :req-un [::profile-id ::collection-id]))

(sq/defquery ::icons
  [{:keys [profile-id collection-id] :as params}]
  (-> (db/query db/pool [sql:icons profile-id collection-id])
      (p/then' #(mapv decode-row %))))


;; --- Query: Icon (by ID)

(declare retrieve-icon)

(s/def ::id ::us/uuid)
(s/def ::icon
  (s/keys :req-un [::profile-id ::id]))

(sq/defquery ::icon
  [{:keys [id] :as params}]
  (-> (retrieve-icon db/pool id)
      (p/then' su/raise-not-found-if-nil)))

(defn retrieve-icon
  [conn id]
  (let [sql "select * from icon
              where id = $1
                and deleted_at is null;"]
    (-> (db/query-one conn [sql id])
        (p/then' su/raise-not-found-if-nil))))

