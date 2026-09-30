# Storm Project Zomboid Mods

### Setup local.properties

1. Create a new file in the repo named local.properties
2. Specify these two required directories

* gameDir - Project Zomboid Installation directory
* zomboidDir - Project Zomboid configuration directory

```
gameDir=E:\\SteamLibrary\\steamapps\\common\\ProjectZomboid
zomboidDir=C:\\Users\\user\\Zomboid
```

Deploy the mods locally:

Linux:
```
./gradlew deployMod
```

Windows:
```
.\gradlew.bat deployMod
```

### Private server jars

Some mods keep their server logic out of the workshop. `pvp-raids` builds two jars from one
project: `pvp-raids.jar` from `src/main` (shared wire format + client code, uploaded with
`uploadWorkshop` as usual) and `pvp-raids-server.jar` from `src/server` (server-only code,
never uploaded). The server loads both from the same class loader, so `src/server` may
reference `src/main` but not the reverse.

Deploy the server jar into the dedicated server's mods directory:

```
./gradlew :pvp-raids:deployServerMod
```

It lands in `<serverZomboidDir>/mods/pvp-raids-server-b42<env>/42/`. `serverZomboidDir` is an
optional `local.properties` entry and defaults to `~/Zomboid`. Storm loads everything under
`~/Zomboid/mods` without a `Mods=` entry, so nothing in the server ini changes. Ship the
workshop upload and the server deploy together: an old workshop jar still carrying the server
classes next to the new server jar registers the server handlers twice.
