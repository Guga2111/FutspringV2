# Security review workflow

This is the required author/reviewer workflow for security-relevant pull requests into `dev` and `main`, including release PRs. It is a review and remediation gate, not an automated CI job or a certification that the application has no vulnerabilities. The PR template records compliance; it does not enforce branch protection by itself.

## When the review is mandatory

Every PR records `Security impact: yes/no` with a concrete reason. Use Trail of Bits **`differential-review`** whenever a change can affect a trust boundary or a security invariant, including:

- Authentication, JWTs, refresh cookies, passwords, verification/recovery links and account lifecycle.
- Authorization, membership, admin/finance/platform privileges, parent-child resource scoping and tenant isolation.
- API/STOMP inputs, validation, uploads/file delivery, personal data, exports, logs/telemetry and browser persistence.
- Financial writes, transaction ordering, concurrency, idempotency and integrity constraints/migrations.
- CSP, CORS, CSRF, redirects, service workers, WebSocket subscriptions and external requests/SSRF protections.
- Secrets, dependencies or lockfiles, reverse proxies, Docker, deployment, CI permissions and observability access.
- Security fixes and refactors or tests that change any of the above behavior, even if the diff is small.

Formatting, documentation-only changes and presentation-only changes with no security behavior impact may be marked `no`, with the reason. If impact is uncertain, perform the review. Never waive a review merely because CI passes or the code was reviewed previously.

## Review, fix, verify

1. **Identify the source.** Read `AGENTS.md`, `client/docs/FRONTEND.md` and `core/docs/BACKEND.md`. Record the target branch, its current SHA, the merge base and the proposed head SHA, plus the skill revision. For a release, review the complete release diff against its target; prior feature reviews are supporting evidence, not a replacement.
2. **Run the skill.** Load the installed `differential-review/SKILL.md` and its relevant methodology, adversarial and reporting references. Understand the baseline invariants, inspect both versions, inspect history/blame of removed guards, trace callers and downstream effects, and check test gaps. Use a disposable worktree or `git show` for baseline inspection; do not disturb another worktree's changes.
3. **Validate candidates.** Trace input and actor through existing validation and framework/configuration controls to the affected resource. Record source/line references, preconditions, concrete impact and minimal evidence. Separate newly introduced issues, regressions and pre-existing issues. An authenticated attacker can still exploit IDOR or cross-pelada access: requiring login alone is not a dismissal. Separate confirmed vulnerabilities from hardening advice and candidates needing validation; do not guess production configuration.
4. **Fix the root cause.** Correct confirmed vulnerabilities in changed code and its reachable related flows before merge, whether newly introduced or pre-existing. Use the responsible trusted layer and the existing architecture. Fix them in this PR or in a prerequisite fix PR merged first; do not expand an unrelated feature patch indefinitely. Keep API contracts/docs in sync and include a migration for schema changes. Record unrelated findings separately with owner, priority and a remediation target; critical/high vulnerabilities block the affected release until fixed. No silent deferrals or risk acceptance by the agent.
5. **Prove the fix.** Add a focused regression that fails before the fix and passes after it whenever feasible. Cover unauthorized actors, parent-child/tenant boundaries and relevant races/replays. Run the project's required checks for the changed code. When a proof cannot run, record exactly what remains unresolved; an unexecuted test is not a passing test. Rerun the affected skill phases on the resulting code to catch incomplete fixes and new regressions.
6. **Record and recheck.** Generate the skill's Markdown report even when there are no confirmed findings. Include reviewed refs, scope/coverage, findings/dispositions, tests and limitations. Before merge, confirm that head and target refs still match the recorded review. If either changed, update the comparison and rerun the affected analysis and tests; unrelated changes need a documented impact assessment, not an automatic full audit. Finish with an explicit merge recommendation.

The skill must be used before a security-relevant PR is opened or marked ready. If a PR is already open, complete the review before marking it ready or merging it. Fixes and subsequent security-relevant changes invalidate the old approval for the affected flows.

## Merge and release gate

- Do not merge with confirmed vulnerabilities unresolved in the reviewed scope. A separate fix PR counts only after it has merged and the current diff has been rechecked.
- Unresolved validation of authentication, authorization, credential revocation, private-data access or financial integrity blocks a claim of readiness until the missing evidence is obtained.
- Critical/high findings elsewhere in the application must be tracked and fixed before the affected release; they cannot disappear from the report because they predate the PR.
- The PR states the skill and revision, reviewed base/merge-base/head SHAs, sanitized result, regression/check results, report reference and any remaining limits. A designated reviewer checks this evidence before merge. Never describe a scoped clean review as proof that the whole system is secure.
- If the skill is missing, install it from the official source or report the blocker. Do not replace the required review with a generic checklist or invent a passing result.

## Safe evidence and reports

Use synthetic users, peladas, money and secrets for bounded local reproductions. Do not probe production, other users' data or shared services as part of this review. Follow the invoked skill's execution restrictions; do not weaken them merely to run a test.

Store full reports outside the checkout or in the ignored `.security-reviews/` directory. Give maintainers a restricted report reference; keep secrets, real personal data, private artifacts and unnecessary exploit detail out of tracked files and public PRs/issues. Record tracking references locally and obtain explicit authorization before publishing sensitive findings externally. Use a sanitized PR summary rather than committing the full report.

Cloudflare's **`security-audit`** is available for an explicitly requested broader audit. Its workflow is read-only with respect to target source; remediation follows separately. Its target-code execution requires the documented OS sandbox, network isolation, allowlisted environment and resource limits. If those controls are unavailable, retain the appropriate validation blocker instead of executing target code unsafely. It complements, rather than replaces, the per-PR Trail of Bits review.

## Installed skills and provenance

Installed user-wide for Codex on 2026-11-13, under `$CODEX_HOME/skills` (default `~/.codex/skills`). The original skill instructions and supporting files are installed unchanged; they are not vendored into this repository.

| Skill | Official source | Installed revision |
|---|---|---|
| `differential-review` | [Trail of Bits](https://github.com/trailofbits/skills/tree/149d7b21f4067765b82e67029730b42d228ef4b4/plugins/differential-review/skills/differential-review) | `149d7b21f4067765b82e67029730b42d228ef4b4` |
| `security-audit` | [Cloudflare](https://github.com/cloudflare/security-audit-skill/tree/c1c8a8c1471069fb0e188eeaff69b8e8db6564a8/skills/security-audit) | `c1c8a8c1471069fb0e188eeaff69b8e8db6564a8` |

To install the same reviewed versions with the Skills CLI:

```bash
bunx skills add https://github.com/trailofbits/skills/tree/149d7b21f4067765b82e67029730b42d228ef4b4/plugins/differential-review/skills/differential-review --skill differential-review --agent codex --global
bunx skills add https://github.com/cloudflare/security-audit-skill/tree/c1c8a8c1471069fb0e188eeaff69b8e8db6564a8/skills/security-audit --skill security-audit --agent codex --global
```

Other contributors and CI agents need their own installation; this machine's global skills are not automatically available elsewhere. Review upstream instruction/script changes before upgrading, then update this provenance table. Never download a similarly named third-party fork as a substitute for the official skill.
