#!/usr/bin/env python3
"""Unit tests for shared eval metrics (stdlib unittest).

Run:  python3 evaluation/test_metrics.py -v
"""
from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import metrics


class RankedDocIdsTest(unittest.TestCase):
    def test_dedups_keeps_first_rank_order(self):
        ranked = [
            {"doc_id": None, "source": "doc:evl-business"},
            {"source": "doc:evl-business"},          # duplicate chunk, same doc
            {"source": "doc:evl-fin"},
            {"source": "doc:evl-fin"},               # duplicate again
            {"source": "doc:evl-business"},          # third duplicate
        ]
        self.assertEqual(metrics.ranked_doc_ids(ranked), ["evl-business", "evl-fin"])

    def test_doc_id_wins_over_source(self):
        ranked = [{"doc_id": "evl-business", "source": "doc:evl-fin"}]
        self.assertEqual(metrics.ranked_doc_ids(ranked), ["evl-business"])

    def test_empty_and_missing_ids_dropped(self):
        ranked = [{}, {"doc_id": None, "source": None}, {"doc_id": "", "source": "doc:"},
                  {"source": ""}]
        self.assertEqual(metrics.ranked_doc_ids(ranked), [])

    def test_plain_source_kept_as_id(self):
        ranked = [{"source": "evl-business"}]
        self.assertEqual(metrics.ranked_doc_ids(ranked), ["evl-business"])


class ScoreSemanticsTest(unittest.TestCase):
    def test_duplicated_chunks_count_doc_once(self):
        ranked = [{"source": f"doc:biz"} for _ in range(8)]
        docs = metrics.ranked_doc_ids(ranked)
        self.assertEqual(docs, ["biz"])
        self.assertLessEqual(metrics.recall_at_k(docs[:8], {"biz"}), 1.0)
        self.assertEqual(metrics.recall_at_k(docs[:8], {"biz"}), 1.0)
        # precision divides by the number of distinct docs returned (capped at k)
        self.assertEqual(metrics.precision_at_k(docs[:8], {"biz"}, 8), 1.0)

    def test_two_relevant_docs_full_scores(self):
        ranked = [{"source": "doc:biz"}] * 5 + [{"source": "doc:eng"}] * 3
        docs = metrics.ranked_doc_ids(ranked)
        self.assertEqual(metrics.precision_at_k(docs, {"biz", "eng"}, 8), 1.0)
        self.assertEqual(metrics.recall_at_k(docs, {"biz", "eng"}, 8), 1.0)
        self.assertEqual(metrics.ndcg_at_k(docs, {"biz", "eng"}, 8), 1.0)
        self.assertEqual(metrics.reciprocal_rank(docs, {"biz", "eng"}), 1.0)

    def test_partial_relevance(self):
        ranked = [{"source": "doc:biz"}] * 5 + [{"source": "doc:eng"}] * 3
        docs = metrics.ranked_doc_ids(ranked)
        self.assertEqual(metrics.precision_at_k(docs, {"eng"}, 8), 0.5)
        self.assertEqual(metrics.recall_at_k(docs, {"eng"}, 8), 1.0)
        self.assertEqual(metrics.reciprocal_rank(docs, {"eng"}), 0.5)
        self.assertAlmostEqual(metrics.ndcg_at_k(docs, {"eng"}, 8), math_log2_3())

    def test_no_relevant(self):
        ranked = [{"source": "doc:biz"}] * 5
        docs = metrics.ranked_doc_ids(ranked)
        self.assertEqual(metrics.precision_at_k(docs, {"nope"}, 8), 0.0)
        self.assertEqual(metrics.recall_at_k(docs, {"nope"}, 8), 0.0)
        self.assertEqual(metrics.reciprocal_rank(docs, {"nope"}), 0.0)


def math_log2_3() -> float:
    import math
    dcg = 1 / math.log2(3)               # eng at rank 2
    idcg = 1 / math.log2(2)              # one relevant doc at rank 1
    return dcg / idcg


class AggregateTest(unittest.TestCase):
    def test_valid_proportions_aggregate(self):
        out = metrics.aggregate_over_queries([{"P@k": 0.5}, {"P@k": 1.0}, {"P@k": 0.0}])
        self.assertAlmostEqual(out["P@k"]["mean"], 0.5)
        self.assertEqual(out["P@k"]["n"], 3)

    def test_out_of_range_raises(self):
        with self.assertRaises(ValueError) as ctx:
            metrics.aggregate_over_queries([{"R@k": 4.0}, {"R@k": 8.0}])
        self.assertIn("R@k", str(ctx.exception))
        self.assertIn("outside [0,1]", str(ctx.exception))

    def test_list_agreement_deduped(self):
        a = ["biz", "eng"]
        b = ["biz", "math"]
        self.assertEqual(metrics.list_agreement(a, b, 2), 0.5)


if __name__ == "__main__":
    unittest.main()