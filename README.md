# Player-Reports-MySQL-Redis-Integration-
A lightweight and efficient report system plugin for Minecraft servers that uses MySQL and Redis to handle player reports and online status tracking.


📌 Features

/report <player> <reason> — Allows players to report others for misconduct.

/reports — Admin GUI to view, manage, and moderate player reports.

Subcommands:

/reports remove <id> — Remove a specific report.

/reports removeall — Clear all reports.

/reports reload — Reload plugin configuration.

⚙️ How It Works

MySQL (via HikariCP Pooling) is used to store and manage all report data.

Redis is used to efficiently track player online/offline status:

When a player joins the server, a Redis key is set to 1.

When the player leaves, the key is removed.

This allows the plugin to:

Show real-time online/offline status in the GUI.

Filter/sort reports based on whether the reported player is currently online.

💡 Why This Matters

This plugin serves as a practical example of combining persistent storage (MySQL) with in-memory caching (Redis) to build a performant and scalable Minecraft plugin.

🧑‍💻 Author

Tahoma34 (_xyd0jnik)

📦 Technical Info

API Version: 1.16+

Dependencies: Redis, MySQL (HikariCP connection pool)
