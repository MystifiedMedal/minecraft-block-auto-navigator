# Minecraft Automated Agent (Forge 1.8.9)

This project is an experimental **automated agent** built for Minecraft minigames such as **Block Party / Pixel Party**.  
It explores real-time decision-making in a live, event-driven environment under strict tick and latency constraints.

The project began as an accessibility-focused navigation aid and gradually evolved into a more general automated system for experimentation and self-challenge.

This is a **proof of concept**, not a competitive gameplay tool.

---

## Origin & Evolution

The original idea was simple:  
provide directional guidance (arrows) to help visually impaired players orient toward the correct block.

Over time, additional features were added:
- automatic target detection
- continuous correction under timing changes
- increasingly autonomous movement logic

Eventually, the project became an automated agent capable of challenging me on my own test servers, turning it into a sandbox for studying autonomous behavior rather than an assistive overlay alone.

---

## Core Ideas Explored

This project focuses on:
- event-driven logic in a real-time environment
- per-tick **sense → decide → act** control loops
- handling noisy game state and network latency
- translating abstract targets into concrete player movement

Minecraft minigames provide a constrained but non-trivial environment for testing these ideas.

---

## How It Works (High Level)

Every game tick, the agent runs a simple control loop:

1. **Sense**  
   Read the relevant game state (e.g. target block or color for the current round).

2. **Decide**  
   Compute the optimal movement direction based on the player’s current position and the detected target.

3. **Act**  
   Issue movement inputs to guide the player.

4. **Correct**  
   Continuously re-evaluate assumptions to adapt to timing shifts, latency, or incorrect detections.

This reactive approach avoids precomputed paths and allows real-time adaptation.

---

## Build & Setup (From Source)

### Requirements
- Java **JDK 8**
- Git
- Internet connection (Gradle will download dependencies)

### Build Steps

```bash
git clone https://github.com/MystifiedMedal/minecraft-automated-agent.git
cd minecraft-automated-agent
```

Linux / macOS
```bash
./gradlew setupDecompWorkspace
./gradlew build
```

Windows
```bash
gradlew.bat setupDecompWorkspace
gradlew.bat build
```

After a successful build, the compiled mod will be located in:

```bash
build/libs/
```
Copy the generated .jar file into your Minecraft Forge 1.8.9 mods folder.


## Prebuilt JAR (Optional)

For convenience, prebuilt .jar files may be provided under Releases.

These are intended for:

- testing

- experimentation

- private servers

- educational use


[Download here](https://github.com/MystifiedMedal/minecraft-automated-agent/releases/download/jar/BlockPartyHelper-1.93.jar)


### Testing Environment:

Primarily tested on private servers

Also tested on a friend's server with explicit permission

Not designed for use on competitive public servers without consent
If you are banned or penalized for using this mod on public servers, that responsibility is yours.
