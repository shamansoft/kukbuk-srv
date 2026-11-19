# Goal
Let users set their own tags on a saved recipe; stored in the recipe file in their Drive/Dropbox.
Correct only if:
- absent field unchanged, present replaces; normalization + limits per contract
- PATCH never corrupts or drops other content of the stored file, on both providers
- re-extracting the same recipe keeps existing user tags; a failed lookup never blocks a save
- no auth bypass: a user can only patch their own files
Finding nothing is a valid answer.
