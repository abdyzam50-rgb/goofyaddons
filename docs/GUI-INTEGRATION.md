# A* GUI integration — 0.2.53

Source: [A* GUI update c2b54c5](https://github.com/abdyzam50-rgb/A-/commit/c2b54c525727f3665e3d2a92fa3065b8427651f7)
on `claude/project-thread-qf4zec`. A*'s default `main` branch still points to
`e7c5126`; the update was taken from its published feature branch.

The integration imports Pixel.java and the corresponding AstarScreen/AstarClient
changes: stone frame, dropdowns, switches, slider styling, theme-aware states,
version display, logo preview and the `dropdown` development test step. It keeps
GoofyAddons keybind capture, private-key masking, trading orchestration and
validated autosave. It does not import upstream test JARs or replace trading code.

## Settings navigation

Open G → Macros. Six tabs replace the long two-column settings wall:

| Tab | Controls |
| --- | --- |
| Overview | Trading status, start/stop, mode and keybinds |
| Limits | Trading capital, purse reserve, route slots and queued input buying |
| Crafts | Automatic craft limits, ranking and the separate single-item AH test |
| Calculator | Local service, dashboard, port, account checks and advanced reload |
| Rest | Regional timezone and login/logout windows |
| Connections | Shared gameplay enrollment and Discord settings |

Each tab keeps its own scroll position, and the last tab is remembered for the
current game session. Tabs wrap on narrow windows; trading cards use a single
column. Rows put controls below the description when labels cannot fit beside
them. Long status callouts wrap. Headings use the upstream tracked capitals;
body text, URLs and entered values keep their case. Search includes trading
control labels and help without indexing private field values.

Valid edits still save automatically. There is no permanent “No unsaved changes”,
“Settings saved”, Retry save or Discard panel. Pending/invalid edits and save
failures appear in the header; hover the message for the full explanation. Correct
the input or re-edit a value to retry a failed save. Closing the screen with
unsaved edits reports the problem in chat. Contributor-key enrollment keeps its
explicit Save sharing action and masked input.

## Validation

Run `test calculatorIntegrationTest build` from the client Gradle project. The
existing config, scheduler, production, private sharing and keybind checks stay
enabled. In-game rendering and mouse routing still need a Minecraft smoke test;
this cloud environment has no display/Xvfb runtime.

1. Open G → Macros, visit all six tabs, scroll each, and return to check its position.
2. Pick Book/Craft mode from the dropdown, use Escape to close an open dropdown,
   and toggle Rest while stopped. Reopen settings to confirm the saved values.
3. Type a calculator port, wait briefly, and confirm the dashboard uses that port.
   Enter an invalid timezone or a blank numeric value; check the header explanation
   and hover text, then correct it. Do not start trading with invalid pending edits.
4. Change a keybind and verify Escape cancels capture; paste a contributor key and
   confirm it is masked. Check that body text and URLs remain case-correct.
5. Check smaller game windows and multiple GUI scales: tabs must wrap, long labels
   must remain visible, and controls must stay within their card.
6. Search “rest”, “port” and “minimum profit” and check the matching trading controls.

## Status GUI and remade A* logo — 0.2.68

The status HUD uses the integrated A* `Pixel.panel`, `Theme.shown()` colour tokens
and `Ui.text` font. Theme changes apply to its surfaces, borders and text, while
explicit green/amber/red status roles remain readable. It wraps the current task
into up to three lines instead of repeating two truncated activity strings. A
cached blocking craft review produces a REVIEW badge, its ID and saved reason;
the full reason is also available in G → Macros → Overview. This is a presentation
state and does not clear or acknowledge journal records.

Craft mode replaces irrelevant zero Books/General rows with active craft slots
and strategy selection. Short layouts reserve warnings and task status before
the profit figure. Existing scale/side controls and clipping cleanup remain.
Overview separates the state callout from the activity description and adds an
Export debug button using the existing redacted ZIP exporter. Render-time status
reads use cached review evidence, not journal IO or full diagnostic snapshots.

The existing pixel GUI branch `c2b54c5` contains the old A logo. The remade asset
is imported separately from [4bf09c3](https://github.com/abdyzam50-rgb/A-/commit/4bf09c3ecd0421822e2b65532e6918f1278431b9)
on `claude/project-thread-dkp9f9`, along with its palette fix: only entry zero is
transparent; entry one is the explored trail. The packaged 24,659-byte asset was
compared byte-for-byte with that upstream revision. Upstream main still points to
the revert commit; no whole-branch merge or trading-code replacement was needed.

The full Java suite/build passes, including responsive-layout checks and a new
short-screen task-priority regression. In-game themes, readability, logo animation
and export button interaction still require a Minecraft smoke test; no live GUI
rendering was performed in this environment.
