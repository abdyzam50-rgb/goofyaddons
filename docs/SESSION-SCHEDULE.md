# Scheduled rest and reconnect (mod 1.3.54)

This is a daily local-clock schedule. Choose a region and login/logout ranges;
the mod chooses one minute inside each range every day. Two play windows use
eight clock values: login range, logout range, login range, logout range.
You can configure one to four play windows. It is disabled by default.

Minecraft and the PC must remain running during rest. The mod disconnects from
the server and reconnects using the existing Minecraft session; it does not
close/relaunch the game, wake the PC, or store Microsoft credentials. Keep the
Node companion running too if you want uninterrupted public market collection.

## Configure

Install mod 1.3.54. Keep companion 1.3.53. Close Minecraft, or cancel the schedule
and stop trading with K, then add this object to the existing
`config/goofyaddons.json`. Preserve the rest of your config and its JSON commas.

```json
"restSchedule": {
  "enabled": true,
  "timeZone": "America/New_York",
  "windows": [
    {
      "loginFrom": "08:00",
      "loginUntil": "09:00",
      "logoutFrom": "12:00",
      "logoutUntil": "13:00"
    },
    {
      "loginFrom": "16:00",
      "loginUntil": "17:00",
      "logoutFrom": "22:00",
      "logoutUntil": "23:00"
    }
  ]
}
```

That example runs two sessions. The first starts sometime from 08:00 to 09:00
and ends sometime from 12:00 to 13:00. The second starts from 16:00 to 17:00
and ends from 22:00 to 23:00. All times use the selected region's 24-hour clock.
The endpoints are included, and a range with identical endpoints uses that
exact minute. These are clock times, not durations or counts of hours.

Use region names so daylight saving follows the region:

| Region | `timeZone` |
| --- | --- |
| Eastern US/Canada | `America/New_York` or `America/Toronto` |
| Central US | `America/Chicago` |
| Mountain US | `America/Denver` |
| Pacific US | `America/Los_Angeles` |
| UK | `Europe/London` |
| Central Europe | `Europe/Berlin` |
| India | `Asia/Kolkata` |
| Japan | `Asia/Tokyo` |
| UTC | `UTC` |

EST/EDT, CST/CDT, MST/MDT and PST/PDT are accepted as convenience aliases for
those US regions, including daylight saving. Prefer the full region name,
especially for regions that do not observe daylight saving.

Sessions may cross midnight: login 22:00–23:00 and logout 02:00–03:00 means
logout the following morning. Individual login or logout ranges cannot cross
midnight. Reserve disjoint windows, including their entire ranges and overnight
continuations. Logout must follow the latest possible login. Overlapping or
malformed schedules are rejected by configuration validation.

At spring-forward, nonexistent times resolve to the first valid instant after
the skipped hour. If that collapses an entire window to zero duration, that
window is skipped for the day. At autumn's repeated hour, the first occurrence
is used consistently. The stored seed makes daily choices reproducible through
restarts; changing the schedule can change those choices.

## Arm and control

Connect manually to your multiplayer SkyBlock server, then press J to start.
With scheduling enabled, J also arms the schedule. You can alternatively run:

```
/goofyschedule on
```

If it is currently a rest period, the mod winds down and disconnects. If it is
a play period, it runs until that day's chosen logout time. The schedule follows
the configured clock periods; it does not give you a fresh multi-hour session
simply because you started it late.

View state and the next scheduled change:

```
/goofyschedule
```

Press K or run `/goofyschedule off` to stop trading and cancel automatic
reconnect. While disconnected, the rest screen has a cancellation button and
Escape also cancels. Cancelling a connection back to the rest screen cancels
retries. Manual connection during rest overrides and disarms the schedule.

After restarting Minecraft, connect manually and press J again to arm it.
A fresh client launch never automatically logs in. The chosen daily times are
preserved by `config/goofyaddons-session-schedule.json`; retain it when updating.
Editing schedule settings while armed cancels the old schedule. Reload with
`/goofyreload` while stopped, then explicitly rearm it.

## Logout and recovery

At logout time, new menu work stops being selected. The transaction already in
progress may finish. Disconnect requires a verified boundary, an empty cursor
and no unsettled purchase debit. The mod saves retained positions and ownership
before disconnecting. It does not liquidate every order merely because a rest
starts. If no verified boundary is reached within three minutes, it pauses for
review instead of forcing a disconnect with uncertain items.

After rest, the mod reconnects to the same server. It waits for a world, joins
SkyBlock if needed, sends `is`, and waits for a stable readable SkyBlock purse
before starting the existing journal/inventory/order recovery process. It
preserves your selected trading mode. Paused/offline time is excluded from the
active-time profit metric, and interrupted trade timings remain ineligible.

Reconnect attempts are bounded: three attempts, a 90-second connection timeout
and a 60-second retry delay by default. A still-running connection is not
replaced with a parallel connection. World readiness also has a timeout. A
failure pauses for manual review; the schedule never automatically clears a
trading safety block. Unexpected disconnects are not treated as scheduled rests
and do not authorize automatic reconnect.

Optional fields within `restSchedule`:

```json
"reconnectAttempts": 3,
"reconnectDelaySeconds": 60,
"connectionTimeoutSeconds": 90,
"transactionWaitSeconds": 180,
"joinCommand": "skyblock",
"resumeCommand": "is"
```

Commands omit the leading slash. They run only during a scheduled reconnect.
Account unlocks, server availability, session authentication, and actual menu
recovery still need to work. This release has deterministic timing/reconnect
tests and a compiled client integration; a live Hypixel rest/reconnect must be
verified on your account before relying on long unattended sessions.
