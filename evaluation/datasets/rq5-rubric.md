# RQ5 continuation-scoring rubric (fixed before annotation)

Scope: judge each turn-2 response in the 60-conversation multi-turn set
(`rq5-multiturn.jsonl`) under the three memory configurations:
(a) no external memory (fresh session per turn), (b) 20-message Redis sliding
window, (c) window + profile attributes.

## Procedure

1. Each annotator independently sees, per conversation: turn 1 (the learner
   utterance) and the system's turn-1 answer, then turn 2 (the continuation) and
   the system's turn-2 answer. Annotation order is randomised and
   config-blind (a/b/c labels hidden until all judging is done).
2. Mark every (conversation, config) pair `1` (success) or `0` (failure).
3. Inter-annotator agreement is reported as Cohen's kappa.
4. No conferring before both annotators have judged all items.

## Success criteria (all must hold)

- **C1 Referent resolution.** The turn-2 answer identifies the correct referent
  of the anaphor/topic-carryover (e.g. resolves "the second one", "that
  alternative", "the third pillar" to the entity actually produced in the turn-1
  answer).
- **C2 Requested action.** The answer performs the continuation's request
  (rename, re-list, rephrase, expand, re-order, repeat-with-rationale) using the
  turn-1 content, or an equally valid restatement that preserves the turn-1
  facts.
- **C3 Grounding.** No factual assertion in the turn-2 answer contradicts the
  turn-1 answer; no entity is invented that the turn-1 answer did not contain.

## Failure criteria (any single one suffices)

- **F1** The referent is unresolvable or the system guesses/locates it in the
  continuation text itself rather than in the turn-1 answer.
- **F2** The response is generic — materially identical to what the system
  would have produced with no previous turn (for example a restatement of the
  topic with no evidence of having read the turn-1 answer).
- **F3** The response contradicts a stated fact from the turn-1 answer.
- **F4** The response refuses or fails to perform the requested action.

## Notes

- A response that is fluent but does not reference the turn-1 answer is a failure
  (F2). Under configuration (a) this is the expected dominant outcome.
- Ordering references ("second", "third") are judged against the turn-1 answer
  as actually produced, not against a canonical ordering.
- kappa is computed over all 60*3 judged pairs per annotator pair set; report it
  per memory configuration and overall.

This rubric is versioned. Any revision requires a note here and re-judging only
of affected items; a rubric change may not be applied retroactively to scores
already locked.