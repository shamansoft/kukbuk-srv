You are merging a review panel's findings into one set. You are not reviewing code, you must not add a
finding of your own, and you must not judge whether a finding is true.

**Work from the findings text below and nothing else. Do not open files, do not run `git diff`, `rg`
or any other command, and do not go looking at the code.** Deciding whether two findings are the same
issue, which sources raised each, and which singletons are too weak to keep are all answerable from
what you have been given. A verifier runs after you with the code in front of it, and that is where
a finding is confirmed or rejected — duplicating it here spends a whole model run to reach a verdict
that is about to be reached properly, and an unverified opinion formed here contaminates the set the
verifier is handed.

Nothing you do writes anything. Do not modify, delete, move, stage or commit, and do not write a file
through a shell redirect.

## Sources that ran

2 sources ran, 2 reported.
- bugs (lenses: bugs) reported no findings
- adversarial (lenses: adversarial) reported 2 findings: adversarial-1, adversarial-2

Treat that list as fact. It is what actually ran, not what was requested — never infer the source
count from the findings themselves.

## Findings

[
  {
    "id": "adversarial-1",
    "file": "extractor/src/main/java/net/shamansoft/cookbook/service/RecipeService.java",
    "line": 349,
    "end_line": 355,
    "severity": "major",
    "confidence": 95,
    "title": "Concurrent field patches write stale copies of the entire YAML",
    "body": "If a tags-only PATCH and a notes-only PATCH read the same file before either writes, each modifies its own snapshot and unconditionally replaces the entire file. Whichever writes second restores the other field's old value, despite both requests succeeding. This violates the explicit last-write-wins-per-field contract and the promise that absent fields remain unchanged. The unconditional read/write mechanism is pre-existing; adding userNotes exposes this conflict between two independently editable fields.",
    "fix": "Use storage revision preconditions and retry conflicting writes by reading the latest file and reapplying only the fields present in the request.",
    "sources": [
      "adversarial"
    ],
    "lenses": [
      "adversarial"
    ],
    "verdict": ""
  },
  {
    "id": "adversarial-2",
    "file": "extractor/src/main/java/net/shamansoft/cookbook/service/RecipeService.java",
    "line": 193,
    "end_line": 0,
    "severity": "major",
    "confidence": 95,
    "title": "Failed preservation reads use the missing-file empty-note fallback",
    "body": "When re-saving an existing recipe, a transient failure reading its content is converted by findExistingYaml into Optional.empty(). This initializes userNotes to an empty string, and uploadRecipe then overwrites the existing file without user_notes if the subsequent upload succeeds. A saved personal note is therefore erased after a failed read. The fail-open lookup behavior is pre-existing for tags; this change extends it to notes, contradicting the re-save preservation contract.",
    "fix": "Distinguish confirmed absence from read failure and abort the overwrite when existing user data cannot be read. Ensure provider implementations also propagate failures other than confirmed file-not-found responses.",
    "sources": [
      "adversarial"
    ],
    "lenses": [
      "adversarial"
    ],
    "verdict": ""
  }
]

## What to produce

1. Split out what is not a defect in the change under review. A question the reviewer could not
   answer from the code goes to **open questions**; a defect in code the change did not touch goes to
   **pre-existing**. Move both out first: neither is deduped, boosted or dropped.

2. Deduplicate. Two findings are the same when they name the same file within two lines of each other
   and describe the same problem. Merge them into one, keeping the clearest title and body.

3. Confidence on a merged finding is `min(99, highest confidence + 10 * (distinct sources - 1))`.
   A source is a process. One process reporting the same problem under two of its lenses is still one
   source and earns no boost.

4. Severity is the highest severity any input claimed.

5. Drop a finding that has a single source, confidence below 80, and nothing corroborating it.
   Never drop a critical or a major this way — a single source is not evidence against a serious
   defect, and only one reviewer looking in the right place is the normal case for the worst bugs.
   Keep it and route it to the verifier, which is the authority on whether it is real.
   When the source list above shows the run was degraded, drop nothing: keep every would-be-drop and
   route it to the verifier instead. Corroboration is rarer with a source missing, so the drop rule
   starts eating findings the missing source would have confirmed, and the verifier is the authority
   anyway.

Every output finding carries the ids of the input findings it came from — one id when nothing was
merged. Attribution is derived from those ids, so an output with none is unusable.

As you work, narrate what you are doing. This is a running commentary read live by a human watching the run, and it is separate from your answer, which goes only in the structured output.

- Before each group of related tool calls, write one short line saying what you are about to check and why: "checking whether the stagger gate can still open on a fork".
- When something turns out to matter, say so in one line as you find it.
- Keep going for the whole review. Do not narrate the opening few steps and then fall silent for the rest of it — a reader who stops seeing lines cannot tell you apart from a hung process.
- One line at a time, under a dozen words, and never a summary of what you already said.
