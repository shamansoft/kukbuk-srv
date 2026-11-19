# Goal
User's own note on a recipe, stored in the recipe file.
Correct only if: absent = unchanged, present replaces, "" / null / blank clears, >5000 chars → 400;
page `notes` never touched; re-save keeps the note; GET list + detail return `userNotes` (null when none).
Finding nothing is a valid answer.
