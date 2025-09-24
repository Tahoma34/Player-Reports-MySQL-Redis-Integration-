# Player-Reports-MySQL-Redis-Integration-

A lightweight and efficient report system plugin for Minecraft servers that uses **MySQL** and optionally **Redis** to handle player reports and track player online status in real time.

---

## 📌 Features

- `/report <player> <reason>` — Allows players to report others for misconduct.
- `/reports` — Admin GUI to view, manage, and moderate player reports.
- Admin Subcommands:
  - `/reports remove <id>` — Remove a specific report.
  - `/reports removeall` — Clear all reports.
  - `/reports reload` — Reload the plugin configuration.

---

## ⚙️ How It Works

### 🗄️ MySQL (Required)
- All report data is stored and retrieved using a MySQL database.
- Connection pooling is handled via **HikariCP** for high performance and reliability.

### 🧠 Redis (Optional)
- Redis is used for fast and lightweight online/offline player tracking:
  - When a player **joins**, a key is set to `1` in Redis.
  - When a player **leaves**, the key is removed.
  - This enables:
    - Real-time display of player online status in the report GUI.
    - Filtering or sorting reports by online players.

> 🔸 Redis is **not required** for the plugin to function.  
> If Redis is not available, the plugin gracefully disables online status features, and all other functionality remains fully operational.

---

## ⚡ Performance

All **database** and **Redis** operations are executed **asynchronously**, ensuring that the main server thread remains unaffected.

This makes the plugin:
- Safe to use on production servers
- Scalable and responsive under high load
- Optimized for performance

---

## 💡 Why Use This Plugin?

This plugin demonstrates a practical and modern architecture using:

- ✅ **Persistent storage** (MySQL) for core data
- ✅ **Optional in-memory caching** (Redis) for real-time features
- ✅ **Asynchronous programming** to maintain performance

It’s a clean and modular example for developers looking to integrate external services like MySQL and Redis into Minecraft plugins.

---

## 🧑‍💻 Author

**Tahoma34 (_xyd0jnik)**

---

## 📦 Technical Details

- **API Version:** `1.16+`
- **MySQL:** Required  
- **Redis:** Optional  
- **Connection Pooling:** HikariCP  
- **Threading:** Fully asynchronous operations

---



