# Persistent companion data

The companion resolves Windows LocalAppData, macOS Application Support, or Linux
XDG user data independently of its installation. GOOFY_BAZAAR_DATA_DIR permits an
explicit absolute location; scheduled Actions jobs set a workspace location.
Collector state, gameplay outcomes, collection status and Windows task logs share
this resolver. Startup and local health show the resolved directory.

Legacy recognized files are copied to temporary files in the destination, then
published with an exclusive hard link, so incomplete copies are not recognized as
migrated history and existing files are never replaced. Originals are retained.
Migration failures stop startup. Optional --migrate-from imports an old installation
without requiring it to be copied inside the new one. Destination folders must
support hard links for migration (the normal Windows NTFS, macOS and Linux user
filesystems do); unsupported custom filesystems produce an explicit migration error.

The first upgrade must migrate before the old folder is deleted. Later upgrades can
replace the installation freely. The runtime package excludes all collected data.

## Configuration scope

GoofyConfig still governs capital/reserve, tax, mode, order slots, action pacing,
repricing/holding/drawdown limits, storage page commands, keys and telemetry opt-in.
Configured route lists currently grant execution scope. PipelinePlanner is advisory
and explicitly defers unconfigured research routes. Removing route lists therefore
requires wiring a validated execution queue into both engines and recovery first.
A smaller settings surface can retain spending, reserve, risk and connection limits
while route discovery/ranking becomes automatic; deleting the file now would discard
controls that the current runtime still depends on. This storage change leaves
configuration intact.
