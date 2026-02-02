# Prompt Improvement Activity Log

## Summary

- **Total iterations**: 1
- **Best score**: 8.0/10 (iteration 1)
- **Final score**: 8.0/10
- **Overall improvement**: 0.0 points
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

