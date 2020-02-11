;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) 2019 Andrey Antukh <niwi@niwi.nz>

(ns uxbox.services.queries.images
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

(s/def ::id ::us/uuid)
(s/def ::name ::us/string)
(s/def ::profile-id ::us/uuid)
(s/def ::collection-id (s/nilable ::us/uuid))

;; --- Query: Image Collections

(def ^:private sql:collections
  "select *,
          (select count(*) from image where collection_id = ic.id) as num_images
     from image_collection as ic
    where (ic.profile_id = $1 or
           ic.profile_id = '00000000-0000-0000-0000-000000000000'::uuid)
      and ic.deleted_at is null
    order by ic.created_at desc;")

(s/def ::image-collections
  (s/keys :req-un [::profile-id]))

(sq/defquery ::image-collections
  [{:keys [profile-id] :as params}]
  (db/query db/pool [sql:collections profile-id]))


;; --- Query: Image by ID

(defn retrieve-image
  [conn id]
  (let [sql "select * from image
              where id = $1
                and deleted_at is null;"]
    (db/query-one conn [sql id])))

(s/def ::id ::us/uuid)
(s/def ::image-by-id
  (s/keys :req-un [::profile-id ::id]))

(sq/defquery ::image-by-id
  [params]
  (-> (retrieve-image db/pool (:id params))
      (p/then' su/raise-not-found-if-nil)
      (p/then' #(images/resolve-urls % :path :uri))
      (p/then' #(images/resolve-urls % :thumb-path :thumb-uri))))

;; --- Query: Images by collection ID

(def sql:images-by-collection
  "select * from image
    where (profile_id = $1 or
           profile_id = '00000000-0000-0000-0000-000000000000'::uuid)
      and deleted_at is null
   order by created_at desc")

(def sql:images-by-collection
  (str "with image as (" sql:images-by-collection ")
        select im.* from image as im
         where im.collection_id = $2"))

(s/def ::images-by-collection
  (s/keys :req-un [::profile-id]
          :opt-un [::collection-id]))

;; TODO: check if we can resolve url with transducer for reduce
;; garbage generation for each request

(sq/defquery ::images-by-collection
  [{:keys [profile-id collection-id] :as params}]
  (let [sqlv [sql:images-by-collection profile-id collection-id]]
    (-> (db/query db/pool sqlv)
        (p/then' (fn [rows]
                   (->> rows
                        (mapv #(images/resolve-urls % :path :uri))
                        (mapv #(images/resolve-urls % :thumb-path :thumb-uri))))))))
