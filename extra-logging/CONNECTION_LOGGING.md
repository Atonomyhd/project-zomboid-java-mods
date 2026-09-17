# Connection snapshot robustness

Non-Steam clients can return a missing or empty Steam ID. The shared log prefix now
records `non-steam` alongside the username, while preserving real Steam IDs.

The connection snapshot removes its temporary OnTick callback before invoking the
logger. If a downstream formatter fails, the error is reported once and does not
repeat every frame. The callback still waits until the player object exists.

Run the focused regression from the repository root with Lua 5.1:

```sh
lua extra-logging/tests/ConnectionLogging.lua
```

The fixture checks missing/empty/real Steam IDs, delayed player creation, callback
removal before logging and an injected formatter failure. This prevents a specific
logging error loop; it is not a demonstrated fix for high-population admin crashes.
