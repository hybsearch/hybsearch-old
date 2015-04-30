(ns hybsearch.rpc
  (:require-macros
    [tailrecursion.javelin :refer [defc defc= set-cell!=]])
  (:require
   [tailrecursion.javelin :refer [cell-map]]
   [tailrecursion.castra :refer [mkremote]]
   [datascript :as d]))

(enable-console-print!)

(def jobs-db-schema {

             :clustal-scheme/name                  {:db/cardinality :db.cardinality/one}
             :clustal-scheme/ex-setting            {:db/cardinality :db.cardinality/one}
             :clustal-scheme/num-triples           {:db/cardinality :db.cardinality/one} ;; Always the same, equal to the number of triples that can be created for all loci
             :clustal-scheme/num-processed-triples {:db/cardinality :db.cardinality/one} ;; Depends on how well processed the global set is for this clustal scheme

             :analysis-set/name                    {:db/cardinality :db.cardinality/one}
             :analysis-set/set-def                 {:db/cardinality :db.cardinality/one :db/valueType :db.type/ref}
             :analysis-set/num-triples             {:db/cardinality :db.cardinality/one}
             :analysis-set/num-processed-triples   {:db/cardinality :db.cardinality/one}


             :job/name                             {:db/cardinality :db.cardinality/one}
             :job/set-def                          {:db/cardinality :db.cardinality/one :db/valueType :db.type/ref}
             :job/num-triples                      {:db/cardinality :db.cardinality/one}
             :job/num-processed-triples            {:db/cardinality :db.cardinality/one}
             :job/clustal-scheme                   {:db/cardinality :db.cardinality/one :db/valueType :db.type/ref}


             ;; Todo: how to match up relational ref ids when downloading data from server?

             ;; all triples for the set = triples(loci for each binomial UNION loci list) INTERSECT analysis-set triples
             :set-def/binomials                    {:db/cardinality :db.cardinality/many} ;; Currently a list of binomial species names
             :set-def/loci                         {:db/cardinality :db.cardinality/many} ;; Currently a list of accession numbers
             ;; Filter is ptional. Filter further restricts the set definition.
             ;; Think of the filter as another set-def you must itersect the other parts
             ;; of this definition with to fully resolve this set definition.
             :set-def/filter                       {:db/cardinality :db.cardinality/one :db/valueType :db.type/ref}


             })


(def loci-db-schema {
             :locus/accession-num                  {:db/cardinality :db.cardinality/one :db/unique :db.unique/identity}
             :locus/binomial                       {:db/cardinality :db.cardinality/one}

             ;; Todo: Eventually allow more locus information on client.
             ;; There is also more species information than the binomial available in the GenBank files, i.e. the ncbi_taxid
             ;; Will probably also need to enforce uniqueness on the clustal-schemes
            })


;; We do two client-side databases so locus data (of which there will be a lot) doesn't have to be
;; pushed repeatedly in its entirety. (We can request entities as needed for loci, because we'll
;; know from the job data or a dynamic form which ones we'll need). Job data is entirely an unknown,
;; but is small, so we can just poll for that.

(defonce loci-db (d/create-conn loci-db-schema))

(defc jobs-state {})
(defc jobs-error nil)
(defc jobs-loading [])

(defc loci-state {})
(defc loci-error nil)
(defc loci-loading [])

(defc= jobs-entities (:entities jobs-state))
(defc= jobs-db (:db-after (d/with (d/empty-db jobs-db-schema) jobs-entities)))

;; Todo: mkremote methods for getting specific locus state

(def get-jobs-state (mkremote 'hybsearch.api/get-jobs-state jobs-state jobs-error jobs-loading))


;; Todo: wish there was a better way to query than just by name (i.e. what if no name?)

(defc= clustal-scheme-ids (d/q '[:find ?e :where [?e :clustal-scheme/name ?name]] jobs-db))
(defc= analysis-set-ids (d/q '[:find ?e :where [?e :analysis-set/name ?name]] jobs-db))
(defc= clustal-schemes (map (fn [e] (d/entity jobs-db (first e))) clustal-scheme-ids))
(defc= analysis-sets   (map (fn [e] (d/entity jobs-db (first e))) analysis-set-ids))

(defc  selected-clustal-scheme-id nil)
(defc  selected-analysis-set-id nil)
(defc= selected-clustal-scheme (if selected-clustal-scheme-id (d/entity jobs-db selected-clustal-scheme-id))) ;; Guard here, because if there are no schemes the selected id will be nil and d/entity will throw an exception on lazy eval.
(defc= selected-analysis-set   (if selected-analysis-set-id   (d/entity jobs-db selected-analysis-set-id))) ;; Guard here, because if there are no sets the selected id will be nil and d/entity will throw an exception on lazy eval.

;; Scheme-set Jobs
(defc= scheme-set-jobs (if (and selected-clustal-scheme selected-analysis-set)
                         (map (fn [e] (d/entity jobs-db (first e)))
                              (d/q '[:find ?e
                                     :in $ ?scheme [?set-def ...] ;; [?set-def ...] is all of the set definitions that use the analysis-set for their filter
                                     :where [?e :job/set-def ?set-def] ;; Datomic docs say put most restricting clauses first for optimal performance, not sure if this applies to DataScript but doing it anyway
                                     [?e :job/clustal-scheme ?scheme]] ;; Todo: Should check job/set-def/filter -> analysis-set/set-def
                                   jobs-db
                                   (get selected-clustal-scheme :db/id)
                                   ;; All set-def entities that use the selected analysis-set as a filter
                                   (map (fn [e] (first e))
                                        (d/q '[:find ?e
                                               :in $ ?set-def
                                               :where [?e :set-def/filter ?set-def]]
                                             jobs-db
                                             (-> (get selected-analysis-set :analysis-set/set-def) (get :db/id))))))))


(defn jobs-state-poll [interval]
  (get-jobs-state)
  (js/setTimeout #(jobs-state-poll interval) interval))

;; Init
(defn init [] (jobs-state-poll 3000))























