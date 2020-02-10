;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) 2019 Andrey Antukh <niwi@niwi.nz>

(ns uxbox.fixtures
  "A initial fixtures."
  (:require
   [clojure.tools.logging :as log]
   [sodi.pwhash :as pwhash]
   [mount.core :as mount]
   [promesa.core :as p]
   [uxbox.config :as cfg]
   [uxbox.common.data :as d]
   [uxbox.core]
   [uxbox.db :as db]
   [uxbox.media :as media]
   [uxbox.migrations]
   [uxbox.util.blob :as blob]
   [uxbox.util.uuid :as uuid]))

(defn- mk-uuid
  [prefix & args]
  (uuid/namespaced uuid/oid (apply str prefix (interpose "-" args))))

;; --- Profiles creation

(def sql:create-profile
  "insert into profile (id, fullname, email, password, photo)
   values ($1, $2, $3, $4, $5)
   returning *;")

(def password (pwhash/derive "123123"))

(defn create-profile
  [conn profile-index]
  (log/info "create profile" profile-index)
  (let [sql sql:create-profile
        id (mk-uuid "profile" profile-index)
        fullname (str "Profile " profile-index)
        email (str "profile" profile-index ".test@uxbox.io")
        photo ""]
  (db/query-one conn [sql id fullname email password photo])))


;; --- Teams Creation

(def sql:create-team
  "insert into team (id, name, photo)
   values ($1, $2, $3)
   returning *;")

(defn create-team
  [conn team-index]
  (log/info "create team" team-index)
  (let [sql sql:create-team
        id (mk-uuid "team" team-index)
        name (str "Team" team-index)]
    (db/query-one conn [sql id name ""])))


;; --- Team Profile Relation Creation

(def sql:create-team-profile
  "insert into team_profile_rel (team_id, profile_id, is_owner, is_admin, can_edit)
   values ($1, $2, $3, $4, $5)
   returning *;")

(defn create-team-profile
  [conn team-index owner? profile-index]
  (log/info "create team profile" team-index profile-index)
  (let [team-id (mk-uuid "team" team-index)
        profile-id (mk-uuid "profile" profile-index)
        sql sql:create-team-profile]
    (db/query-one conn [sql team-id profile-id owner? true true])))


;; --- Projects creation

(def sql:create-project
  "insert into project (id, team_id, name)
   values ($1, $2, $3)
   returning *;")

(defn create-project
  [conn team-index project-index]
  (log/info "create project" team-index project-index)
  (let [sql sql:create-project
        id (mk-uuid "project" team-index project-index)
        team-id (mk-uuid "team" team-index)
        name (str "project " project-index)]
    (db/query-one conn [sql id team-id name])))

;; (create-project-profile-relation conn project-index profile-index)
;; (when (and (= project-index 0)
;;            (> profile-index 0))
;;   (create-project-profile-relation conn project-index (dec profile-index) false)))))


;; --- Project Profile Relation Creation

(def sql:create-project-profile-relation
  "insert into project_profile_rel (project_id, profile_id, is_owner, is_admin, can_edit)
   values ($1, $2, $3, $4, $5)
   returning *")

(defn create-project-profile-relation
  [conn team-index profile-index owner? project-index]
  (log/info "create project profile" profile-index project-index)
  (let [sql sql:create-project-profile-relation
        team-id (mk-uuid "team" team-index)
        project-id (mk-uuid "project" team-index project-index)
        profile-id (mk-uuid "profile" profile-index)]
    (db/query-one conn [sql project-id profile-id owner? true true])))


;; --- Create Page Files

(def sql:create-project-file
  "insert into file (id, project_id, name)
   values ($1, $2, $3 ) returning *")

(defn create-project-file
  [conn team-index project-index file-index]
  (log/info "create project file" team-index project-index file-index)
  (let [sql sql:create-project-file
        id (mk-uuid "file" team-index project-index file-index)
        project-id (mk-uuid "project" team-index project-index)
        name (str "file " file-index "," project-index)]
    (db/query-one conn [sql id project-id name])))

(defn create-draft-file
  [conn profile-index file-index]
  (log/info "create draft file" profile-index file-index)
  (let [sql sql:create-project-file
        id (mk-uuid "draft-file" profile-index file-index)
        name (str "file " file-index "," profile-index)]
    (db/query-one conn [sql id nil name])))

;; --- Create Pages

(def sql:create-project-page
  "insert into page (id, file_id, name,
                     version, ordering, data)
   values ($1, $2, $3, $4, $5, $6)
   returning id;")

(defn create-project-page
  [conn team-index project-index file-index page-index]
  (log/info "create project page" team-index project-index file-index page-index)
  (let [data {:version 1
              :shapes []
              :canvas []
              :options {}
              :shapes-by-id {}}

        sql sql:create-project-page

        id (mk-uuid "page" team-index project-index file-index page-index)
        file-id (mk-uuid "file" team-index project-index file-index)
        name (str "page " page-index)
        version 0
        ordering page-index
        data (blob/encode data)]
    (db/query-one conn [sql id file-id name version ordering data])))


(defn create-page
  [conn profile-index file-index page-index]
  (log/info "create draft page" profile-index file-index page-index)
  (let [data {:version 1
              :shapes []
              :canvas []
              :options {}
              :shapes-by-id {}}

        sql sql:create-project-page

        id (mk-uuid "draft-page" profile-index file-index page-index)
        file-id (mk-uuid "draft-file" profile-index file-index)
        name (str "page " page-index)
        version 0
        ordering page-index
        data (blob/encode data)]
    (db/query-one conn [sql id file-id name version ordering data])))

(def preset-small
  {:num-profiles 100
   :num-teams 10
   :num-teams-per-profile 3
   :num-projects-per-team 5
   :num-files-per-project 5
   :num-pages-per-file 3
   :num-draft-files-per-profile 10
   :num-draft-pages-per-file 3})

(defn rng-ids
  [rng n max]
  (let [stream (->> (.longs rng 0 max)
                    (.iterator)
                    (iterator-seq))]
    (reduce (fn [acc item]
              (if (= (count acc) n)
                (reduced (vec acc))
                (conj acc item)))
            #{}
            stream)))

(defn run
  [opts]
  (let [rng (java.util.Random. 1)

        create-draft-pages
        (fn [conn profile-index file-index]
          (p/run! (partial create-page conn profile-index file-index)
                  (range (:num-draft-pages-per-file opts))))


        create-draft-files
        (fn [conn profile-index]
          (p/do!
           (p/run! (partial create-draft-file conn profile-index)
                   (range (:num-draft-files-per-profile opts)))
           (p/run! (partial create-draft-pages conn profile-index)
                   (range (:num-draft-files-per-profile opts)))))

        create-pages-for-file
        (fn [conn team-index project-index file-index]
          (p/run! (partial create-project-page conn team-index project-index file-index)
                  (range (:num-pages-per-file opts))))

        assign-random-teams-to-profile
        (fn [conn profile-index]
          (let [team-indexes (rng-ids rng
                                      (:num-teams-per-profile opts)
                                      (:num-teams opts))]
            (p/run! #(create-team-profile conn % false profile-index)
                    team-indexes)))

        create-files-for-project
        (fn [conn team-index project-index]
          (p/do!
           (p/run! (partial create-project-file conn team-index project-index)
                   (range (:num-files-per-project opts)))
           (p/run! (partial create-pages-for-file conn team-index project-index)
                   (range (:num-files-per-project opts)))))

        create-projects-for-team
        (fn [conn team-index]
          (p/do!
           (p/run! (partial create-project conn team-index)
                   (range (:num-projects-per-team opts)))
           (p/run! (partial create-files-for-project conn team-index)
                   (range (:num-projects-per-team opts)))))

        create-team-with-projects
        (fn [conn team-index]
          (log/info "create team" team-index)
          (p/do!
           (create-team conn team-index)
           (create-projects-for-team conn team-index)))

        ]

    (db/with-atomic [conn db/pool]
      (p/run! (partial create-profile conn)
              (range (:num-profiles opts)))

      (p/run! (partial create-team-with-projects conn)
              (range (:num-teams opts)))

      (p/run! (partial assign-random-teams-to-profile conn)
              (range (:num-profiles opts)))

      (p/run! (partial create-draft-files conn)
              (range (:num-profiles opts))))))

(defn -main
  [& args]
  (try
    (-> (mount/only #{#'uxbox.config/config
                      #'uxbox.core/system
                      #'uxbox.db/pool
                      #'uxbox.migrations/migrations})
        (mount/start))
    (let [preset (case (first args)
                   (nil "small") preset-small
                   ;; "medium" preset-medium
                   ;; "big" preset-big
                   preset-small)]
      (log/info "Using preset:" (pr-str preset))
      (deref (run preset)))
    (finally
      (mount/stop))))
