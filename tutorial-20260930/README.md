# Cranfield Lucene Indexer

This Maven project indexes the tagged records in `cran.all.1400`. Each `.I` record becomes one Lucene document. Its numeric ID is stored in the exact-match `id` field; tagged content such as `.T`, `.A`, `.B`, and `.W` is indexed both under its own field and in the combined `contents` field.

`cran.qry` contains search queries, and `cranqrel` contains relevance judgments for evaluation. Neither file is part of the document index.

## Build and run

From this directory:

```sh
mvn package
java -jar target/cranfield-indexer-1.0.0.jar cran.all.1400 index
```

The corpus and index paths are optional; the defaults are `cran.all.1400` and `index`. Running the indexer recreates the index each time. The output reports how many records were indexed; the supplied corpus should produce 1,400 documents.

## Search and evaluate

Run all queries in `cran.qry` against the index and write the top 50 results per query in trec_eval format:

```sh
java -cp target/cranfield-indexer-1.0.0.jar cranfield.CranfieldSearcher cran.qry index results.txt
```

The query parser uses the same `StandardAnalyzer` as the indexer. Search uses Lucene's default BM25 similarity and ORs the analyzed query terms; query syntax characters are escaped so the Cranfield text is treated as plain text. Cranfield `.I` query IDs are sparse, while `cranqrel` uses sequential query numbers, so the results use each query's 1-based position in `cran.qry` as its trec_eval query ID. Each output row contains `query-id Q0 document-id rank score run-id`.

```sh
awk 'NF == 3 { print $1, "Q0", $2, $3 }' cranqrel > cranqrel.trec
cd trec_eval-9.0.7
make
make quicktest
cd ..
./trec_eval-9.0.7/trec_eval -m P.5 cranqrel.trec results.txt
```

The conversion adds trec_eval's required iteration column and leaves the original relevance file unchanged. `P_5` is precision at 5, not MAP; use `-m map` to report mean average precision. The qrels cover all 225 query numbers. The query file, index directory, and results file arguments are optional; defaults are `cran.qry`, `index`, and `results.txt`. The results file is replaced each time.