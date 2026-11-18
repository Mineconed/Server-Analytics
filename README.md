# ServerAnalytics • Daily player reports in Discord
Made by Mineconed

Know when your server is busy. ServerAnalytics tracks your players every minute and posts a **daily report with a chart** to Discord.
Works on **1.8 to 1.21** (Spigot and Paper). No other plugins and no Discord bot needed, just a webhook.

## Features
- 📊 **Daily report** in Discord with a **bar chart** of players per hour
- 👑 Peak players and the time it happened
- 👥 Unique players, 🆕 new players and joins
- ⏱️ Total playtime and average session length
- 🔥 Busiest and quietest hour
- ⚡ Average and lowest TPS
- 🏆 Top 3 players with the most playtime
- ▲▼ Compared to the day before
- 📅 **Weekly report** with the best day
- 💬 `/analytics` shows today, yesterday or the last 7 days in game
- 🕵️ Vanished players are not counted
- 🌍 English and Danish built in, make your own `messages_xx.yml`
- 🪶 Light: one sample per minute, data saved as small yml files

## Setup
1. Put `ServerAnalytics.jar` in your plugins folder and start the server
2. In Discord: channel → Edit channel → Integrations → Webhooks → New Webhook → Copy Webhook URL
3. Paste it in `plugins/ServerAnalytics/config.yml` → `discord.webhook_url`
4. Set `server_name` and `timezone`, then `/analytics reload`
5. Test it with `/analytics report`

## Commands
| Command | |
|---|---|
| `/analytics` | Today's numbers (also `/sa`) |
| `/analytics yesterday` | Yesterday |
| `/analytics week` | Last 7 days |
| `/analytics report` | Send today's report to Discord now |
| `/analytics report week` | Send the weekly report now |
| `/analytics reload` | Reload config and messages |

## Permissions
| Permission | Default | |
|---|---|---|
| `serveranalytics.use` | op | Use /analytics |
| `serveranalytics.ignore` | false | Never counted (fx staff alts) |
