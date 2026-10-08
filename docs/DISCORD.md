# Discord status and manual controls

This runs on the same PC as Minecraft, alongside the Bazaar calculator. Keep Minecraft and the calculator running. The webhook posts status and alerts; the Discord bot receives your commands. No Discord token is placed in the mod or shared gameplay dataset.

## 1. Create the Discord bot and webhook

1. Open https://discord.com/developers/applications and create an application.
2. Open **Bot**, create/reset its token, and keep that token private.
3. Install the bot into your Discord server. Give it **View Channel**, **Send Messages**, and **Read Message History** for one private channel. It does not need Administrator or the privileged Message Content intent; commands explicitly mention the bot.
4. In that channel's **Edit Channel → Integrations → Webhooks**, create a webhook and copy its URL.
5. Turn on Discord's **User Settings → Advanced → Developer Mode**. Right-click your user and the channel to copy their IDs. The configured user is the only person allowed to control Minecraft.

## 2. Pair Minecraft and the calculator

The bundled calculator starts with Minecraft and creates missing pairing files
locally. Use **G → Macros → Discord companion → Open settings**. Open the private
`discord-settings.json` file and fill in:

- `enabled`: `true`
- `botToken`: your Discord bot token
- `channelId`: the private channel's ID
- `ownerId`: your Discord user ID
- `webhookUrl`: that channel's webhook URL
- `statusSeconds`: posting interval, from 60 to 3600 seconds; default 300

Keep the generated `bridgeKey` unchanged. The same key is already in Minecraft's `config/goofyaddons-discord.key`. Existing pairing/settings files are never overwritten. If you already configured the companion for another instance, both keys must match.

The settings live in the persistent data folder, separate from the replaceable installation: `%LOCALAPPDATA%\GoofyAddons\bazaar-calc` on Windows, or your existing `GOOFY_BAZAAR_DATA_DIR` override. Keep `discord-settings.json` and the pairing key private; do not include them in diagnostics, Git commits or downloads you distribute.

Use **G → Macros → Market and account checks → Retry / restart** to reload the
calculator's private settings. If the status says **Using existing calculator**,
close that separately launched service once so the bundled service can take over.

In Minecraft, use **G → Macros → Discord companion → Enabled**. If Discord delivery is unavailable, the card reports that separately from local pairing.

Defaults pause trading on contact and enable mention/staff-message alerts. **Bridge: PAIRED** means the local connection is working.

## 3. Use the bot

In the configured Discord channel, mention the bot followed by a command. For example, select its actual Discord mention and type `status`:

| Command | Behavior |
| --- | --- |
| `@YourBot status` | Connected state, purse, confirmed session profit and actual coins/hour |
| `@YourBot start` | Start/resume trading in a connected SkyBlock world with menus closed |
| `@YourBot stop` | Stop trading and cancel scheduled/remote reconnects |
| `@YourBot logout` | Finish the current transaction, retain saved positions, then disconnect |
| `@YourBot login` | Reconnect to the server remembered from this Minecraft session and run normal recovery |
| `@YourBot chat Yes, I'm here.` | Send one plain in-game chat message |

Wait for acknowledgment before another player action. Requests expire after 30 seconds and are not replayed after a mod/bot restart. A new local stop or contact pause overrides older queued start/chat requests. Chat is limited to 256 characters and cannot execute slash or `.a*` commands.

For login, manually connect once after launching Minecraft so it knows your server. Login does not launch Minecraft, handle authentication prompts or reconnect after a ban/kick by itself. It uses the existing authenticated client, tries the configured SkyBlock join command if needed, and stops waiting after two minutes.

Logout waits up to a minute for a verified transaction boundary. It will not close a menu with an item on the cursor. If it cannot safely finish, it pauses and alerts you instead of forcing disconnect.

## Contact alerts and server transfers

Mention/direct-message alerts and messages with a staff rank in the sender prefix are sent through the webhook and can ping your configured Discord user. A rank alert is a message classification, not proof that a staff check occurred. With **Pause on contact** enabled, trading pauses for your review; send a response manually using `chat`, then send a fresh `start` when ready. There are no automatic chat replies, fake movement or staff-presence evasion routines.

When an active trader changes worlds during a server transfer, it waits for a readable SkyBlock scoreboard, purse and empty cursor to remain ready for five seconds. It then restarts normal inventory/storage/order recovery. Saved positions remain reserved; it does not repeat a pending purchase or claim merely because the menu disappeared. Manual stops and safety/contact blocks prevent automatic resumption. Disconnections remain stopped; no automatic reconnect is attempted for unexpected kicks.

Live Discord delivery, actual reconnects and in-game transfer behavior still need testing with your account. The automated tests cover authentication, stale/duplicate commands, owner/channel restrictions, chat policy, contact detection, and transfer readiness/stop precedence.
