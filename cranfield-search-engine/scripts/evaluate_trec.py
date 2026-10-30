#!/usr/bin/env python3
"""
Evaluation script for the Lucene Information Retrieval Search Engine using the Cranfield Collection.
"""

import os
import sys
import glob
import subprocess
from collections import defaultdict


METRIC_LABELS = {
    "map": "MAP",
    "recall_1000": "Recall@1000",
    "P_5": "P@5",
    "P_10": "P@10",
    "recip_rank": "MRR",
}


def load_qrels(qrels_file):
    """
    Loads TREC-formatted or Cranfield qrels.
    Format: qid [iter] doc_id rel
    Returns dict: qid -> set of relevant doc_ids
    """
    qrels = defaultdict(set)
    with open(qrels_file, "r") as f:
        for line in f:
            parts = line.strip().split()
            if len(parts) >= 4:
                qid = str(int(parts[0]))
                doc_id = str(int(parts[2]))
                rel = int(parts[3])
                if rel > 0:
                    qrels[qid].add(doc_id)
            elif len(parts) == 3:
                qid = str(int(parts[0]))
                doc_id = str(int(parts[1]))
                rel = int(parts[2])
                if rel > 0:
                    qrels[qid].add(doc_id)
    return qrels


def load_run(run_file):
    """
    Loads standard TREC run file.
    Format: qid Q0 doc_id rank score tag
    Returns dict: qid -> list of retrieved doc_ids in rank order
    """
    runs = defaultdict(list)
    with open(run_file, "r") as f:
        for line in f:
            parts = line.strip().split()
            if len(parts) >= 6:
                qid = str(int(parts[0]))
                doc_id = str(int(parts[2]))
                runs[qid].append(doc_id)
    return runs


def try_official_trec_eval(trec_eval_bin, qrels_file, run_file):
    """
    Runs official NIST trec_eval binary.
    """
    if not os.path.exists(trec_eval_bin):
        print(f"Official trec_eval binary not found at: {trec_eval_bin}", file=sys.stderr)
        print("Run scripts/setup_trec_eval.sh to compile it.", file=sys.stderr)
        return None

    if not os.access(trec_eval_bin, os.X_OK):
        print(f"Official trec_eval binary is not executable: {trec_eval_bin}", file=sys.stderr)
        return None

    cmd = [
        trec_eval_bin,
        "-m", "map",
        "-m", "recall.1000",
        "-m", "P.5,10",
        "-m", "recip_rank",
        qrels_file,
        run_file,
    ]
    try:
        proc = subprocess.run(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            check=True,
        )
    except subprocess.CalledProcessError as error:
        details = error.stderr.strip() or "no error output"
        print(f"Official trec_eval failed: {details}", file=sys.stderr)
        return None
    except OSError as error:
        print(f"Could not execute official trec_eval: {error}", file=sys.stderr)
        return None

    return proc.stdout


def parse_official_output(output):
    """Converts official trec_eval output into the metrics used by the table."""
    metrics = {}
    for line in output.splitlines():
        parts = line.split()
        if len(parts) == 3 and parts[1] == "all" and parts[0] in METRIC_LABELS:
            metrics[parts[0]] = float(parts[2])

    missing = set(METRIC_LABELS) - set(metrics)
    if missing:
        missing_labels = ", ".join(sorted(missing))
        raise ValueError(f"trec_eval output is missing metrics: {missing_labels}")
    return metrics


def print_results_table(results):
    """Prints a tabular comparison of official trec_eval results."""
    headers = ["Run", *METRIC_LABELS.values()]
    widths = [
        max(len(headers[0]), *(len(result["name"]) for result in results)),
        *[8] * (len(headers) - 1),
    ]

    def format_row(values):
        return " | ".join(
            str(value).ljust(width)
            for value, width in zip(values, widths)
        )

    separator = "-+-".join("-" * width for width in widths)
    print(format_row(headers))
    print(separator)
    for result in results:
        values = [result["name"]]
        values.extend(f"{result[metric]:.4f}" for metric in METRIC_LABELS)
        print(format_row(values))


def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.abspath(os.path.join(script_dir, ".."))

    # Default paths
    qrels_path = os.path.join(project_root, "data", "QRelsCorrectedforTRECeval")
    if not os.path.exists(qrels_path):
        qrels_path = os.path.join(project_root, "data", "cran", "cranqrel")

    results_dir = os.path.join(project_root, "results")
    trec_eval_bin = os.path.join(project_root, "trec_eval", "trec_eval")

    if len(sys.argv) > 1 and sys.argv[1] not in ["--all", "-a"]:
        qrels_path = sys.argv[1]
        run_file = sys.argv[2] if len(sys.argv) > 2 else None

        if run_file:
            print(f"Evaluating: {run_file} against {qrels_path}")
            # Try official trec_eval first
            official_out = try_official_trec_eval(trec_eval_bin, qrels_path, run_file)

            print("\n--- Official NIST trec_eval Output ---")
            print(official_out if official_out is not None else "Evaluation failed.")
            return

    run_files = sorted(glob.glob(os.path.join(results_dir, "*.txt")))
    if not run_files:
        print(f"No run files found matching {results_dir}/*.txt")
        print("Run the search engine first with: mvn exec:java -Dexec.args=\"--batch\"")
        return

    if not os.path.exists(qrels_path):
        print(f"Error: Qrels file not found at: {qrels_path}")
        return

    print("=========================================================================================")
    print("                        CRANFIELD RETRIEVAL BENCHMARK EVALUATION                        ")
    print(f" Qrels: {os.path.basename(qrels_path)} | Evaluating {len(run_files) } experimental runs")
    print("=========================================================================================\n")

    results = []

    for rf in run_files:
        run_name = os.path.splitext(os.path.basename(rf))[0]
        official_out = try_official_trec_eval(trec_eval_bin, qrels_path, rf)
        if official_out is None:
            continue
        metrics = parse_official_output(official_out)
        metrics["name"] = run_name
        results.append(metrics)

    if results:
        print_results_table(results)


if __name__ == "__main__":
    main()
