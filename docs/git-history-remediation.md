# Regulatory assessment in public Git history — owner action

Written 2026-08-26 during the final release-blocker pass. **Nothing was rewritten.** History rewriting
and force-pushing are owner decisions with consequences this task cannot take on someone's behalf,
and the assessment's contents were not opened, altered or copied anywhere in the course of writing
this.

## What was found

`docs/regulatory-qualification-assessment.md` and its PDF export are correctly untracked *today* —
`.gitignore` covers them and `git ls-tree -r HEAD` confirms neither is in the current tree. They are
nevertheless **still in reachable history on a public repository**.

| | |
|---|---|
| Repository | `https://github.com/Morfildor/Just-the-Carbs` — **public** since 2026-08-16 |
| `.md` added | `7a3b43a` — *Draft the §44 qualification assessment for owner signature*, 2026-08-14, 14,899 bytes |
| `.pdf` added | `7212efb` — *Add the Play release readiness pass: fail-closed signing and honest disclosures*, 2026-08-14, 193,753 bytes |
| Both removed | `5675c45` — *Polish repeat-use UX, accessibility, and meal workflow*, 2026-08-16 |
| Commits carrying the `.md` | 5 |
| Reachable from `main`? | **Yes.** Both adding commits are ancestors of `main` |

So `git log -p` on any clone, and GitHub's own commit view, still serve both files in full.

The order of events matters for the risk assessment: the files were committed on **2026-08-14**, when
the repository was still private, and removed on **2026-08-16** — the same day the repository was made
public so GitHub Pages could serve the privacy policy. Whether the blobs were ever publicly reachable
depends on which of those two happened first on 2026-08-16, and that ordering is **not established
here** — it needs the repository's own visibility-change timestamp from GitHub's settings/audit log.

## Why it matters

The repository is public and the assessment carries the owner's personal information. §7.1 of the
assessment additionally forbids the owner's personal circumstances appearing in published material,
which a public Git object arguably is.

This is a **release-governance action, not a software defect**. No shipped artifact contains these
files; the app, the AAB and the working tree are all clean.

## Remediation procedure — owner action, do not run unattended

Rewriting history changes every commit SHA from `7a3b43a` onward and requires a force-push. On a
public repository that is not a full remedy on its own: forks, clones, GitHub's cached commit views
and third-party mirrors are not reached by it. **Treat the contents as already disclosed and plan
accordingly** — the rewrite limits further spread, it does not undo the exposure.

### Before anything

1. Establish whether the blobs were ever publicly reachable. Check the repository's visibility-change
   timestamp against `5675c45`'s commit time. Record the answer with its source and date; it decides
   whether this is a cleanup or an incident.
2. Take a full backup of the repository — `git clone --mirror` to somewhere off this machine.
3. Confirm nobody else has a clone or an open fork.

### The rewrite

`git filter-repo` (not `filter-branch`, which is deprecated and slower). It is a Python tool:

```bash
pip install --user git-filter-repo
```

Run it on a **fresh mirror clone**, which is what filter-repo expects:

```bash
git clone --mirror https://github.com/Morfildor/Just-the-Carbs.git jtc-rewrite.git
cd jtc-rewrite.git

# Dry run first: lists what would be removed and writes a report, changing nothing.
git filter-repo --analyze

git filter-repo \
  --invert-paths \
  --path docs/regulatory-qualification-assessment.md \
  --path docs/regulatory-qualification-assessment.pdf
```

`--invert-paths` with two explicit `--path` values removes **only** those two paths. It does not
touch any other file, and it does not alter the contents of the regulatory documents themselves —
they remain on disk, untracked, exactly as they are now.

Verify before pushing anything:

```bash
git log --all --oneline -- docs/regulatory-qualification-assessment.md   # must print nothing
git log --all --oneline -- docs/regulatory-qualification-assessment.pdf  # must print nothing
git rev-list --all --count                                              # sanity: still ~62
```

Then, and only then:

```bash
git remote add origin https://github.com/Morfildor/Just-the-Carbs.git
git push --force --all
git push --force --tags
```

### Afterwards

- Ask GitHub Support to purge cached views of the old commits and to garbage-collect the unreachable
  objects. A force-push alone leaves them retrievable by SHA for some time.
- Delete and re-clone every local working copy. A stale clone will happily push the old history back.
- If the rewrite lands, note that the release-provenance commit SHA recorded for the v1 build changes
  with it, so re-record it (see `docs/play-release-readiness.md`).

### The alternative

If the visibility check shows the blobs were never publicly reachable, the cheaper and safer option
is to **leave history alone** and record the finding with its evidence. A force-push on a public
repository has its own costs and is not automatically the right answer.

## Status

**Open — owner action.** Not a blocker on the software; it is a blocker on being able to say the
public repository carries no personal information.
