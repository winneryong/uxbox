;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; This Source Code Form is "Incompatible With Secondary Licenses", as
;; defined by the Mozilla Public License, v. 2.0.
;;
;; Copyright (c) 2019 Andrey Antukh <niwi@niwi.nz>

(ns uxbox.services.mutations.icons
  (:require
   [clojure.spec.alpha :as s]
   [datoteka.core :as fs]
   [datoteka.storages :as ds]
   [promesa.core :as p]
   [promesa.exec :as px]
   [uxbox.common.exceptions :as ex]
   [uxbox.common.spec :as us]
   [uxbox.config :as cfg]
   [uxbox.db :as db]
   [uxbox.media :as media]
   [uxbox.images :as images]
   [uxbox.tasks :as tasks]
   [uxbox.services.queries.icons :refer [decode-row]]
   [uxbox.services.mutations :as sm]
   [uxbox.services.util :as su]
   [uxbox.util.blob :as blob]
   [uxbox.util.data :as data]
   [uxbox.util.uuid :as uuid]
   [uxbox.util.storage :as ust]
   [vertx.util :as vu]))

;; --- Helpers & Specs

(s/def ::id ::us/uuid)
(s/def ::name ::us/string)
(s/def ::profile-id ::us/uuid)
(s/def ::collection-id ::us/uuid)
(s/def ::width ::us/integer)
(s/def ::height ::us/integer)

(s/def ::view-box
  (s/and (s/coll-of number?)
         #(= 4 (count %))
         vector?))

(s/def ::content ::us/string)
(s/def ::mimetype ::us/string)

(s/def ::metadata
  (s/keys :opt-un [::width ::height ::view-box ::mimetype]))



;; --- Mutation: Create Collection

(declare create-icon-collection)

(s/def ::create-icon-collection
  (s/keys :req-un [::profile-id ::name]
          :opt-un [::id]))

(sm/defmutation ::create-icon-collection
  [{:keys [id profile-id name] :as params}]
  (db/with-atomic [conn db/pool]
    (create-icon-collection conn params)))

(def ^:private sql:create-icon-collection
  "insert into icon_collection (id, profile_id, name)
   values ($1, $2, $3)
   returning *;")

(defn- create-icon-collection
  [conn {:keys [id profile-id name] :as params}]
  (let [id (or id (uuid/next))]
    (db/query-one conn [sql:create-icon-collection id profile-id name])))



;; --- Collection Permissions Check

(def ^:private sql:select-collection
  "select id, profile_id
     from icon_collection
    where id=$1 and deleted_at is null
      for update")

(defn- check-collection-edition-permissions!
  [conn profile-id coll-id]
  (p/let [coll (-> (db/query-one conn [sql:select-collection coll-id])
                   (p/then' su/raise-not-found-if-nil))]
    (when (not= (:profile-id coll) profile-id)
      (ex/raise :type :validation
                :code :not-authorized))))



;; --- Mutation: Update Collection

(def ^:private sql:rename-collection
  "update icon_collection
      set name = $2
    where id = $1
   returning *")

(s/def ::rename-icon-collection
  (s/keys :req-un [::profile-id ::name ::id]))

(sm/defmutation ::rename-icon-collection
  [{:keys [id profile-id name] :as params}]
  (db/with-atomic [conn db/pool]
    (check-collection-edition-permissions! conn profile-id id)
    (db/query-one conn [sql:rename-collection id name])))



;; ;; --- Copy Icon

;; (declare create-icon)

;; (defn- retrieve-icon
;;   [conn {:keys [profile-id id]}]
;;   (let [sql "select * from icon
;;               where id = $1
;;                 and deleted_at is null
;;                 and (profile_id = $2 or
;;                      profile_id = '00000000-0000-0000-0000-000000000000'::uuid)"]
;;   (-> (db/query-one conn [sql id profile-id])
;;       (p/then' su/raise-not-found-if-nil))))

;; (s/def ::copy-icon
;;   (s/keys :req-un [:us/id ::collection-id ::profile-id]))

;; (sm/defmutation ::copy-icon
;;   [{:keys [profile-id id collection-id] :as params}]
;;   (db/with-atomic [conn db/pool]
;;     (-> (retrieve-icon conn {:profile-id profile-id :id id})
;;         (p/then (fn [icon]
;;                   (let [icon (-> (dissoc icon :id)
;;                                  (assoc :collection-id collection-id))]
;;                     (create-icon conn icon)))))))

;; --- Delete Collection

(def ^:private sql:mark-collection-deleted
  "update icon_collection
      set deleted_at = clock_timestamp()
    where id = $1
   returning id")

(s/def ::delete-icon-collection
  (s/keys :req-un [::profile-id ::id]))

(sm/defmutation ::delete-icon-collection
  [{:keys [profile-id id] :as params}]
  (db/with-atomic [conn db/pool]
    (check-collection-edition-permissions! conn profile-id id)
    (-> (db/query-one conn [sql:mark-collection-deleted id])
        (p/then' su/constantly-nil))))



;; --- Mutation: Create Icon (Upload)

(declare create-icon)

(s/def ::create-icon
  (s/keys :req-un [::profile-id ::name ::metadata ::content ::collection-id]
          :opt-un [::id]))

(sm/defmutation ::create-icon
  [{:keys [profile-id collection-id] :as params}]
  (db/with-atomic [conn db/pool]
    (check-collection-edition-permissions! conn profile-id collection-id)
    (create-icon conn params)))

(def ^:private sql:create-icon
  "insert into icon (id, profile_id, name, collection_id, content, metadata)
   values ($1, $2, $3, $4, $5, $6) returning *")

(defn create-icon
  [conn {:keys [id profile-id name collection-id metadata content]}]
  (let [id (or id (uuid/next))]
    (-> (db/query-one conn [sql:create-icon id profile-id name
                            collection-id content (blob/encode metadata)])
        (p/then' decode-row))))



;; --- Mutation: Update Icon

(def ^:private sql:update-icon
  "update icon
      set name = $3,
          collection_id = $4
    where id = $1
      and profile_id = $2
   returning *")

(s/def ::update-icon
  (s/keys :req-un [::id ::profile-id ::name ::collection-id]))

(sm/defmutation ::update-icon
  [{:keys [id name profile-id collection-id] :as params}]
  (db/with-atomic [conn db/pool]
    (check-collection-edition-permissions! conn profile-id collection-id)
    (-> (db/query-one db/pool [sql:update-icon id profile-id  name collection-id])
        (p/then' su/raise-not-found-if-nil))))



;; --- Mutation: Delete Icon

(def ^:private sql:mark-icon-deleted
  "update icon
      set deleted_at = clock_timestamp()
    where id = $1
      and profile_id = $2
   returning id")

(s/def ::delete-icon
  (s/keys :req-un [::profile-id ::id]))

(sm/defmutation ::delete-icon
  [{:keys [id profile-id] :as params}]
  (db/with-atomic [conn db/pool]
    (-> (db/query-one conn [sql:mark-icon-deleted id profile-id])
        (p/then' su/raise-not-found-if-nil))

    ;; Schedule object deletion
    (tasks/schedule! conn {:name "delete-object"
                           :delay cfg/default-deletion-delay
                           :props {:id id :type :icon}})

    nil))


