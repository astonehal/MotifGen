---
name: sdlc
description: >
  Full SDLC workflow for a GitHub issue. Walks through Requirements, Design,
  Implementation (TDD, Google Java Style), Testing (Playwright E2E), and Code Review
  phases — pausing at each phase for engineer approval before proceeding.
  Use when asked to work through an issue end-to-end, run the SDLC process,
  or implement a GitHub issue with full lifecycle management.
when_to_use: >
  Triggered when the user says "run sdlc", "sdlc workflow", "full lifecycle",
  "implement issue end-to-end", or invokes /sdlc with a GitHub issue reference.
argument-hint: <github-issue-number-or-url>
disable-model-invocation: true
allowed-tools: Bash(gh *) Bash(git *) Bash(./gradlew *) Bash(gradle *) Read Write Edit Grep Glob Agent AskUserQuestion WebFetch
effort: max
---

# SDLC Workflow

You are running a full Software Development Lifecycle workflow for a GitHub issue.
This is an **interactive, multi-phase process**. You MUST stop after each phase,
present your work to the engineer, and wait for explicit approval before moving on.
If the engineer requests corrections, incorporate them and re-present before proceeding.

The GitHub issue is: **$ARGUMENTS**

---

## Phase 1: Requirements

### Steps

1. **Read the GitHub issue** using `gh issue view`. Capture the title, body, labels, and any linked items.
2. **Check for spec files** — Read the spec file from the comments
3. **Explore the codebase** to understand the current state of the code relevant to this issue. Use Glob, Grep, and Read to understand the architecture, existing patterns, and relevant modules.
4. **Present the requirements** to the engineer in your own words:
   - A clear summary of what the issue is asking for
   - Any assumptions you are making
   - **Acceptance Criteria in Gherkin format** (`Given / When / Then` scenarios)
   - Any open questions or ambiguities you identified

5. **Ask the engineer** to confirm, correct, or add to the requirements using `AskUserQuestion`.

### After Approval

Once the engineer confirms the requirements:

1. **Add a comment to the GitHub issue** with the confirmed requirements (including the Gherkin acceptance criteria) using:
   ```
   gh issue comment <number> --body "<requirements>"
   ```
2. **Assign the issue** to the engineer. Determine the engineer's GitHub username from `gh api user` (the authenticated user) and run:
   ```
   gh issue edit <number> --add-assignee <username>
   ```
3. **Mark the issue as in progress** by adding an "in progress" label:
   ```
   gh issue edit <number> --add-label "in progress"
   ```
4. **Pull the latest code** from the main branch:
   ```
   git checkout main && git pull origin main
   ```
5. **Create a feature branch** named after the issue (e.g., `feature/<issue-number>-short-description`):
   ```
   git checkout -b feature/<issue-number>-<short-kebab-description>
   ```

Then proceed to Phase 2.

---

## Phase 2: Design

### Steps

1. **Check the GitHub wiki** for existing design documentation:
   ```
   gh api repos/{owner}/{repo}/pages 2>/dev/null
   ```
   Clone or fetch the wiki repo to read its contents:
   ```
   git clone <repo-url>.wiki.git /tmp/wiki-$(basename $(pwd)) 2>/dev/null || true
   ```
   Read any existing design documents from the wiki (look for architecture docs, design patterns, module overviews).

2. **If no design documentation exists** for the repository:
   - Analyze the codebase architecture, module structure, key patterns, and dependencies
   - Generate comprehensive design documentation covering: architecture overview, module descriptions, key design patterns used, data flow, and technology stack
   - Create a wiki page for it:
     ```
     cd /tmp/wiki-$(basename $(pwd))
     # Write the design doc and push
     git add . && git commit -m "Add project design documentation" && git push
     ```

3. **Design the solution** for this specific issue:
   - Describe which modules/classes/files will be modified or created
   - Explain the approach and rationale
   - Identify any new design patterns to be applied
   - Note any impacts on existing functionality
   - Include a simple component/sequence diagram in Mermaid or ASCII if helpful

4. **Present the design** to the engineer and ask for confirmation using `AskUserQuestion`.

### After Approval

Once the engineer confirms the design:

1. **Create a wiki page** for this issue's design:
   ```
   cd /tmp/wiki-$(basename $(pwd))
   # Write design page (e.g., "Design-Issue-<number>.md") and push
   git add . && git commit -m "Add design for issue #<number>" && git push
   ```
2. **Add a comment to the GitHub issue** with a link to the wiki design page:
   ```
   gh issue comment <number> --body "Design document: <wiki-page-url>"
   ```

Then proceed to Phase 3.

---

## Phase 3: Implementation

### Approach: Test-Driven Development (Test First)

Follow this cycle strictly:
1. **Write a failing test** that captures a requirement
2. **Write the minimum code** to make the test pass
3. **Refactor** while keeping tests green
4. Repeat for each requirement/acceptance criterion

### Standards

- **Google Java Style Guide**: Follow https://google.github.io/styleguide/javaguide.html strictly:
  - 2-space indentation (no tabs)
  - Column limit: 100 characters
  - Proper Javadoc on public APIs
  - Correct import ordering (static imports separated, no wildcard imports)
  - Braces follow K&R style
  - Use `@Override` where applicable
  - Follow naming conventions (camelCase for methods/variables, PascalCase for classes, UPPER_SNAKE for constants)

- **Security**: Actively avoid OWASP Top 10 vulnerabilities:
  - Validate and sanitize all inputs
  - Use parameterized queries (no string concatenation for SQL)
  - Encode output to prevent XSS
  - Use secure defaults for authentication/authorization
  - No hardcoded secrets or credentials

- **Code Smells**: Avoid:
  - Long methods (break down into smaller, focused methods)
  - God classes (follow Single Responsibility Principle)
  - Duplicate code (DRY principle)
  - Deep nesting (early returns, guard clauses)
  - Magic numbers/strings (use named constants)

- **Design Patterns**: Apply patterns where they genuinely fit:
  - Strategy, Factory, Builder, Observer, etc. where appropriate
  - Do NOT force patterns where they add unnecessary complexity

### Steps

1. Write tests first, then implementation code, following the TDD cycle above.
2. Run the full test suite after implementation to ensure nothing is broken:
   ```
   ./gradlew test
   ```
3. Run any linting or static analysis configured in the project.
4. **Commit the changes locally** (do NOT push yet):
   ```
   git add -A && git commit -m "Implement #<number>: <short description>"
   ```
5. **Present a summary** of all changes to the engineer:
   - Files created/modified
   - Key implementation decisions
   - Test coverage summary
   - Any deviations from the design and why

6. **Ask the engineer** to confirm using `AskUserQuestion`.

### After Approval

Once the engineer confirms:

1. **Add a comment to the GitHub issue** with the implementation summary:
   ```
   gh issue comment <number> --body "<implementation summary>"
   ```
2. **Push the code** to the feature branch:
   ```
   git push -u origin <branch-name>
   ```

Then proceed to Phase 4.

---

## Phase 4: Testing (End-to-End)

### Steps

1. **Create end-to-end tests** for the GitHub issue:
   - Tests should cover the acceptance criteria from Phase 1
   - Place tests in the appropriate test directory for E2E tests
   - Ensure tests are descriptive and follow the Given/When/Then structure from the Gherkin scenarios

2. **Integrate with Gradle build**:
   - Ensure the E2E tests are wired into the Gradle build lifecycle
   - They should be runnable via a Gradle task (e.g., `./gradlew e2eTest` or `./gradlew playwright`)
   - Verify they run correctly:
     ```
     ./gradlew <e2e-test-task>
     ```

3. **Present a summary** to the engineer:
   - List of E2E test files created
   - What each test covers
   - How to run them
   - Test results

4. **Ask the engineer** to confirm using `AskUserQuestion`.

### After Approval

Once the engineer confirms:

1. **Commit and push** the E2E tests:
   ```
   git add -A && git commit -m "Add E2E tests for #<number>" && git push
   ```
2. **Add a comment to the GitHub issue** with a summary of the tests created:
   ```
   gh issue comment <number> --body "<test summary>"
   ```

Then proceed to Phase 5.

---

## Phase 5: Code Review

### Steps

1. **Create a GitHub Pull Request** from the feature branch to main:
   ```
   gh pr create --base main --head <branch-name> --title "<PR title>" --body "<PR body>"
   ```
   The PR body should include:
   - Summary of changes
   - Link to the GitHub issue (closes #<number>)
   - Link to the design wiki page
   - Test coverage notes
   - Screenshots or examples if applicable

2. **Present an overall summary** to the engineer covering the entire SDLC journey:
   - Requirements recap
   - Design decisions
   - Implementation highlights
   - Test coverage
   - PR link

3. **Ask the engineer** for final confirmation using `AskUserQuestion`.

### After Approval

Once the engineer confirms:

1. **Add a final comment to the GitHub issue** with the overall summary and a link to the PR:
   ```
   gh issue comment <number> --body "<overall summary with PR link>"
   ```

---

## Important Reminders

- **NEVER skip a phase or combine phases.** Each phase must be completed and approved individually.
- **ALWAYS use `AskUserQuestion`** to get explicit engineer approval at the end of each phase.
- **If the engineer rejects or requests changes**, incorporate ALL feedback and re-present the phase before proceeding.
- **Keep the GitHub issue updated** with comments at each phase transition.
- **Parse the issue argument flexibly**: accept `#123`, `123`, or a full GitHub issue URL.
- **If any step fails** (e.g., wiki doesn't exist, tests fail), diagnose the issue, explain it to the engineer, and propose a solution before continuing.
