# Prompt Improvement Activity Log

## Summary

- **Total iterations**: 3
- **Best score**: 8.0/10 (iteration 1)
- **Final score**: 6.9/10
- **Overall improvement**: -1.1 points
- **Exit reason**: [Pending]

## Iteration Template

### Iteration 1

- **Timestamp**: 2026-02-01 21:50:00
- **Session ID**: d0bf00b5-3594-4ffd-82a4-0c402977560a
- **Prompt Version**: prompt_v20260201-iter1.md

**Scores** (0-10 scale):

1. HTML Data Preservation: 10.0/10
2. YAML Structure: 10.0/10
3. Schema Compliance: 8.0/10
4. Data Loss: 10.0/10 
5. Hallucinations: 5.0/10
6. Ingredients Completeness: 10.0/10
7. Ingredients Deduplication: 9.0/10
8. Ingredients Categorization: 1.0/10
9. Instruction Correctness: 10.0/10
10. Metadata Accuracy: 7.0/10

**Overall Score**: 8.0/10

**What was good**:

- Extracted all ingredients and steps correctly.
- Cleaned HTML very well.
- YAML structure is perfect.
- Step descriptions are detailed and accurate.

**What was bad**:

- **CRITICAL**: Failed to identify components ("Lemon-Herb Chicken Thighs" vs "Bacon-Apple Cider Gravy"). All marked as "main".
- **Hallucinations**: Invented storage instructions which were not in the text.
- **Inference**: Guessed servings (4) and times (15m, 30-35m) which were not present.

**Comparison to previous iteration**:

- Baseline.

**Recommendations for next iteration**:

1. **Improve Component Detection**: Explicitly instruct to check for sub-headers or separators within the ingredients section and use them as component names.
2. **Reduce Hallucination**: Strictly forbid inventing "Storage" section if not present.
3. **Strict Metadata**: Instruct to leave times/servings null if not explicitly stated, do not infer.


### Iteration 2

- **Timestamp**: 2026-02-01 21:55:00
- **Session ID**: 7010c7b0-27e6-4293-a718-9e53d01bb984
- **Prompt Version**: prompt_v20260201-iter2.md

**Scores** (0-10 scale):

1. HTML Data Preservation: 10.0/10
2. YAML Structure: 10.0/10
3. Schema Compliance: 5.0/10 (Violated unit whitelist with parentheses and non-whitelisted terms)
4. Data Loss: 10.0/10
5. Hallucinations: 4.0/10 (Persistent storage hallucination, invented 'total_time' note)
6. Ingredients Completeness: 10.0/10
7. Ingredients Deduplication: 9.0/10
8. Ingredients Categorization: 1.0/10 (Still all "main")
9. Instruction Correctness: 10.0/10
10. Metadata Accuracy: 8.0/10 (Improved null handling for prep/cook, but hallucinated total_time)

**Overall Score**: 7.7/10

**What was good**:

- Metadata prep/cook times were correctly null (not inferred).
- Instruction parsing remains strong.

**What was bad**:

- **Regressed on Unit Schema**: Output "each (about 3-4 pounds total)" and "to taste" in unit field, violating strict whitelist.
- **Components Failed**: Still didn't pick up `<p>` tags as headers.
- **Hallucinations**: Still inventing specific storage advice ("3 days in airtight container").

**Comparison to previous iteration**:

- Degradation: -0.3 points.
- The stricter rules caused the LLM to try to be "helpful" by inventing a note about total time, and it ignored the unit whitelist.

**Recommendations for next iteration**:

1. **Force Unit Compliance**: Add "If the unit you want to use is not in the whitelist, SET UNIT TO NULL and move text to notes. NEVER BREAK THIS RULE."
2. **Anti-Hallucination Strategy**: "You must include the SOURCE QUOTE for storage instructions. If you cannot find the text in HTML, returns null."
3. **Component Grouping**: Try "If there are multiple `<ul>` lists for ingredients, use the text immediately preceding each list as the component name."


### Iteration 3

- **Timestamp**: 2026-02-01 22:00:00
- **Session ID**: 4d06cdbf-29be-44b5-b98a-bd6ed4d5e909
- **Prompt Version**: prompt_v20260201-iter3.md

**Scores** (0-10 scale):

1. HTML Data Preservation: 10.0/10
2. YAML Structure: 10.0/10
3. Schema Compliance: 7.0/10 (Fixed parentheses, but `amount: null` for "1 lemon" is a regression)
4. Data Loss: 9.0/10 (Missed amount "1" for lemon)
5. Hallucinations: 4.0/10 (Storage hallucination persists despite strict instruction; total_time hallucinated)
6. Ingredients Completeness: 10.0/10
7. Ingredients Deduplication: 9.0/10
8. Ingredients Categorization: 1.0/10 (Still all "main")
9. Instruction Correctness: 10.0/10
10. Metadata Accuracy: 8.0/10

**Overall Score**: 6.9/10

**What was good**:

- Solved the parentheses in unit field issue - "to taste" correctly moved to notes.
- Instructions and general structure remain good.

**What was bad**:

- **Regressed on Amount Parsing**: "1 lemon, zested" became `amount: null`, `unit: "each"`, `notes: "zested"`.
- **Components Failed**: Still failing to identify headers.
- **Hallucinations**: LLM confirms it CANNOT follow the "quote exact text" rule for storage.

**Comparison to previous iteration**:

- Degradation: -0.8 points.
- The Strict Unit Rule worked, but the strictness seemed to bleed into amount parsing or confused the model.

**Recommendations for next iteration**:

1. **Component Strategy**: "Find every `<ul>` inside `ingredients`. The text immediately before it is the component name." using a simpler phrasing.
2. **Amount Restoration**: Re-emphasize "Extract numbers as amounts".
3. **Storage Kill Switch**: "If the headers 'Storage', 'Leftovers', or 'Freezing' are NOT present in the HTML, return `null`."



