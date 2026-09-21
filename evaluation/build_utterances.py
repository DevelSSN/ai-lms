#!/usr/bin/env python3
"""Build a varied, duplicate-free RQ1 utterance corpus (1000+ rows).

Writes `utterances.csv` (utterance,intent) with a balanced, shuffled mix of the
five intents. Deterministic (fixed seed) so the corpus is reproducible.

Usage: python3 evaluation/build_utterances.py
"""
import csv
import random
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUT = HERE / "utterances.csv"

TOPICS = [
    "Linear Algebra", "Calculus", "Quantum Physics", "Microeconomics",
    "Database Systems", "Discrete Math", "Cell Biology", "Machine Learning",
    "Software Engineering", "Thermodynamics", "Psychology", "World History",
    "Ethics", "Operating Systems", "Organic Chemistry", "Classical Mechanics",
    "Data Structures", "Computer Networks", "Sociology", "Algorithms",
    "Statistics", "Genetics", "Macroeconomics", "Cyber Security",
    "Artificial Intelligence", "Game Theory", "Fluid Dynamics", "Astrophysics",
    "Molecular Biology", "Human Anatomy", "Compiler Design", "Network Security",
    "Big Data", "Cognitive Science", "Linear Programming", "Computer Architecture",
]

DOCS = [
    "text", "file", "document", "slide", "PDF", "paper", "chapter",
    "article", "material", "notes", "handout", "section", "report",
]


def conversation():
    lines = [
        "Hi", "Hello", "Hey", "Hi there", "Hello there", "Hi AI",
        "Hey buddy", "Yo", "Sup", "Hello tutor", "Hiya", "Good morning",
        "Good afternoon", "Good evening", "Hey there", "Namaste", "Hola",
        "What's up", "What's good", "Howdy", "Morning", "Evening",
        "Who are you?", "What are you?", "Are you an AI?", "Are you a bot?",
        "Are you a human?", "What can you do?", "What do you do?",
        "How do you work?", "How does this system work?", "How were you built?",
        "Are you real?", "Can you help me with anything?", "What tools do you have?",
        "Do you have limits?", "Can you think?", "What's your name?",
        "What is your name?", "Do you have a name?", "Can I call you something?",
        "Who made you?", "What language do you speak?", "Are you free?",
        "Can we just chat?", "Can we talk?", "I want to chat", "I want to talk",
        "Let's talk", "Let's have a conversation", "Can you talk to me?",
        "Wanna chat?", "Can we have small talk?", "Talk to me", "Chat with me",
        "I'm bored", "I feel bored", "I'm tired", "I'm happy today",
        "I had a long day", "I'm excited", "I'm frustrated",
        "I'm nervous about my exams", "I'm stressed", "I'm stuck",
        "I'm feeling stuck", "I'm confused about things",
        "Can you help me learn?", "I want to learn something new",
        "Teach me something", "Tell me a fact", "Tell me something interesting",
        "Tell me a joke", "Tell me a story", "Do you know any trivia?",
        "What can you teach me?", "Why should I study?", "How do I learn better?",
        "Any study tips?", "What's your favorite subject?", "Recommend a book",
        "I'm new here", "Nice to meet you", "Good to meet you",
        "I just joined", "I'm a new user", "Great to be here",
        "How long have you been here?", "Help me get started",
        "Where do I begin?", "I don't know what to do", "What should I do first?",
        "Let me sign up", "What is the capital of France?",
        "What is the capital of Japan?", "How big is the sun?",
        "Who wrote Romeo and Juliet?", "What is the speed of light?",
        "Why is the sky blue?", "What time is it?", "What's the weather like?",
        "How many continents are there?", "What is water made of?",
        "Can fish fly?", "How old is the Earth?", "What year is it?",
        "Is it raining?", "What day is it today?", "How are you?",
        "How have you been?", "How's it going?", "How are you doing?",
        "Are you okay?", "What have you been up to?", "How was your day?",
        "Are you ready?", "Are you there?", "Is anyone there?",
        "Hello, anyone here?", "Can you hear me?", "Are you listening?",
        "Just checking in", "Goodbye", "Bye", "See you later", "Talk later",
        "See you soon", "Good night", "Take care", "Thanks", "Thank you",
        "Thank you very much", "Appreciate it", "Thanks for your help",
        "Great", "Awesome", "Cool", "Perfect", "OK", "Okay", "Fine",
        "Alright", "Hmm", "Interesting", "Nice", "Wow", "Oh I see",
        "Got it", "Understood", "Sure", "Yeah", "No thanks", "Not now",
        "Maybe later", "I'll come back later", "Let me think", "Just a moment",
        "Pause", "Hold on", "Wait a second", "Can you repeat that?",
        "What did you say?", "Say it again", "Could you elaborate?",
        "Give me an example", "An example please", "I didn't get that",
        "Can you explain that more?", "What does that mean?",
        "Never mind", "Ignore that", "Let's move on", "Next question",
        "Skip that", "That's it", "We're done", "That was helpful",
        "Really helpful", "This is great", "You're good at this",
        "You're really smart", "I like this", "This is fun", "This is boring",
        "I'm going now", "Logging off", "Signing out", "I'll be right back",
        "One moment please", "Let's just chill", "I want to relax",
        "Do you like music?", "What music do you like?", "Tell me about yourself",
        "Is there any news today?", "Do you watch movies?", "What's a good movie?",
        "What's your favorite color?", "Are you a teacher?", "Are you my tutor?",
        "You remind me of my teacher", "I like your style", "Good answer",
        "That's funny", "Ha ha", "LOL", "Very cool", "Nice one",
        "Not bad", "Sounds good", "Sounds great", "Fair enough",
        "Makes sense", "Right", "Agreed", "Absolutely", "Definitely",
        "I think so", "I guess so", "Maybe", "Probably", "Whatever",
        "Who cares?", "Doesn't matter", "Let's go", "Come on",
    ]
    return lines


def video_search():
    t = [(f"Find a video on {x}", x) for x in TOPICS]
    t += [(f"Find a video about {x}", x) for x in TOPICS]
    t += [(f"Show me a YouTube video on {x}", x) for x in TOPICS]
    t += [(f"Show me a YouTube video about {x}", x) for x in TOPICS]
    t += [(f"Any clips explaining {x}?", x) for x in TOPICS]
    t += [(f"Any clips on {x}?", x) for x in TOPICS]
    t += [(f"Get me a video tutorial for {x}", x) for x in TOPICS]
    t += [(f"Get me a video tutorial on {x}", x) for x in TOPICS]
    t += [(f"I need a visual guide on {x}", x) for x in TOPICS]
    t += [(f"I need a visual guide for {x}", x) for x in TOPICS]
    t += [(f"Search for tutorials about {x}", x) for x in TOPICS]
    t += [(f"Search for a clip about {x}", x) for x in TOPICS]
    t += [(f"Look for educational videos about {x}", x) for x in TOPICS]
    t += [(f"Look for educational videos on {x}", x) for x in TOPICS]
    t += [(f"Find some videos for {x}", x) for x in TOPICS]
    t += [(f"Find some videos about {x}", x) for x in TOPICS]
    t += [(f"Find a lecture video on {x}", x) for x in TOPICS]
    t += [(f"Suggest a video for {x}", x) for x in TOPICS]
    t += [(f"Suggest a video on {x}", x) for x in TOPICS]
    t += [(f"I want to watch a video on {x}", x) for x in TOPICS]
    t += [(f"I want a video on {x}", x) for x in TOPICS]
    t += [(f"Can you find a video on {x}?", x) for x in TOPICS]
    t += [(f"Do you have a video about {x}?", x) for x in TOPICS]
    t += [(f"Is there a clip on {x}?", x) for x in TOPICS]
    t += [(f"Recommend a YouTube video about {x}", x) for x in TOPICS]
    t += [(f"Link a video on {x}", x) for x in TOPICS]
    t += [(f"Show a video demo of {x}", x) for x in TOPICS]
    t += [(f"Video examples for {x}", x) for x in TOPICS]
    t += [(f"A tutorial video about {x}", x) for x in TOPICS]
    return [msg for msg, _ in t]


def content_analysis():
    items = []
    for d in DOCS:
        items += [
            f"Summarize the {d}",
            f"Give me a summary of the {d}",
            f"What is the main point of the {d}?",
            f"What is discussed in the {d}?",
            f"Explain the findings in the {d}",
            f"Review the {d} for me",
            f"Break down the key concepts in the {d}",
            f"Extract the main ideas from the {d}",
            f"Analyze the uploaded {d}",
            f"I need an analysis of the {d}",
            f"Tell me more about the content in the {d}",
            f"Summarize the {d} in three bullet points",
            f"Explain the second page of the {d}",
            f"Explain the second section of the {d}",
            f"What does the {d} cover?",
            f"Can you walk me through the {d}?",
        ]
        for topic in TOPICS[:12]:
            items.append(f"What does the {d} say about {topic}?")
            items.append(f"Does the {d} mention {topic}?")
    items += [
        "Summarize this document",
        "Summarize this chapter",
        "Summarize this paper",
        "Summarize this slide",
        "Summarize this file",
        "Summarize this text",
        "Summarize this article",
        "What is this document about?",
        "What is this chapter about?",
        "Can you explain this text?",
        "Help me understand this material",
        "What are the main takeaways?",
        "Give me the key points",
        "What should I remember from the file?",
        "Condense this material",
        "Reduce this chapter to the essentials",
        "What is the thesis of the paper?",
        "What are the arguments in the article?",
        "Give me the conclusion of the paper",
        "What do the findings mean?",
        "Explain the results in the document",
        "Summarize the key findings of the chapter",
        "What are the important definitions here?",
        "List the main topics of the section",
        "Outline the document",
        "What is the structure of the paper?",
        "Tell me about the experiments in the article",
        "What method does the paper use?",
        "What data is in the file?",
        "Explain the figures in the slide",
        "Interpret the table in the document",
        "What is the introduction about?",
        "What does the conclusion say?",
        "Summarize the background section",
        "What are the limitations mentioned?",
        "Compare this with the last chapter",
        "Are the findings consistent?",
    ]
    return items


def assessment():
    t = []
    for x in TOPICS:
        t += [
            f"Quiz me on {x}",
            f"Test me on {x}",
            f"Test my knowledge of {x}",
            f"Test my knowledge on {x}",
            f"Give me 5 questions on {x}",
            f"Give me 10 questions on {x}",
            f"Ask me some questions on {x}",
            f"Ask me 5 questions on {x}",
            f"Check if I understood {x}",
            f"Evaluate my knowledge of {x}",
            f"Evaluate my understanding of {x}",
            f"Assess my understanding of {x}",
            f"Examine me on {x}",
            f"Assess me on {x}",
            f"Can I have a practice test for {x}?",
            f"Can I have a practice test on {x}?",
            f"I'm ready for a test on {x}",
            f"I want a test on {x}",
            f"Give me a quiz on {x}",
            f"Generate a quiz about {x}",
            f"Create an assessment for {x}",
            f"Prepare a quiz for {x}",
            f"Make me a quiz on {x}",
            f"Let's do a quiz on {x}",
            f"Let's take a quiz on {x}",
            f"Question me on {x}",
            f"I want to be tested on {x}",
            f"Start a quiz on {x}",
        ]
    return t


def insight():
    lines = [
        "What is my progress?",
        "How is my progress?",
        "Show my progress",
        "Tell me my progress",
        "Give me a progress update",
        "What's my progress so far?",
        "Progress report",
        "Where do I stand?",
        "How far along am I?",
        "How am I doing?",
        "How am I performing?",
        "Analyze my performance",
        "Evaluate my performance",
        "Tell me about my performance",
        "Am I doing well?",
        "Am I falling behind?",
        "What are my knowledge gaps?",
        "Where are my knowledge gaps?",
        "What do I not know?",
        "Which topics do I struggle with?",
        "Where am I struggling?",
        "What am I weak at?",
        "What are my weak spots?",
        "Tell me my weak areas",
        "What concepts am I missing?",
        "Where do I need improvement?",
        "What do I need to work on?",
        "What should I study next?",
        "What do I review next?",
        "Tell me what to study next",
        "What topics do I need to review?",
        "What should I revise?",
        "What's my next topic?",
        "Plan my next study session",
        "What should I focus on?",
        "What do I prioritize?",
        "Recommend what to study",
        "What can I learn next?",
        "Give me a summary of my achievements",
        "Show my achievements",
        "What have I achieved?",
        "Summarize my milestones",
        "What have I accomplished?",
        "Show my completed topics",
        "Show my learning report",
        "Give me a learning report",
        "Show my learning summary",
        "Display my report",
        "My learning journey",
        "How have I progressed?",
        "Show my growth over time",
        "Show my improvement",
        "Track my progress",
        "Give me my stats",
        "Show my stats",
        "Analyze my learning record",
        "Provide feedback on my learning path",
        "Give me feedback on my progress",
        "Feedback on how I'm doing",
        "Give me some feedback",
        "Any feedback for me?",
        "How can I improve?",
        "Give me a recommendation",
        "Recommendations based on my progress",
        "Give me some recommendations",
        "Personalized recommendations",
        "What do you recommend for me?",
        "I need learning insights",
        "Give me insights about my learning",
        "Share insights on my progress",
        "Analyze my learning behavior",
        "What's my learning pattern?",
        "Tell me about my study habits",
        "How consistent am I?",
        "Am I improving?",
        "Give me a study plan",
        "Create a study plan for me",
        "Plan my studies",
        "Build me a schedule",
        "Make me a plan",
        "I need a learning plan",
        "What's my learning path?",
        "Show my learning path",
        "How am I doing overall?",
        "Any insights?",
        "What should I work towards?",
        "What are my strengths?",
        "What am I good at?",
        "Summarize my learning",
        "Give me a full report",
        "Show me my recent activity",
        "What did I study recently?",
    ]
    for x in TOPICS:
        lines.append(f"How am I doing in {x}?")
        lines.append(f"Am I good at {x}?")
        lines.append(f"What's my level in {x}?")
        lines.append(f"Where do I stand in {x}?")
        lines.append(f"Are there gaps in my {x} knowledge?")
    return lines


def main():
    rng = random.Random(42)

    rows = []
    pools = {
        "CONVERSATION": conversation(),
        "VIDEO_SEARCH": video_search(),
        "CONTENT_ANALYSIS": content_analysis(),
        "ASSESSMENT": assessment(),
        "INSIGHT": insight(),
    }

    targets = {
        "CONVERSATION": 220,
        "VIDEO_SEARCH": 250,
        "CONTENT_ANALYSIS": 260,
        "ASSESSMENT": 270,
        "INSIGHT": 200,
    }

    seen = set()
    for intent, pool in pools.items():
        rng.shuffle(pool)
        pick = targets[intent]
        for msg in pool:
            if pick <= 0:
                break
            if msg in seen:
                continue
            seen.add(msg)
            rows.append((msg, intent))
            pick -= 1

    # Preserve true labels for any message that could appear in another pool.
    label = {m: i for m, i in rows}
    rows = list(label.items())

    rng.shuffle(rows)

    with open(OUT, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["utterance", "intent"])
        w.writerows(rows)

    n = len(rows)
    dist = {}
    for _, i in rows:
        dist[i] = dist.get(i, 0) + 1
    print(f"wrote {n} rows -> {OUT}")
    print("distribution:", ", ".join(f"{k}={v}" for k, v in sorted(dist.items())))


if __name__ == "__main__":
    main()