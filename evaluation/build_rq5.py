#!/usr/bin/env python3
"""Build the RQ5 multi-turn continuation set (datasets/rq5-multiturn.jsonl).

Each conversation has 2 turns: a general-knowledge tutor question (turn 1) and a
continuation (turn 2) that is ONLY resolvable from the turn-1 answer via anaphora
or topic carryover ("rename that to...", "which did you say...", "you listed it
second", ...). No turn-2 is self-sufficient: without cross-turn memory the
referents are unbound, which is exactly what RQ5 measures across the three memory
configurations (fresh session / 20-message window / window + profile).

Properties enforced:
  - 60 conversations (120 rows), ids c0..c59
  - every turn-2 text distinct (uniqueness across the whole set)
  - no turn-2 repeated anywhere that would make e.g. "no memory" trivially cheat

Usage:
  python3 evaluation/build_rq5.py
"""
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUT = HERE / "datasets" / "rq5-multiturn.jsonl"

# (turn1, turn2)  -- turn1 builds content the model must remember; turn2 resolves only
# against that content. Topics span the six corpus subjects plus general-study topics.
CONVERSATIONS = [
    # Business / Finance (12)
    ("What are the main differences between a sole proprietorship and a corporation?",
     'Rename "sole proprietorship" to "single-owner" and restate its biggest risk.'),
    ("What are the typical stages of the business decision-making process, in order?",
     "Which stage did you list third, and why is it important?"),
    ("Explain the difference between fixed costs and variable costs with one example each.",
     "Give two more examples of a fixed cost, like the ones you just mentioned."),
    ("Name the four main functions of management in order.",
     "Go back to staffing, the third one, and tell me more about it."),
    ("What does SWOT stand for and what does each letter mean?",
     "Which of the four did you say looks at internal factors? Repeat them."),
    ("What does a cash flow statement contain?",
     "List those three sections again in the same order."),
    ("What is the difference between debt financing and equity financing?",
     'Rename the first one to "borrowed capital" and list its main drawback.'),
    ("Explain how present value relates to compound interest.",
     "Which formula did you give for present value? Write it again."),
    ("What are the main components of a corporate balance sheet?",
     "Which of the three components did you say is reported at a point in time?"),
    ("Define supply and demand and how they set a market price.",
     "Which curve did you say shifts when income rises? Restate that effect."),
    ("Compare renewable and non-renewable energy sources.",
     'Rename "non-renewable" to "depletable" and repeat its main drawback.'),
    ("List the main components of a personal budget.",
     "Which component did you say should be prioritized first?"),
    # Science / Chemistry / Engineering communication (9)
    ("What were the main organic reaction mechanisms, such as SN1, SN2, E1, and E2?",
     "Which mechanism did you describe second? Explain it again in one sentence."),
    ("Explain how NMR spectroscopy helps identify molecular structure.",
     "What signal did you say corresponds to hydrogen? Repeat what that shows."),
    ("How do you perform a titration correctly?",
     "Where exactly did you say to add the indicator?"),
    ("What is the difference between addition and condensation polymerization?",
     'Rename the second one to "step-growth" and give a fresh example.'),
    ("How do catalysts lower activation energy?",
     "Tell me more about that energy diagram you mentioned."),
    ("List the main functional groups that appear in organic chemistry.",
     "You mentioned the hydroxyl group — what does it do?"),
    ("Name Newton's three laws of motion briefly.",
     "Give another real-world example of the law about equal and opposite forces."),
    ("What are the main phases of the water cycle?",
     "Which phase did you say comes before condensation?"),
    ("What are the main strategies for climate change adaptation?",
     "Go back to the strategy about coastal protection and give more detail."),
    # Engineering composition (3)
    ("What are the key elements of a strong thesis statement?",
     "Rephrase the second element in simpler words."),
    ("What structure does a well-formed problem statement usually follow?",
     "What word did you say should appear in the first sentence?"),
    ("Explain the typical peer-review steps for an engineering report.",
     "Which step came right before the one you called 'final revision'?"),
    # Computer science + programming (10)
    ("What are the main categories of variables in programming?",
     'Rename the second category with the term "mutable" — does that still fit its definition?'),
    ("Explain how binary search works step by step.",
     "What was the very first step you stated?"),
    ("Compare stacks and queues with one example each.",
     "Swap those examples so the queue now uses the stack's example."),
    ("What is the difference between compiled and interpreted languages?",
     "Which one did you say executes line by line?"),
    ("List the five major components of a computer.",
     "Which component did you list after memory?"),
    ("What is the difference between a list and a tuple in Python?",
     "Which of the two is immutable again?"),
    ("How do a for loop and a while loop differ?",
     "Change the while-loop example so it prints a countdown — which example is that?"),
    ("What does a Python function's return statement do?",
     "What happens when a function has no return — restate what you said."),
    ("Summarize how a Python dictionary stores data.",
     'Rename "key" to "label" in your explanation and repeat it.'),
    ("What are the primitive data types in Python?",
     "Which type did you list last?"),
    # Mathematics (6)
    ("What are the steps to solve a quadratic equation by factoring?",
     "What did you say to do immediately after factoring?"),
    ("Explain the difference between mean, median, and mode.",
     "Which of the three is most affected by outliers? Repeat why."),
    ("What is the chain rule and how is it applied?",
     "Which part did you say to differentiate first, the outer or the inner?"),
    ("State the Pythagorean theorem and its main uses.",
     'Rename "hypotenuse" to "longest side" and restate the relation.'),
    ("What are the distance and midpoint formulas in coordinate geometry?",
     "Write the midpoint formula again, the one you gave second."),
    ("List the basic exponent properties.",
     "Which exponent property applies when you multiply terms with the same base?"),
    # Data science (6)
    ("What are the main steps of the data science project lifecycle, in order?",
     "Which step did you place between cleaning and modeling?"),
    ("Explain the difference between supervised and unsupervised learning.",
     'Rename the second one to "self-labeled" — does that fit its definition?'),
    ("What is cross-validation and why is it used?",
     "How many folds did you say is typical? Repeat that."),
    ("What are precision and recall and how do they differ?",
     "Which one did you say rewards avoiding false positives?"),
    ("Summarize what a confusion matrix contains.",
     "Which quadrant did you call true positives?"),
    ("What does the bias-variance tradeoff describe?",
     "Which error source did you say is high variance? Restate it."),
    # Emacs (6)
    ("What are the main ways to navigate buffers in Emacs?",
     "Which navigation command did you list second?"),
    ("Explain the difference between C-x C-f and C-x C-s.",
     "Which of the two opens a file? Repeat its binding."),
    ("What basic editing modes does Emacs support?",
     'Rename "insert mode" to "editing mode" and describe it.'),
    ("How do you search and replace text in Emacs?",
     "What key sequence starts a search — the first one you listed?"),
    ("What does the minibuffer do in Emacs?",
     "What did you say appears in it after M-x?"),
    ("List the common Emacs keybindings for saving and quitting.",
     "Which of them quits Emacs entirely?"),
    # Cross-topic general study (6)
    ("What is opportunity cost in economics?",
     "Tell me more about that 'next best alternative' you mentioned."),
    ("Describe the main steps of the scientific method in order.",
     "What was the step right after forming a hypothesis?"),
    ("What are the main causes of market failure?",
     "Restate the cause involving information, which you listed second."),
    ("Explain the four pillars of data quality.",
     'Rename the third pillar to "reliability" and redefine it.'),
    ("What is the difference between knowledge and skill in learning?",
     'You mentioned "declarative" for one of them — which was it, and what does that mean?'),
    ("Describe the typical phases of a software project lifecycle.",
     'Rewrite the phase that follows Initiation under the name "Planning" and continue from there.'),
    ("What are the main differences between TCP and UDP?",
     "Which one did you say is connection-oriented? Repeat its reliability guarantee."),
    ("What does a business's break-even point represent?",
     "Which formula did you give for it? Write it again."),
]


def main() -> int:
    if len(CONVERSATIONS) != 60:
        raise SystemExit(f"expected 60 conversations, got {len(CONVERSATIONS)}")
    rows = []
    for i, (t1, t2) in enumerate(CONVERSATIONS):
        rows.append({"conv_id": f"c{i}", "turn": 1, "user": t1})
        rows.append({"conv_id": f"c{i}", "turn": 2, "user": t2})

    turns2 = [r["user"] for r in rows if r["turn"] == 2]
    if len(set(turns2)) != len(turns2):
        raise SystemExit("duplicate turn-2 texts present — continuations must be distinct")
    if any(t1 == t2 for t1, t2 in CONVERSATIONS):
        raise SystemExit("turn-1 and turn-2 must differ")

    OUT.write_text("\n".join(json.dumps(r) for r in rows) + "\n")
    print(f"wrote {len(rows)} rows / {len(CONVERSATIONS)} conversations -> {OUT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())