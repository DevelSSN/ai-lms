# Seed Course Handbook

This handbook is the reference material for the AI-LMS evaluation corpus. Each
chapter covers one course topic taught in the programme and is written to be
self-contained: a learner may read the chapter alone, ask what it says about the
topic, or be quizzed on it.

The handbook is deliberately broad rather than deep. It exists so that a question
of the form "what does the document say about *T*" has an answer in the material
for every *T* named in the labelled utterance corpus, and so that assessment
requests draw on real text rather than on the model's prior knowledge.

## Algorithms

An algorithm is a finite, deterministic procedure that transforms an input into a
desired output. Textbook analysis measures an algorithm by its time complexity,
usually expressed with big-O notation, and by its space complexity. Sorting,
graph traversal, and dynamic programming are the three families most often taught,
because each illustrates a different trade-off between memory and repeated work.
Correctness is argued by loop invariants or by induction on the input size.

## Artificial Intelligence

Artificial intelligence is the field concerned with systems that act rationally,
whether by search, by probabilistic reasoning, or by learning from data. Classical
symbolic approaches encode knowledge as rules; modern machine-learning approaches
estimate parameters from examples. Evaluation of an AI system separates task
accuracy from robustness, since a model that scores well on a held-out set may
still fail under distribution shift.

## Astrophysics

Astrophysics applies physics to celestial objects and to the universe as a whole.
Stellar structure is understood through hydrostatic equilibrium, in which gravity
is balanced by pressure from nuclear fusion in the core. Spectral lines reveal
composition and temperature, while redshift measured across distant galaxies
supports the expansion of the universe. Planetary science extends the same
methods to formation, orbital dynamics, and atmospheric chemistry.

## Big Data

Big data refers to datasets too large or too fast-moving for a single machine to
process with conventional tools. The discipline is organised around the three
known properties of volume, velocity, and variety, and around the system designs
that follow from them: distributed storage, batch and stream processing, and
eventual consistency across replicas. Sampling is often rejected in favour of
processing the whole dataset, because the cost of a distributed pass is dominated
by fixed overhead rather than by record count.

## Calculus

Calculus is built on two operations, differentiation and integration, linked by
the fundamental theorem. Derivatives describe instantaneous rate of change and
give the slope of a tangent line; integrals accumulate a quantity over an
interval and give the area under a curve. Limits make both operations rigorous.
Single-variable calculus extends to several variables through partial derivatives
and gradients, which are the basis of the optimisation used in machine learning.

## Cell Biology

A cell is the smallest unit that carries out the processes of life. Eukaryotic
cells compartmentalise these processes: the nucleus stores DNA, ribosomes
translate messenger RNA into protein, mitochondria generate ATP, and the
endoplasmic reticulum and Golgi apparatus process and transport proteins. The
plasma membrane is selectively permeable and controls exchange with the
environment. The cell cycle is regulated by cyclins, and failure of that
regulation is one route to uncontrolled division.

## Classical Mechanics

Classical mechanics describes motion under forces, from Newton's three laws
through to Lagrangian and Hamiltonian reformulations. Conservation laws follow
from symmetries: conservation of momentum from spatial homogeneity, and
conservation of energy from time invariance. Rigid-body rotation introduces
moment of inertia, and oscillatory motion leads to the harmonic oscillator, the
central model for small vibrations in physical and economic systems alike.

## Cognitive Science

Cognitive science studies the mind as an information-processing system, drawing
on psychology, neuroscience, linguistics, and computer science. Central topics
include attention, working memory with its limited capacity, and the distinction
between declarative and procedural knowledge. Models of learning distinguish
acquisition of facts from transfer of skill, and describe forgetting as decay
interfered with by competing memories rather than as simple loss.

## Compiler Design

A compiler translates source code into a lower-level representation. The front
end performs lexical analysis, parsing, and semantic analysis, producing an
abstract syntax tree. The middle end applies optimisations such as constant
propagation, dead-code elimination, and inlining on an intermediate
representation. The back end schedules instructions and allocates registers for
the target architecture. Parsing theory distinguishes regular, context-free, and
context-sensitive structure, which determines what each pass can decide.

## Computer Architecture

Computer architecture concerns the organisation of the hardware that executes
instructions. The fetch-decode-execute cycle is refined by instruction pipelining,
which overlaps stages of successive instructions and is disrupted by hazards.
The memory hierarchy trades speed against capacity through registers, cache, main
memory, and disk, and its behaviour is described by locality of reference.
Instruction-set design balances addressing modes, word size, and the frequency of
each operation in real workloads.

## Computer Networks

Computer networks move data between hosts according to layered protocols. The
transmission layer provides reliability or speed, the network layer provides
addressing and routing across independent networks, and the link layer provides
framing on a single medium. The layered model matters because each layer can be
replaced without redesigning the others. Congestion control, bandwidth-delay
product, and the choice between circuit and packet switching determine how well a
network carries interactive traffic.

## Cyber Security

Cyber security protects the confidentiality, integrity, and availability of
systems and the data they hold. Threat modelling begins with an inventory of
assets and an adversary capable of acting against them. Defences include
authentication, least privilege, encryption in transit and at rest, and
monitoring for anomalous behaviour. Cryptographic primitives are analysed for
resistance to known attacks, and system security is argued from isolation and
from the absence of unintended channels rather than from secrecy of design.

## Data Structures

A data structure organises values so that the operations an application needs are
efficient. Arrays give constant-time indexing but costly insertion; linked lists
give cheap insertion but linear traversal; hash tables give amortised constant
time at the cost of ordering; trees and heaps give ordered access with logarithmic
height. Choice of structure is driven by the access pattern, and each structure
carries its own invariants that the operations must preserve.

## Database Systems

A database system stores structured data and exposes it through a query language.
The relational model organises data into tables related by keys, and queries are
declared in a form the optimiser can rearrange. Transactions provide atomicity,
consistency, isolation, and durability; isolation levels trade throughput against
anomalies such as lost updates and non-repeatable reads. Storage is organised into
pages and indexed with B-trees so that lookups do not scan whole relations.
Normalisation removes redundancy at the cost of more joins at query time.

## Discrete Math

Discrete mathematics covers the structures that are countable rather than
continuous: sets, relations, functions, graphs, and combinatorial counts. Logic
provides the connectives and quantifiers used to state proofs, and proof
techniques include direct argument, contraposition, contradiction, and induction.
Graph theory contributes trees, connectivity, and colouring; combinatorics
contributes counting arguments and generating functions. These tools underpin
analysis of algorithms and of networks.

## Ethics

Ethics in a computing context examines what systems ought to do, not only what
they can do. Normative frameworks include consequentialism, which judges acts by
their outcomes; deontology, which judges them by adherence to duty; and virtue
ethics, which judges the character of the actor. Applied topics include informed
consent for data collection, fairness in classification, accountability when an
automated decision harms someone, and the distribution of the benefits and costs
of a system across the people it affects.

## Fluid Dynamics

Fluid dynamics treats liquids and gases as continua described by velocity and
pressure fields. The Euler equations neglect viscosity, while the Navier-Stokes
equations include it. Flow is classified as laminar or turbulent by Reynolds
number, which compares inertial to viscous forces. Bernoulli's principle relates
pressure to speed along a streamline, and the boundary layer near a solid surface
is where most real drag originates.

## Game Theory

Game theory models strategic interaction among agents with possibly conflicting
interests. A game is specified by players, strategies, and payoffs. The central
solution concept is the Nash equilibrium, a strategy profile from which no player
can improve by changing alone. Zero-sum games admit minimax strategies, and
repeated games permit cooperation that one-shot games do not. Mechanism design
works backwards, asking what rules would produce a desired equilibrium.

## Genetics

Genetics studies inheritance and the variation it produces. DNA sequences are
transcribed to RNA and translated into protein, and variation arises through
mutation, recombination, and selection. Mendelian inheritance describes segregation
of alleles, while population genetics describes how allele frequencies change over
generations under drift, migration, and selection. Sequencing technologies have
made genome-scale association studies possible, at the cost of careful control for
population structure and multiple testing.

## Human Anatomy

Human anatomy describes the structure of the body at scales from organs to
tissues. The organ systems are conventionally grouped as integumentary, skeletal,
muscular, nervous, endocrine, cardiovascular, lymphatic, respiratory, digestive,
urinary, and reproductive. Structure is studied in relation to function, and
regional anatomy is taught alongside systemic anatomy so that spatial relations
among structures are preserved. Gross anatomy is supplemented by histology at the
tissue level.

## Linear Algebra

Linear algebra studies vectors, matrices, and the linear maps they represent.
Matrix multiplication composes linear maps, and solving a linear system is
equivalent to inverting a map. Eigenvalues and eigenvectors characterise directions
left unchanged by a map, which makes them central to stability analysis and to
principal component analysis. Rank, span, null space, and orthogonality are the
structural properties used to describe subspaces, and decomposition theorems such
as singular value decomposition make them computable.

## Linear Programming

Linear programming optimises a linear objective subject to linear constraints on
non-negative variables. The feasible region is a convex polytope, and the
optimum, if one exists, lies at a vertex; this is the basis of the simplex
method, which moves along edges between vertices. Duality associates every
problem with a dual whose optimal values coincide, which gives both a bound and
an economic interpretation in terms of shadow prices. Integer variants are
computationally harder.

## Machine Learning

Machine learning builds systems that improve their performance on a task by
exposure to data rather than by explicit programming. Supervised learning fits a
function from labelled examples and is evaluated on held-out data; unsupervised
learning finds structure without labels; reinforcement learning optimises a
reward through interaction. Overfitting is controlled by regularisation, by
validation, and by limiting model capacity. The distinction between training and
evaluation data is what makes reported accuracy meaningful.

## Macroeconomics

Macroeconomics studies the economy as a whole: output, unemployment, inflation,
and the policies that affect them. National income is measured by gross domestic
product, computed by the expenditure, income, or production approach. Aggregate
demand and aggregate supply determine the price level in the short run, while
growth of capital, labour, and productivity determines output in the long run.
Monetary and fiscal policy act on demand, with lags and with effects that depend
on expectations.

## Microeconomics

Microeconomics studies the decisions of individual agents and how those decisions
interact in markets. Consumer choice is modelled as utility maximisation under a
budget constraint, and the slope of the demand curve follows from substitution
and income effects. A firm chooses output where marginal cost meets marginal
revenue. Market structure ranges from perfect competition to monopoly, and
externalities and public goods are the standard cases where private decisions
diverge from social optimum.

## Molecular Biology

Molecular biology explains cellular processes in terms of the molecules that carry
them out. The central dogma describes information flow from DNA to RNA to protein,
and its regulation operates at transcription, RNA processing, translation, and
post-translational modification. Techniques such as polymerase chain reaction,
gel electrophoresis, and sequencing are the experimental basis of the field, and
recombinant DNA methods allow individual genes to be isolated and studied.

## Network Security

Network security defends the communication links and the services they carry.
Perimeter controls include firewalls and segmentation; transport security uses
authenticated encryption to provide confidentiality and integrity end to end.
Intrusion detection monitors traffic for signatures or for anomalies. Denial of
service attacks exhaust a resource rather than steal data, and their mitigation
depends on redundancy and on filtering upstream of the target.

## Organic Chemistry

Organic chemistry is the chemistry of carbon compounds, whose ability to form four
bonds and to chain gives rise to enormous structural diversity. The field is
organised by functional groups — alcohols, aldehydes, carboxylic acids, amines,
and others — each with characteristic reactions. Reaction mechanisms describe the
movement of electron pairs, distinguishing nucleophilic from electrophilic attack
and substitution from elimination. Stereochemistry matters because the spatial
arrangement of atoms can change a molecule's properties entirely.

## Operating Systems

An operating system mediates between applications and hardware. It schedules
processes and threads, manages virtual memory through paging and translation
lookaside buffers, and arbitrates file systems and devices through drivers.
Concurrency introduces deadlock, which is addressed by enforcing a global ordering
on resources. System calls are the controlled interface across the user and kernel
boundary, and their cost is one reason batched and asynchronous interfaces exist.

## Psychology

Psychology is the scientific study of behaviour and mental processes. Experimental
method controls confounds through randomisation and blinding, and reliability is
established by replication. Major areas include perception, learning, memory,
development, and personality. Cognitive psychology models internal processes such
as attention and working memory, while clinical psychology studies the
classification and treatment of disorders, where diagnosis relies on agreed
criteria rather than on a single biomarker.

## Quantum Physics

Quantum physics describes matter and radiation at atomic scales. States are
represented by vectors in a Hilbert space, and observables by operators whose
eigenvalues are the possible measurement outcomes. Superposition and entanglement
distinguish quantum from classical description, and the uncertainty principle
bounds the joint precision of conjugate variables. The Schrödinger equation
gives the time evolution of a state, and tunnelling explains phenomena that
classical energy barriers would forbid.

## Software Engineering

Software engineering applies engineering discipline to the construction and
maintenance of software. Requirements are gathered and specified before design,
though iterative processes deliver in increments and accept changing requirements.
Design separates concerns through modularity and defined interfaces, and quality
is argued from testing at unit, integration, and system levels together with
review. Maintenance dominates lifetime cost, so readability and modifiability are
treated as first-class properties alongside correctness.

## Sociology

Sociology studies social structures, institutions, and the patterns of interaction
among people. Core concepts include social stratification, role, norm, and
institution, and the distinction between agency and structure. Research methods
include surveys, interviews, ethnography, and secondary analysis of administrative
data, each with its own trade-off between depth and generalisability. Theory
ranges from functional accounts of how institutions stabilise a system to conflict
accounts of how they reproduce inequality.

## Statistics

Statistics provides the tools for drawing conclusions from data. Descriptive
statistics summarise a sample; inferential statistics generalise to a population
through estimation and hypothesis testing. The sampling distribution of a
statistic supports confidence intervals and tests, and the central limit theorem
explains why many such distributions are approximately normal. Regression models
the relationship between variables, while analysis of variance partitions
variability among groups. Multiple comparisons and selection bias are the usual
threats to a stated significance level.

## Thermodynamics

Thermodynamics studies energy, heat, and work. The first law conserves energy
within a system, and the second law imposes a direction on spontaneous change
through entropy. Equilibrium states are related by equations of state, and cycles
such as the Carnot cycle bound the efficiency of any heat engine. Statistical
mechanics interprets entropy as a count of microscopic configurations, which
connects the macroscopic laws to the behaviour of particles.

## World History

World history examines change over long periods across regions that were in
contact. Periodisation is conventional rather than absolute, but recurring themes
include the rise and fall of agrarian empires, the expansion of trade networks,
and the effects of industrialisation on labour and demography. Sources are read
critically, since surviving records over-represent literate and elite producers,
and comparison across regions is used to separate local contingency from
structural causes.
