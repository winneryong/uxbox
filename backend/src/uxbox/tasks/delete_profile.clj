;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; This Source Code Form is "Incompatible With Secondary Licenses", as
;; defined by the Mozilla Public License, v. 2.0.
;;
;; Copyright (c) 2020 Andrey Antukh <niwi@niwi.nz>

(ns uxbox.tasks.delete-profile
  "Task for permanent deletion of profiles."
  (:require
   [clojure.spec.alpha :as s]
   [clojure.tools.logging :as log]
   [promesa.core :as p]
   [uxbox.common.exceptions :as ex]
   [uxbox.common.spec :as us]
   [uxbox.db :as db]
   [uxbox.media :as media]
   [uxbox.util.storage :as ust]
   [vertx.util :as vu]))

(declare select-profile)
(declare delete-profile-data)
(declare delete-teams)
(declare delete-files)
(declare delete-profile)

(s/def ::profile-id ::us/uuid)
(s/def ::props
  (s/keys :req-un [::profile-id]))

(defn handler
  [{:keys [props] :as task}]
  (us/verify ::props props)
  (db/with-atomic [conn db/pool]
    (-> (select-profile conn (:profile-id props))
        (p/then (fn [profile]
                  (if (or (:is-demo profile)
                          (not (nil? (:deleted-at profile))))
                    (delete-profile-data conn (:id profile))
                    (log/warn "Profile " (:id profile)
                              "does not match constraints for deletion")))))))

(defn- delete-profile-data
  [conn profile-id]
  (log/info "Proceding to delete all data related to profile" profile-id)
  (p/do!
   (delete-teams conn profile-id)
   (delete-files conn profile-id)
   (delete-profile conn profile-id)))

(def ^:private sql:select-profile
  "select id, is_demo, deleted_at
     from profile
    where id=$1 for update")

(defn- select-profile
  [conn profile-id]
  (db/query-one conn [sql:select-profile profile-id]))


(def ^:private sql:remove-owned-teams
  "with teams as (
     select distinct
            tpr.team_id as id
       from team_profile_rel as tpr
      where tpr.profile_id = $1
        and tpr.is_owner is true
   ), to_delete_teams as (
     select tpr.team_id as id
       from team_profile_rel as tpr
      where tpr.team_id in (select id from teams)
      group by tpr.team_id
     having count(tpr.profile_id) = 1
   )
   delete from team
    where id in (select id from to_delete_teams)
   returning id")

(defn- delete-teams
  [conn profile-id]
  (-> (db/query-one conn [sql:remove-owned-teams profile-id])
      (p/then' (constantly nil))))

(def ^:private sql:remove-owned-files
  "with files_to_delete as (
     select distinct
            fpr.file_id as id
       from file_profile_rel as fpr
      inner join file as f on (fpr.file_id = f.id)
      where fpr.profile_id = $1
        and fpr.is_owner is true
        and f.project_id is null
   )
   delete from file
    where id in (select id from files_to_delete)
   returning id")

(defn- delete-files
  [conn profile-id]
  (-> (db/query-one conn [sql:remove-owned-files profile-id])
      (p/then' (constantly nil))))

(defn delete-profile
  [conn profile-id]
  (let [sql "delete from profile where id=$1"]
    (-> (db/query conn [sql profile-id])
        (p/then' (constantly profile-id)))))

