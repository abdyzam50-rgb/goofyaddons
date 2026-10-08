# Website overlay

`ProfileLookup.tsx` adds optional read-only username/profile import to the complete
upstream calculator. `../build-website.mjs` applies it to a pinned upstream
checkout and produces `../calculator/`, including licenses and provenance.
The username, profile selector and budgets use calculator state only; they cannot
start the trader or mutate its saved orders.
