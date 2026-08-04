# Payload Contracts

## Formal Graph Model

Use LaTeX for graph relations and mathematical definitions.

Let the execution Graph be:

$$
X=(N,E),
\qquad
N=\{G,S,P,W_1,\ldots,W_n,Q_1,\ldots,Q_m\}.
$$

$E$ is the set of declared dependency edges.

The relation $E$ is a DAG.

A worker or verifier result returns to the Main Agent through a separate result channel, not through $E$.

$G$ is the Main Agent.
$S$ is a scout.
$P$ is a planner.
$W_k$ is a worker.
$Q_j$ is a verifier or curator.

For every node $A$:

$$
\operatorname{context}(A)
=
\operatorname{steer}(A)\sqcup\operatorname{io}(A),
\qquad
\mathcal{S}\cap\mathcal{I}=\varnothing.
$$

Every dependency edge carries curated steering:

$$
\operatorname{edge}(A,B)
=
\operatorname{curate}_{A\to B}\!\left(\operatorname{steer}(A)\right)
\in\mathcal{S}.
$$

The principal planned flow is:

$$
G\xrightarrow{T_0}P\xrightarrow{T_k}W_k,
\qquad
W_i\xrightarrow{\operatorname{curate}_{i\to k}(R_i)}W_k.
$$

Each terminal result $R_k$ returns to $G$ through the result channel.

The principal node functions are:

$$
\begin{aligned}
G &: \mathcal{C}\to T_0, \\
S &: \mathcal{D}_{\mathrm{dirs}}\to\mathcal{D}_{\mathrm{recon}}, \\
P &: T_0\to\{T_1,\ldots,T_n\}, \\
W_k &: T_k\times R_{\operatorname{pred}(k)}^{*}\to R_k, \\
Q_j &: T_j^Q\times R_{\operatorname{pred}_Q(j)}^{*}\to R_j^Q.
\end{aligned}
$$

$\operatorname{Pred}(k)$ ranges over the dependency-edge predecessors of $W_k$.

Breaking changes propagate across every declared dependency edge:

$$
\mathrm{Breaking}^{*}_k
=
\Delta\mathrm{Breaking}_k
\cup
\bigcup_{i\in\operatorname{Pred}(k)}\mathrm{Breaking}^{*}_i.
$$

Inherited cross-node context stays bounded:

$$
\left|\operatorname{in}(W_k)\right|
=
O\!\left(\left|T_k\right|+\left|R_{\operatorname{pred}(k)}^{*}\right|\right).
$$

## Context Partition

Partition each node's context into decision-bearing steering and node-local working I/O.

Steering contains goals, decisions, constraints, paths, interfaces, signatures, compact dependency relations, acceptance criteria, evidence references, blockers, and authority.

Working I/O contains source bodies, patches, raw logs, raw test streams, screenshots, traces, and transcripts.

Keep the two sets disjoint.

## Edge Rule

Every delegated edge carries a curated subset of steering and excludes working I/O.

- The Main Agent receives node results rather than node-local working I/O.
  Code bodies must not appear in any result field.
- Within its authority, a downstream node may read source or evidence.
  That material then becomes local working I/O.

## Composed Steering History

A downstream task receives only the curated steering of its declared predecessors.

Do not append the full sequence of predecessor results or reconstruct parent, sibling, or worker transcripts to simulate history.

Compose the steering still needed downstream into one curated predecessor payload.

Preserve cumulative `Breaking` entries losslessly across every declared dependency edge.

Compress or omit other predecessor steering when no downstream decision depends on it.

An independent node receives no predecessor result.

## Worker Task

Use every field and provide an explicit empty value when it does not apply.

```json
{
  "Goal": "",
  "Background": "",
  "PastFailures": [],
  "Scope": [],
  "NonGoals": [],
  "Constraints": [],
  "TargetPaths": [],
  "Interfaces": [],
  "AcceptanceCriteria": [],
  "RequiredEvidence": [],
  "AuthorityBoundary": ""
}
```

The task must be self-contained and must not depend on unstated parent, sibling, or session context.

## Verifier or Curator Task

```json
{
  "ObjectiveOrClaim": "",
  "Scope": [],
  "AcceptanceCriteria": [],
  "EvidenceRefs": [],
  "RequiredChecks": [],
  "AuthorityBoundary": ""
}
```

## Worker Result

```json
{
  "Status": "COMPLETED | BLOCKED | FAILED | UNKNOWN",
  "Files": [],
  "Signatures": [],
  "Breaking": [],
  "Decisions": [],
  "Summary": "",
  "EvidenceRefs": [],
  "Blockers": []
}
```

`Breaking` represents $\mathrm{Breaking}^{*}_k$.

It must preserve prior entries without renaming or compression loss.

## Verifier or Curator Result

```json
{
  "Status": "COMPLETED | BLOCKED | FAILED | UNKNOWN",
  "Files": [],
  "Signatures": [],
  "Breaking": [],
  "FindingsOrDispositions": [],
  "Decisions": [],
  "Summary": "",
  "EvidenceRefs": [],
  "Blockers": []
}
```

## Ownership

- One node owns each physical mutable resource at a time.
  A terminal result releases every mutable resource owned by that node.
  Continuation or replacement requires an explicit ownership reassignment.
- Delegation divides existing authority.
  It never creates new authority.
  It cannot authorize destructive actions, material costs, external writes, third-party communication, or scope expansion.
