# FrontierContracts

FrontierContracts is a Paper/Spigot plugin for physical settlement supply boards.

Players walk to a real board structure in the world, inspect public supply requests, bring the requested material, and right-click the board to deliver items directly from the hand they are using. Rewards are paid immediately on completion.

## Core behavior

- Settlement boards are physical structures in the world.
- A valid board is a centered 3x2 wooden plank surface on a fence post, with front wall signs facing the board front.
- The top-center header sign must read `contact`.
- A linked bell must be within 13 blocks.
- Optional stricter validation can require a villager bed or job-site block near that bell via `boards.require-village-poi-near-bell`.
- Empty-hand right-click opens the board GUI.
- Right-click with a resource item attempts delivery first.
- Local board tasks are public by default and support partial turn-in.
- Hybrid non-local offer flows remain available through `/contracts`.

## Admin commands

- `/contracts admin scanboard <id> <type> <settlement name...>`
- `/contracts admin placeboard <id> <type> <settlement name...>`
- `/contracts admin validateboard <id>`
- `/contracts admin board list`
- `/contracts admin board refresh <id>`
- `/contracts admin board reputation <uuid> <board> <delta>`

## Files

- `config.yml`: cleanup, reward, bell, refresh, and UI settings
- `contracts.yml`: default delivery templates
- `boards.yml`: registered physical boards
- `messages.yml`: chat and GUI text

## Build

Run:

```powershell
./gradlew compileJava
```
