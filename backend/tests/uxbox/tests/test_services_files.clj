(ns uxbox.tests.test-services-files
  (:require
   [clojure.test :as t]
   [promesa.core :as p]
   [datoteka.core :as fs]
   [uxbox.db :as db]
   [uxbox.media :as media]
   [uxbox.core :refer [system]]
   [uxbox.http :as http]
   [uxbox.services.mutations :as sm]
   [uxbox.services.queries :as sq]
   [uxbox.tests.helpers :as th]
   [uxbox.util.storage :as ust]
   [uxbox.util.uuid :as uuid]
   [vertx.util :as vu]))

(t/use-fixtures :once th/state-init)
(t/use-fixtures :each th/database-reset)

;; (t/deftest query-project-files
;;   (let [profile @(th/create-profile db/pool 2)
;;         proj @(th/create-project db/pool (:id profile) 1)
;;         pf   @(th/create-project-file db/pool (:id profile) (:id proj) 1)
;;         pp   @(th/create-project-page db/pool (:id profile) (:id pf) 1)
;;         data {::sq/type :project-files
;;               :profile-id (:id profile)
;;               :project-id (:id proj)}
;;         out (th/try-on! (sq/handle data))]
;;     ;; (th/print-result! out)
;;     (t/is (nil? (:error out)))
;;     (t/is (= 1 (count (:result out))))
;;     (t/is (= (:id pf)   (get-in out [:result 0 :id])))
;;     (t/is (= (:id proj) (get-in out [:result 0 :project-id])))
;;     (t/is (= (:name pf) (get-in out [:result 0 :name])))
;;     (t/is (= [(:id pp)] (get-in out [:result 0 :pages])))))

(t/deftest query-draft-files
  (let [profile @(th/create-profile db/pool 1)
        file    @(th/create-file db/pool {:profile-id (:id profile)
                                          :index 1})
        page    @(th/create-page db/pool {:profile-id (:id profile)
                                          :file-id (:id file)
                                          :index 1})
        data {::sq/type :draft-files
              :profile-id (:id profile)}
        out (th/try-on! (sq/handle data))]
    ;; (th/print-result! out)
    (t/is (nil? (:error out)))
    (t/is (= 1 (count (:result out))))
    (t/is (= (:id file)   (get-in out [:result 0 :id])))
    (t/is (= (:name file) (get-in out [:result 0 :name])))
    (t/is (= [(:id page)] (get-in out [:result 0 :pages])))
    ))


;; (t/deftest mutation-create-project-file
;;   (let [profile @(th/create-profile db/pool 1)
;;         proj @(th/create-project db/pool (:id profile) 1)
;;         data {::sm/type :create-project-file
;;               :profile-id (:id profile)
;;               :name "test file"
;;               :project-id (:id proj)}
;;         out (th/try-on! (sm/handle data))
;;         ]
;;     ;; (th/print-result! out)
;;     (t/is (nil? (:error out)))
;;     (t/is (= (:name data) (get-in out [:result :name])))
;;     (t/is (= (:project-id data) (get-in out [:result :project-id])))))


(t/deftest mutation-create-draft-file
  (let [profile @(th/create-profile db/pool 1)
        data {::sm/type :create-draft-file
              :profile-id (:id profile)
              :name "test file"}
        out (th/try-on! (sm/handle data))
        ]
    ;; (th/print-result! out)
    (t/is (nil? (:error out)))
    (t/is (= (:name data) (get-in out [:result :name])))
    (t/is (= nil (get-in out [:result :project-id])))))


(t/deftest mutation-rename-file
  (let [profile @(th/create-profile db/pool 1)
        file    @(th/create-file db/pool {:profile-id (:id profile)
                                          :index 1})
        data {::sm/type :rename-file
              :id (:id file)
              :name "new file name"
              :profile-id (:id profile)}
        out  (th/try-on! (sm/handle data))]
    ;; (th/print-result! out)
    ;; TODO: check the result
    (t/is (nil? (:error out)))
    (t/is (nil? (:result out)))))

(t/deftest mutation-delete-file
  (let [profile @(th/create-profile db/pool 1)
        file    @(th/create-file db/pool {:profile-id (:id profile)
                                          :index 1})
        data {::sm/type :delete-file
              :id (:id file)
              :profile-id (:id profile)}
        out  (th/try-on! (sm/handle data))]
    ;; (th/print-result! out)
    (t/is (nil? (:error out)))
    (t/is (nil? (:result out)))
    (let [sql "select * from file
                where deleted_at is null"
          res @(db/query db/pool sql)]
      (t/is (empty? res)))))

(t/deftest mutation-upload-file-image
  (let [profile @(th/create-profile db/pool 1)
        file    @(th/create-file db/pool {:profile-id (:id profile)
                                          :index 1})
        content {:name "sample.jpg"
                 :path "tests/uxbox/tests/_files/sample.jpg"
                 :mtype "image/jpeg"
                 :size 312043}
        data {::sm/type :upload-file-image
              :profile-id (:id profile)
              :file-id (:id file)
              :name "testfile"
              :content content
              :width 800
              :height 800}

        out (th/try-on! (sm/handle data))]

    ;; (th/print-result! out)

    (t/is (= (:id file) (get-in out [:result :file-id])))
    (t/is (= (:name data) (get-in out [:result :name])))
    (t/is (= (:width data) (get-in out [:result :width])))
    (t/is (= (:height data) (get-in out [:result :height])))
    (t/is (= (:mimetype data) (get-in out [:result :mimetype])))

    (t/is (string? (get-in out [:result :path])))
    (t/is (string? (get-in out [:result :thumb-path])))
    (t/is (string? (get-in out [:result :uri])))
    (t/is (string? (get-in out [:result :thumb-uri])))
    ))

;; (t/deftest mutation-import-image-file-from-collection
;;   (let [profile @(th/create-profile db/pool 1)
;;         proj @(th/create-project db/pool (:id profile) 1)
;;         pf   @(th/create-project-file db/pool (:id profile) (:id proj) 1)
;;         coll @(th/create-images-collection db/pool (:id profile) 1)
;;         image-id (uuid/next)

;;         content {:name "sample.jpg"
;;                  :path "tests/uxbox/tests/_files/sample.jpg"
;;                  :mtype "image/jpeg"
;;                  :size 312043}

;;         data {::sm/type :upload-image
;;               :id image-id
;;               :profile-id (:id profile)
;;               :collection-id (:id coll)
;;               :name "testfile"
;;               :content content}
;;         out1 (th/try-on! (sm/handle data))]

;;     ;; (th/print-result! out1)
;;     (t/is (nil? (:error out1)))
;;     (t/is (= image-id (get-in out1 [:result :id])))
;;     (t/is (= "testfile" (get-in out1 [:result :name])))
;;     (t/is (= "image/jpeg" (get-in out1 [:result :mtype])))
;;     (t/is (= "image/webp" (get-in out1 [:result :thumb-mtype])))

;;     (let [data2 {::sm/type :import-image-to-file
;;                  :image-id image-id
;;                  :file-id (:id pf)
;;                  :profile-id (:id profile)}
;;           out2 (th/try-on! (sm/handle data2))]

;;       ;; (th/print-result! out2)
;;       (t/is (nil? (:error out2)))
;;       (t/is (not= (get-in out2 [:result :path])
;;                   (get-in out1 [:result :path])))
;;       (t/is (not= (get-in out2 [:result :thumb-path])
;;                   (get-in out1 [:result :thumb-path]))))))



