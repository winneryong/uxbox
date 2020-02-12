;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; This Source Code Form is "Incompatible With Secondary Licenses", as
;; defined by the Mozilla Public License, v. 2.0.
;;
;; Copyright (c) 2020 Andrey Antukh <niwi@niwi.nz>

(ns uxbox.services.mutations.teams
  (:require
   [clojure.spec.alpha :as s]
   [promesa.core :as p]
   [uxbox.db :as db]
   [uxbox.common.exceptions :as ex]
   [uxbox.common.spec :as us]
   [uxbox.services.mutations :as sm]
   [uxbox.services.util :as su]
   [uxbox.util.blob :as blob]
   [uxbox.util.uuid :as uuid]))

;; --- Helpers & Specs

(s/def ::id ::us/uuid)
(s/def ::name ::us/string)
(s/def ::profile-id ::us/uuid)

;; --- Mutation: Create Team

(declare create-team)
(declare create-team-profile)

(s/def ::create-team
  (s/keys :req-un [::profile-id ::name]
          :opt-un [::id]))

(sm/defmutation ::create-team
  [params]
  (db/with-atomic [conn db/pool]
    (p/let [team (create-team conn params)]
      (create-team-profile conn (assoc params :team-id (:id team)))
      team)))

(def ^:private sql:insert-team
  "insert into team (id, name, photo)
   values ($1, $2, '')
   returning *")

(defn- create-team
  [conn {:keys [id profile-id name] :as params}]
  (let [id (or id (uuid/next))]
    (db/query-one conn [sql:insert-team id name])))

(def ^:private sql:create-team-profile
  "insert into team_profile_rel (team_id, profile_id, is_owner, is_admin, can_edit)
   values ($1, $2, true, true, true)
   returning *")

(defn- create-team-profile
  [conn {:keys [team-id profile-id] :as params}]
  (-> (db/query-one conn [sql:create-team-profile team-id profile-id])
      (p/then' su/constantly-nil)))


