;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; This Source Code Form is "Incompatible With Secondary Licenses", as
;; defined by the Mozilla Public License, v. 2.0.
;;
;; Copyright (c) 2019-2020 Andrey Antukh <niwi@niwi.nz>

(ns uxbox.tests.test-services-profile
  (:require
   [clojure.test :as t]
   [clojure.java.io :as io]
   [promesa.core :as p]
   [cuerdas.core :as str]
   [datoteka.core :as fs]
   [uxbox.db :as db]
   [uxbox.services.mutations :as sm]
   [uxbox.services.queries :as sq]
   [uxbox.tests.helpers :as th]))

(t/use-fixtures :once th/state-init)
(t/use-fixtures :each th/database-reset)

(t/deftest login
  (let [profile @(th/create-profile db/pool 1)]
    (t/testing "failed"
      (let [event {::sm/type :login
                   :email "profile1.test@nodomain.com"
                   :password "foobar"
                   :scope "foobar"}
            out (th/try-on! (sm/handle event))]

        ;; (th/print-result! out)
        (let [error (:error out)]
          (t/is (th/ex-info? error))
          (t/is (th/ex-of-type? error :service-error)))

        (let [error (ex-cause (:error out))]
          (t/is (th/ex-info? error))
          (t/is (th/ex-of-type? error :validation))
          (t/is (th/ex-of-code? error :uxbox.services.mutations.profile/wrong-credentials)))))

    (t/testing "success"
      (let [event {::sm/type :login
                   :email "profile1.test@nodomain.com"
                   :password "123123"
                   :scope "foobar"}
            out (th/try-on! (sm/handle event))]
        ;; (th/print-result! out)
        (t/is (nil? (:error out)))
        (t/is (= (:id profile) (get-in out [:result :id])))))
    ))

(t/deftest profile-query-and-manipulation
  (let [profile @(th/create-profile db/pool 1)]

    (t/testing "query profile"
      (let [data {::sq/type :profile
                  :profile-id (:id profile)}
            out (th/try-on! (sq/handle data))]

        ;; (th/print-result! out)
        (t/is (nil? (:error out)))

        (let [result (:result out)]
          (t/is (= "Profile 1" (:fullname result)))
          (t/is (= "profile1.test@nodomain.com" (:email result)))
          (t/is (not (contains? result :password))))))

    (t/testing "update profile"
      (let [data (assoc profile
                        ::sm/type :update-profile
                        :fullname "Full Name"
                        :name "profile222"
                        :lang "en")
            out (th/try-on! (sm/handle data))]

        ;; (th/print-result! out)
        (t/is (nil? (:error out)))

        (let [result (:result out)]
          (t/is (= (:fullname data) (:fullname result)))
          (t/is (= (:email data) (:email result)))
          (t/is (not (contains? result :password))))))

    (t/testing "update photo"
      (let [data {::sm/type :update-profile-photo
                  :profile-id (:id profile)
                  :file {:name "sample.jpg"
                         :path "tests/uxbox/tests/_files/sample.jpg"
                         :size 123123
                         :mtype "image/jpeg"}}
            out (th/try-on! (sm/handle data))]

        ;; (th/print-result! out)
        (t/is (nil? (:error out)))

        (let [result (:result out)]
          (t/is (= (:id profile) (:id result))))))
    ))

;; (t/deftest test-mutation-register-profile
;;   (let[data {:fullname "Full Name"
;;              :profilename "profile222"
;;              :email "profile222@uxbox.io"
;;              :password "profile222"
;;              ::sv/type :register-profile}
;;        [err rsp] (th/try-on (sm/handle data))]
;;     (println "RESPONSE:" err rsp)))

;; (t/deftest test-http-validate-recovery-token
;;   (with-open [conn (db/connection)]
;;     (let [profile (th/create-profile conn 1)]
;;       (with-server {:handler (uft/routes)}
;;         (let [token (#'usu/request-password-recovery conn "profile1")
;;               uri1 (str th/+base-url+ "/api/auth/recovery/not-existing")
;;               uri2 (str th/+base-url+ "/api/auth/recovery/" token)
;;               [status1 data1] (th/http-get profile uri1)
;;               [status2 data2] (th/http-get profile uri2)]
;;           ;; (println "RESPONSE:" status1 data1)
;;           ;; (println "RESPONSE:" status2 data2)
;;           (t/is (= 404 status1))
;;           (t/is (= 204 status2)))))))

;; (t/deftest test-http-request-password-recovery
;;   (with-open [conn (db/connection)]
;;     (let [profile (th/create-profile conn 1)
;;           sql "select * from profile_pswd_recovery"
;;           res (sc/fetch-one conn sql)]

;;       ;; Initially no tokens exists
;;       (t/is (nil? res))

;;       (with-server {:handler (uft/routes)}
;;         (let [uri (str th/+base-url+ "/api/auth/recovery")
;;               data {:profilename "profile1"}
;;               [status data] (th/http-post profile uri {:body data})]
;;           ;; (println "RESPONSE:" status data)
;;           (t/is (= 204 status)))

;;         (let [res (sc/fetch-one conn sql)]
;;           (t/is (not (nil? res)))
;;           (t/is (= (:profile-id res) (:id profile))))))))

;; (t/deftest test-http-validate-recovery-token
;;   (with-open [conn (db/connection)]
;;     (let [profile (th/create-profile conn 1)]
;;       (with-server {:handler (uft/routes)}
;;         (let [token (#'usu/request-password-recovery conn (:profilename profile))
;;               uri (str th/+base-url+ "/api/auth/recovery")
;;               data {:token token :password "mytestpassword"}
;;               [status data] (th/http-put profile uri {:body data})

;;               profile' (usu/find-full-profile-by-id conn (:id profile))]
;;           (t/is (= status 204))
;;           (t/is (hashers/check "mytestpassword" (:password profile'))))))))


