# FACTS — single source of truth for AI-LMS

_Generated: 2026-10-06T06:52:09.814628+00:00_

## Models
- classifier/agents: `llama3.2:3b`, T=0
- quiz: `llama3.2:1b`, T=0, format=json
- embedding: `nomic-embed-text`, dim=768

## RQ1 (committed)
### primary_1200 (rq1-metrics.json)
```json
{
  "n": 1200,
  "per_label": {
    "CONVERSATION": [
      220,
      0.9434782608695652,
      0.9863636363636363,
      0.9644444444444443
    ],
    "VIDEO_SEARCH": [
      250,
      0.8710801393728222,
      1.0,
      0.931098696461825
    ],
    "CONTENT_ANALYSIS": [
      260,
      1.0,
      0.9192307692307692,
      0.9579158316633267
    ],
    "ASSESSMENT": [
      270,
      1.0,
      0.9074074074074074,
      0.9514563106796117
    ],
    "INSIGHT": [
      200,
      0.9698492462311558,
      0.965,
      0.9674185463659147
    ]
  },
  "accuracy": 0.9533333333333334,
  "macro_precision": 0.9568815292947086,
  "macro_recall": 0.9556003626003626,
  "macro_f1": 0.9544667659230246,
  "short_circuit_count": 267,
  "short_circuit_greeting": 17,
  "short_circuit_video_link": 250,
  "short_circuit_correct": 267,
  "router_served_frac": 22.25,
  "median_all_latency_ms": 3349.0,
  "median_classifier_latency_ms": 4159,
  "median_short_latency_ms": 2033,
  "median_latency_saved_ms": 2126,
  "p95_all_latency_ms": 8005,
  "p99_all_latency_ms": 9346,
  "confusion": {
    "CONVERSATION": {
      "CONVERSATION": 217,
      "VIDEO_SEARCH": 2,
      "CONTENT_ANALYSIS": 0,
      "ASSESSMENT": 0,
      "INSIGHT": 1
    },
    "VIDEO_SEARCH": {
      "CONVERSATION": 0,
      "VIDEO_SEARCH": 250,
      "CONTENT_ANALYSIS": 0,
      "ASSESSMENT": 0,
      "INSIGHT": 0
    },
    "CONTENT_ANALYSIS": {
      "CONVERSATION": 7,
      "VIDEO_SEARCH": 13,
      "CONTENT_ANALYSIS": 239,
      "ASSESSMENT": 0,
      "INSIGHT": 1
    },
    "ASSESSMENT": {
      "CONVERSATION": 0,
      "VIDEO_SEARCH": 21,
      "CONTENT_ANALYSIS": 0,
      "ASSESSMENT": 245,
      "INSIGHT": 4
    },
    "INSIGHT": {
      "CONVERSATION": 6,
      "VIDEO_SEARCH": 1,
      "CONTENT_ANALYSIS": 0,
      "ASSESSMENT": 0,
      "INSIGHT": 193
    }
  }
}
```

### monolithic_1200 (rq1-monolithic-metrics.json)
```json
{
  "n": 1200,
  "per_label": {
    "CONVERSATION": [
      220,
      0.9575471698113207,
      0.9227272727272727,
      0.9398148148148149
    ],
    "VIDEO_SEARCH": [
      250,
      0.8680555555555556,
      1.0,
      0.929368029739777
    ],
    "CONTENT_ANALYSIS": [
      260,
      0.9375,
      0.9230769230769231,
      0.9302325581395349
    ],
    "ASSESSMENT": [
      270,
      0.9959016393442623,
      0.9,
      0.9455252918287939
    ],
    "INSIGHT": [
      200,
      0.965,
      0.965,
      0.965
    ]
  },
  "accuracy": 0.9408333333333333,
  "macro_precision": 0.9448008729422277,
  "macro_recall": 0.9421608391608393,
  "macro_f1": 0.9419881389045841,
  "confusion": {
    "CONVERSATION": {
      "CONVERSATION": 203,
      "VIDEO_SEARCH": 2,
      "CONTENT_ANALYSIS": 13,
      "ASSESSMENT": 1,
      "INSIGHT": 1
    },
    "VIDEO_SEARCH": {
      "CONVERSATION": 0,
      "VIDEO_SEARCH": 250,
      "CONTENT_ANALYSIS": 0,
      "ASSESSMENT": 0,
      "INSIGHT": 0
    },
    "CONTENT_ANALYSIS": {
      "CONVERSATION": 6,
      "VIDEO_SEARCH": 13,
      "CONTENT_ANALYSIS": 240,
      "ASSESSMENT": 0,
      "INSIGHT": 1
    },
    "ASSESSMENT": {
      "CONVERSATION": 0,
      "VIDEO_SEARCH": 22,
      "CONTENT_ANALYSIS": 0,
      "ASSESSMENT": 243,
      "INSIGHT": 5
    },
    "INSIGHT": {
      "CONVERSATION": 3,
      "VIDEO_SEARCH": 1,
      "CONTENT_ANALYSIS": 3,
      "ASSESSMENT": 0,
      "INSIGHT": 193
    }
  },
  "median_all_latency_ms": 3356.0,
  "p95_all_latency_ms": 8012,
  "p99_all_latency_ms": 9439,
  "router_servable_subset": {
    "count": 267,
    "correct": 267,
    "accuracy": 1.0,
    "median_latency_ms": 2333
  }
}
```

### heldout_218 (probe-218-metrics.json)
```json
{
  "n": 218,
  "per_label": {
    "CONVERSATION": [
      50,
      0.9782608695652174,
      0.9,
      0.9375
    ],
    "VIDEO_SEARCH": [
      48,
      0.8727272727272727,
      1.0,
      0.9320388349514563
    ],
    "CONTENT_ANALYSIS": [
      40,
      0.9069767441860465,
      0.975,
      0.9397590361445783
    ],
    "ASSESSMENT": [
      40,
      1.0,
      0.75,
      0.8571428571428571
    ],
    "INSIGHT": [
      40,
      0.9090909090909091,
      1.0,
      0.9523809523809523
    ]
  },
  "accuracy": 0.926605504587156,
  "macro_precision": 0.933411159113889,
  "macro_recall": 0.925,
  "macro_f1": 0.9237643361239689,
  "short_circuit_count": 52,
  "short_circuit_greeting": 9,
  "short_circuit_video_link": 8,
  "short_circuit_video_request": 35,
  "short_circuit_correct": 52,
  "false_short_circuits": 0,
  "router_served_frac": 23.853211009174313,
  "classifier_observed_count": 166,
  "classifier_observed_correct": 150,
  "median_all_latency_ms": 3204.5,
  "median_classifier_latency_ms": 3814.0,
  "median_short_latency_ms": 1920.0,
  "median_latency_saved_ms": 1894.0,
  "p95_all_latency_ms": 6963,
  "p99_all_latency_ms": 8870,
  "confusion": {
    "CONVERSATION": {
      "CONVERSATION": 45,
      "VIDEO_SEARCH": 3,
      "CONTENT_ANALYSIS": 0,
      "ASSESSMENT": 0,
      "INSIGHT": 2
    },
    "VIDEO_SEARCH": {
      "CONVERSATION": 0,
      "VIDEO_SEARCH": 48,
      "CONTENT_ANALYSIS": 0,
      "ASSESSMENT": 0,
      "INSIGHT": 0
    },
    "CONTENT_ANALYSIS": {
      "CONVERSATION": 0,
      "VIDEO_SEARCH": 0,
      "CONTENT_ANALYSIS": 39,
      "ASSESSMENT": 0,
      "INSIGHT": 1
    },
    "ASSESSMENT": {
      "CONVERSATION": 1,
      "VIDEO_SEARCH": 4,
      "CONTENT_ANALYSIS": 4,
      "ASSESSMENT": 30,
      "INSIGHT": 1
    },
    "INSIGHT": {
      "CONVERSATION": 0,
      "VIDEO_SEARCH": 0,
      "CONTENT_ANALYSIS": 0,
      "ASSESSMENT": 0,
      "INSIGHT": 40
    }
  }
}
```

## System
- chunks: 800/100; top-k 8/3
- chat memory: 20 messages, TTL 24h
- vectors: qdrant, pgvector
- tests: orchestrator=179 (7 skipped), gateway=66 (11 skipped), failures=0

## Caveats
- trait storage = append-only free text in behavioralTraits; no fixed schema
- YouTubeLinkValidator fails open on network errors; no upload file-size cap in main flow
- no RBAC enforcement beyond gateway @RolesAllowed; orchestrator trusts X-User-Id
