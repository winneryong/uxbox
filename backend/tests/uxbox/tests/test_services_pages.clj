(ns uxbox.tests.test-services-pages
  (:require
   [clojure.spec.alpha :as s]
   [clojure.test :as t]
   [promesa.core :as p]
   [uxbox.db :as db]
   [uxbox.http :as http]
   [uxbox.services.mutations :as sm]
   [uxbox.services.queries :as sq]
   [uxbox.util.uuid :as uuid]
   [uxbox.tests.helpers :as th]))

(t/use-fixtures :once th/state-init)
(t/use-fixtures :each th/database-reset)

(t/deftest query-pages
  (let [prof @(th/create-profile db/pool 1)
        file @(th/create-file db/pool {:profile-id (:id prof)
                                       :index 1})
        page @(th/create-page db/pool {:profile-id (:id prof)
                                       :file-id (:id file)
                                       :index 1})

        data {::sq/type :pages
              :file-id (:id file)
              :profile-id (:id prof)}
        out (th/try-on! (sq/handle data))]
    ;; (th/print-result! out)
    (t/is (nil? (:error out)))
    (t/is (vector? (:result out)))
    (t/is (= 1 (count (:result out))))
    (t/is (= "page1" (get-in out [:result 0 :name])))
    (t/is (:id file) (get-in out [:result 0 :file-id]))))

(t/deftest mutation-create-project-page
  (let [prof @(th/create-profile db/pool 1)
        file @(th/create-file db/pool {:profile-id (:id prof)
                                       :index 1})
        data {::sm/type :create-page
              :data {:canvas []
                     :options {}
                     :shapes []
                     :shapes-by-id {}}
              :file-id (:id file)
              :ordering 1
              :name "test page"
              :profile-id (:id prof)}
        out (th/try-on! (sm/handle data))]
    ;; (th/print-result! out)
    (t/is (nil? (:error out)))
    (t/is (uuid? (get-in out [:result :id])))
    (t/is (= (:name data) (get-in out [:result :name])))
    (t/is (= (:data data) (get-in out [:result :data])))
    (t/is (= 0 (get-in out [:result :version])))))

(t/deftest mutation-update-project-page-1
  (let [prof @(th/create-profile db/pool 1)
        file @(th/create-file db/pool {:profile-id (:id prof)
                                       :index 1})
        page @(th/create-page db/pool {:profile-id (:id prof)
                                       :file-id (:id file)
                                       :index 1})
        data {::sm/type :update-page
              :id (:id page)
              :revn 99
              :profile-id (:id prof)
              :changes []}

        out (th/try-on! (sm/handle data))]

    ;; (th/print-result! out)

    (let [error (:error out)]
      (t/is (th/ex-info? error))
      (t/is (th/ex-of-type? error :service-error)))

    (let [error (ex-cause (:error out))]
      (t/is (th/ex-info? error))
      (t/is (th/ex-of-type? error :validation))
      (t/is (th/ex-of-code? error :revn-conflict)))))

(t/deftest mutation-update-project-page-2
  (let [prof @(th/create-profile db/pool 1)
        file @(th/create-file db/pool {:profile-id (:id prof)
                                       :index 1})
        page @(th/create-page db/pool {:profile-id (:id prof)
                                       :file-id (:id file)
                                       :index 1})
        sid  (uuid/next)
        data {::sm/type :update-page
              :id (:id page)
              :revn 0
              :profile-id (:id prof)
              :changes [{:type :add-shape
                         :id sid
                         :session-id (uuid/next)
                         :shape {:id sid
                                 :name "Rect"
                                 :type :rect}}]}

        out (th/try-on! (sm/handle data))]

    ;; (th/print-result! out)
    (t/is (nil? (:error out)))
    (t/is (= 1 (get-in out [:result :revn])))
    (t/is (= (:id page) (get-in out [:result :page-id])))
    (t/is (= :add-shape (get-in out [:result :changes 0 :type])))
    ))

(t/deftest mutation-update-project-page-3
  (let [prof @(th/create-profile db/pool 1)
        file @(th/create-file db/pool {:profile-id (:id prof)
                                       :index 1})
        page @(th/create-page db/pool {:profile-id (:id prof)
                                       :file-id (:id file)
                                       :index 1})
        sid  (uuid/next)
        data {::sm/type :update-page
              :id (:id page)
              :revn 0
              :profile-id (:id prof)
              :changes [{:type :add-shape
                         :id sid
                         :session-id (uuid/next)
                         :shape {:id sid
                                 :name "Rect"
                                 :type :rect}}]}

        out1 (th/try-on! (sm/handle data))
        out2 (th/try-on! (sm/handle data))]

    ;; (th/print-result! out1)
    ;; (th/print-result! out2)

    (t/is (nil? (:error out1)))
    (t/is (nil? (:error out2)))

    (t/is (= 1 (count (get-in out1 [:result :changes]))))
    (t/is (= 2 (count (get-in out2 [:result :changes]))))

    (t/is (= (:id data) (get-in out1 [:result :page-id])))
    (t/is (= (:id data) (get-in out2 [:result :page-id])))
    ))

(t/deftest mutation-delete-project-page
  (let [prof @(th/create-profile db/pool 1)
        file @(th/create-file db/pool {:profile-id (:id prof)
                                       :index 1})
        page @(th/create-page db/pool {:profile-id (:id prof)
                                       :file-id (:id file)
                                       :index 1})

        data {::sm/type :delete-page
              :id (:id page)
              :profile-id (:id prof)}
        out (th/try-on! (sm/handle data))]
    ;; (th/print-result! out)
    (t/is (nil? (:error out)))
    (t/is (nil? (:result out)))))
