;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; This Source Code Form is "Incompatible With Secondary Licenses", as
;; defined by the Mozilla Public License, v. 2.0.
;;
;; Copyright (c) 2016-2020 Andrey Antukh <niwi@niwi.nz>

(ns uxbox.tasks.gc
  (:require
   [clojure.tools.logging :as log]
   [clojure.spec.alpha :as s]
   [cuerdas.core :as str]
   [postal.core :as postal]
   [promesa.core :as p]
   [uxbox.common.exceptions :as ex]
   [uxbox.common.spec :as us]
   [uxbox.config :as cfg]
   [uxbox.db :as db]
   [uxbox.util.blob :as blob]))

;; TODO: add images-gc with proper resource removal
;; TODO: add icons-gc
;; TODO: add pages-gc
;; TODO: test this

;; --- Delete Projects

