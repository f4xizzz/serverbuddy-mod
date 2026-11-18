# Changelog

## 1.1.0 — 2026-11-18

- Added the dashboard management foundation, offline player profiles, inventory and ender chest, party, PC and Pokédex views.
- Added visual configuration coverage, validation, search, review before saving, backup history and restore.
- Added Redeem, player tags and LuckPerms group management in the dashboard, with plan checks and audit records.
- Protected configuration passwords, connection strings and webhook addresses with server-side secret markers. Preserved existing credentials during edits and redacted outgoing logs, search results, errors and save reviews.
- Added filtered recent and live logs and clickable dashboard pairing codes and links.
- Fixed trivia answers blocked by duplicate-message checks, redeem permissions and optional expiry, and Catch Combo leaderboard holograms returning after `/sb purgedisplays`.
- Improved trial-network diagnostics and administration in the companion website.

Minecraft 1.21.1, Fabric, Java 21. Cobblemon remains optional. Install the new JAR on the game server and restart it; remove the previous ServerBuddy JAR from `mods/` first. Dashboard management requires the matching website update. Existing configuration files and data are preserved; the single-JAR model and five-day dashboard trial remain unchanged.

Validation: 281 mod tests (280 passed, one skipped), 373 website tests and 210 end-to-end bot checks. UI checked in Portuguese and English, light and dark themes and a 375 px viewport.
