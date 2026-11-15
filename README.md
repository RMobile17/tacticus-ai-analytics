# Tacticus AI Analytics & NotebookLM Knowledge Base Exporter

[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.8%2B-blue.svg)](https://maven.apache.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

An analytics and knowledge base generation tool for **Warhammer 40,000: Tacticus**. This project combines comprehensive static game metadata (characters, Machines of War, Guild Raid bosses and primes, factions, traits, abilities, ranks, equipment, and summons) with personal player progression exported via the official **Tacticus API**.

The tool generates two structured, Markdown-formatted knowledge bases optimized for **Google NotebookLM**:
- **`tacticus_notebooklm_kb.md`** — your personal roster, unlocked/locked characters, Machines of War, combat stats, abilities, traits, and factions.
- **`tacticus_bosses_kb.md`** — Guild Raid bosses, linked primes, stat scaling across rarities, boss abilities, and prime-kill debuffs.

---

## Features

- **Automated API Integration (`TacticusApiDownloader`)**:
  - Connects to the official Snowprint Tacticus API (`api.tacticusgame.com`).
  - Securely downloads player roster data, including unlocked characters and Machines of War, ranks, stars, shards, mythic shards, ability levels, equipped upgrades, and inventory items.
- **Roster & MoW Knowledge Base Generator (`TacticusKnowledgeGenerator`)**:
  - Joins static game metadata (`data/heroes/`, `data/xref/`, `data/game-data/abilities/`, `data/items.json`) with your live player profile.
  - Separates **Characters** (`SECTION 1`) and **Machines of War** (`SECTION 2`), followed by full **Traits** (`SECTION 3`) and **Factions** (`SECTION 4`) references.
  - Supports **Primary**, **Secondary**, and **Mythic** abilities (including locked/level-0 mythic abilities with min–max scaling) and resolves dynamic formula variables (`{[minDmg]}`, `{[maxDmg]}`, `{[dmg[0]]}`, rarity multipliers).
  - Calculates current character combat stats based on rarity, star level, rank, applied rank upgrades, and equipped gear.
  - Displays both **Shards** and **Mythic Shards** in the roster overview and individual unit entries.
- **Guild Raid Bosses Knowledge Base Generator (`TacticusBossKnowledgeGenerator`)**:
  - Parses Guild Raid boss and prime definitions from `data/guild-raid/bosses/`.
  - Links each boss to its associated primes (`prime1`, `prime2`), weapon profiles, traits, and stat scaling tables across rarity tiers.
  - Resolves boss active/passive abilities (`xref/boss_abilities.generated.json` and `data/game-data/abilities/`) and prime-defeat debuff modifiers (`data/guild-raid/boss_debuffs.json`).
- **Google NotebookLM Optimization**:
  - Clean Markdown hierarchy tailored for NotebookLM's context window and grounded multi-source reasoning.
  - Supports filtering to unlocked units only (`--unlocked-only`) or exporting the full roster for theorycrafting.
- **Privacy & Security First**:
  - Strict `.gitignore` rules prevent accidental commits of personal API keys (`api_key.txt`) or personal account exports (`data/my_*.json`).

---

## Prerequisites

- **Java Development Kit (JDK)**: Version 17 or higher (Java 21 supported).
- **Apache Maven**: Version 3.8 or higher.
- **Google Account**: For access to [Google NotebookLM](https://notebooklm.google.com).
- **Warhammer 40,000: Tacticus Account** with a linked **Snowprint ID**.

---

## How to Create a Tacticus API Key

To download your personal roster, you need an API key from Snowprint Studios:

1. **Visit the Tacticus API Portal**:
   - Navigate to [https://api.tacticusgame.com/](https://api.tacticusgame.com/) (or [https://api.tacticusgame.com/settings](https://api.tacticusgame.com/settings)).
2. **Log In with Snowprint ID**:
   - Sign in using your **Snowprint ID** credentials linked to your in-game account.
   - *Note:* Make sure to use your **Snowprint ID**, not your Tactician Club login.
3. **Generate an API Key**:
   - Click **Create Key** (or **Generate New Key**).
   - Configure the required **Scopes**:
     - `Player` (**Required**): Grants read-only access to your unlocked characters, Machines of War, upgrades, ability levels, inventory, and account stats.
     - `Guild` (**Optional**): Grants access to guild member data (requires *Leader* or *Co-Leader* role in your guild).
     - `Guild Raid` (**Optional**): Grants access to guild raid bosses and season progress (requires *Leader* or *Co-Leader* role).
4. **Configure the Key in the Project**:
   - Copy the generated API key.
   - In the root folder of this project, make a copy of `api_key.txt.example` named `api_key.txt`.
   - Paste your key into `api_key.txt` (single line, no extra spaces).
   - *Alternative:* You can set the environment variable `TACTICUS_API_KEY=<your-key>`.
5. **Security Notice**:
   - The API key provides read-only access to your public profile and roster.
   - Treat your API key like a password. Never commit `api_key.txt` to any public repository!

---

## Static Game Data Setup

This project uses static game metadata for unit base statistics, ability level matrices, Guild Raid encounters, traits, items, and upgrade trees.

- Static files are located in the `data/` folder:
  - `data/heroes/` — static character base stats and rank upgrade trees.
  - `data/xref/` — cross-reference tables (`faction_heroes.json`, `hero_abilities.json`, `trait_heroes.json`, `boss_abilities.generated.json`, display metadata).
  - `data/game-data/abilities/` — full ability definitions (`active.json`, `passive.json`, `other.json` for mythic and summon abilities).
  - `data/guild-raid/` — Guild Raid boss/prime configurations (`bosses/`) and prime-kill debuffs (`boss_debuffs.json`).
  - `data/items.json` — equipment stats and upgrade levels.
- Special thanks to [@thetimmyman](https://github.com/thetimmyman) and the [tacticus-analytics](https://github.com/thetimmyman/tacticus-analytics) community for compiling and maintaining the static game database.

---

## Usage

You can run the project either using the provided Windows batch scripts or standard Maven commands.

### Option A: Windows Batch Scripts

1. **Download Player Data**:
   ```cmd
   download.bat
   ```
   Runs `mvn compile exec:java -Pdownload`. Downloads your profile to `data/my_player_data.json`.

2. **Generate Roster & MoW Knowledge Base**:
   ```cmd
   generate.bat
   ```
   Runs `mvn compile exec:java -Pgenerate`. Generates `tacticus_notebooklm_kb.md` in the project root.

3. **Generate Guild Raid Bosses Knowledge Base**:
   ```cmd
   bosses.bat
   ```
   Runs `mvn compile exec:java -Pgenerate-bosses`. Generates `tacticus_bosses_kb.md` in the project root.

---

### Option B: Maven CLI (Cross-Platform)

1. **Download Player Data**:
   ```bash
   mvn compile exec:java -Pdownload
   ```
   Downloads your profile to `data/my_player_data.json`.

2. **Generate Roster & MoW Knowledge Base (All Units)**:
   ```bash
   mvn compile exec:java -Pgenerate
   ```
   Generates `tacticus_notebooklm_kb.md` in the project root.

3. **Generate Roster & MoW Knowledge Base (Unlocked Units Only)**:
   ```bash
   mvn compile exec:java -Pgenerate -Dexec.args="--unlocked-only"
   ```

4. **Generate Guild Raid Bosses Knowledge Base**:
   ```bash
   mvn compile exec:java -Pgenerate-bosses
   ```
   Generates `tacticus_bosses_kb.md` in the project root.

5. **Custom Output File Name (Optional)**:
   Both generators support `--output=<filename>`:
   ```bash
   mvn compile exec:java -Pgenerate -Dexec.args="--unlocked-only --output=my_unlocked_roster.md"
   mvn compile exec:java -Pgenerate-bosses -Dexec.args="--output=raid_bosses.md"
   ```

---

## Using with Google NotebookLM

Once `tacticus_notebooklm_kb.md` and `tacticus_bosses_kb.md` are generated:

1. Open [Google NotebookLM](https://notebooklm.google.com/).
2. Create a new notebook (e.g., *"Warhammer 40k: Tacticus Roster & Strategy"*).
3. Click **Add Source** -> **Upload File**, and upload both `tacticus_notebooklm_kb.md` and `tacticus_bosses_kb.md`.
4. Once processed, NotebookLM has complete, grounded context of your exact roster, Machines of War, Guild Raid bosses/primes, and game mechanics.

### Example Prompts for NotebookLM

- **Guild Raid Team & Machine of War Optimization**:
  > *"Using my unlocked characters, Machines of War, and the Guild Raid boss/prime mechanics in the sources, suggest the top 2 teams (including the best Machine of War and prime-kill priority) for Ghazghkull Thraka."*

- **Legendary Release Event (LRE) Planning**:
  > *"Analyze my unlocked roster against typical LRE track restrictions (e.g., Physical damage only, Piercing only, 3+ hits). Which characters fulfill the most criteria simultaneously and should be leveled up next?"*

- **Investment & Ascension Prioritization**:
  > *"Review my roster, shards, and mythic shards to identify the top 3 units that will gain the largest power spike if ascended or if their primary/passive ability is pushed higher."*

- **Synergy & Mechanic Analysis**:
  > *"Find all synergistic combinations in my roster centered around Mechanical units, Actus' repair mechanics, and Exitor-Rho's passive extra attacks."*

---

## Project Structure

```text
tacticus-ai-analytics/
├── pom.xml                                        # Maven configuration ('download', 'generate', 'generate-bosses')
├── download.bat                                   # Shortcut to download player data
├── generate.bat                                   # Shortcut to generate the roster/MoW knowledge base
├── bosses.bat                                     # Shortcut to generate the Guild Raid bosses knowledge base
├── api_key.txt.example                            # Template for your Snowprint API key
├── .gitignore                                     # Excludes private keys, player data, and build artifacts
├── README.md                                      # Project documentation
├── src/main/java/com/tacticus/analytics/
│   ├── TacticusApiDownloader.java               # HTTP client for Snowprint API
│   ├── TacticusKnowledgeGenerator.java            # Markdown KB builder (Characters, MoW, Traits, Factions)
│   ├── TacticusBossKnowledgeGenerator.java        # Markdown KB builder for Guild Raid bosses, primes & debuffs
│   ├── common/
│   │   ├── DataPaths.java                         # Resolves static, Guild Raid, and player data paths
│   │   ├── GameDataLoader.java                    # Loads heroes, xref tables, abilities catalogs, and player JSON
│   │   ├── GameTextFormatter.java                 # Formats faction names and resolves ability placeholders
│   │   ├── HeroStatsCalculator.java               # Computes character stats from rank, stars, upgrades, and items
│   │   └── ProgressionHelper.java                 # Maps progression indices, ranks, and unit/ability matching
│   └── model/
│       ├── GameModels.java                        # Static metadata records (abilities, heroes, weapons, items)
│       ├── HeroProfile.java                       # Aggregated unit profile (static + player progression)
│       └── PlayerModels.java                      # Snowprint API response records
└── data/
    ├── api_key.txt                                # (Ignored) Alternative location for API key
    ├── my_player_data.json                        # (Ignored) Your downloaded player data
    ├── items.json                                 # Static gear and upgrade items
    ├── heroes/                                    # Static character base stats
    ├── xref/                                      # Static cross-reference tables & boss ability metadata
    ├── guild-raid/                                # Guild Raid boss/prime configs (bosses/) & boss_debuffs.json
    └── game-data/abilities/                       # Ability catalogs (active.json, passive.json, other.json)
```

---

## Contributing & License

Contributions, issues, and feature requests are welcome!
Distributed under the MIT License.