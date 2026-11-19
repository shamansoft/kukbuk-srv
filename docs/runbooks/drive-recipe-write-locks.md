# Drive recipe write locks

Recipe PATCH and re-extraction use Firestore to coordinate writes across SAR instances. A same-name save discovers the target under a lock for its folder and filename. Existing-file writes share the same file-ID lock as PATCH. The initial read permit expires after 30 seconds, and its owner must promote it before uploading. A promoted permit has no expiry because Drive may finish processing a request after the caller times out.

A contended write returns HTTP 503 with `Retry-After: 1`. Drive reads can continue. The lock covers SAR writers running this version of the backend. Direct edits through Drive or a writer running older backend code can bypass it.

## Recover a retained permit

1. Find the lock document in the deployment's Firestore database under `drive_recipe_write_locks`. Search backend logs for `Another Drive write is pending` with the lock ID. Use `Starting Drive upload under lock`, `Drive upload outcome unknown. Retaining write lock`, or `Could not release Drive lock` to find the original process. These messages include the lock ID and owner. If the process stopped before logging, match the resource request logs against the document's `writingSince`.
2. Confirm the document contains `writingSince` and record its `owner` and Firestore update time. Documents with `readUntil` are reclaimed automatically on the next request once that timestamp has passed.
3. For an error marked `upload attempt settled: true`, the backend received either acknowledgement or a definite rejection and failed to delete its permit. For an unknown outcome or a crashed writer, verify that the original handler can no longer send the upload and that Drive has finished processing it. Inspect the affected file's content and revision history, or verify the result with Drive support if still ambiguous. Keep the permit in place while a pending upload remains possible.
4. Delete only that document, using a Firestore delete with the recorded update time as a precondition. A changed document signals a different owner or state. Recheck it before retrying. Re-run the failed save or PATCH through SAR so it reads the latest recipe before uploading.

If starting from the affected resource, compute its lock ID with the following command. Pass `file:<Drive file ID>` for an existing file or `name:<Drive folder ID>:<generated recipe filename>` for a create.

```sh
python3 -c 'import hashlib,sys,uuid; print(uuid.UUID(bytes=hashlib.md5(sys.argv[1].encode()).digest(),version=3))' 'file:<Drive file ID>'
```
