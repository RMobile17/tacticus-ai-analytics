# Tacticus AI Analytics & NotebookLM Knowledge Base Exporter

[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.8%2B-blue.svg)](https://maven.apache.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

An analytics and knowledge base generation tool for **Warhammer 40,000: Tacticus**. This project combines comprehensive static game metadata (characters, factions, traits, abilities, ranks, equipment, and summons) with personal player progression exported via the official **Tacticus API**.

The resulting output is a structured, highly optimized Markdown knowledge base (`tacticus_notebooklm_kb.md`) ready to be uploaded to **Google NotebookLM** for deep tactical queries, guild raid team planning, upgrade prioritization, and Legendary Release Event (LRE) calculations.

---

## Features

- **Automated API Integration (`TacticusApiDownloader`)**:
  - Connects to the official Snowprint Tacticus API (`api.tacticusgame.com`).
  - Securely downloads player roster data, including unlocked heroes, ranks, stars, ability levels, equipped upgrades, inventory items, and guild raid status.
- **Knowledge Base Generator (`TacticusKnowledgeGenerator`)**:
  - Joins static game metadata with your live player profile.
  - Resolves dynamic ability formulas and values precisely for your hero's current level (e.g., resolving multi-target damage variables like `{[dmg[0]]}` and level scaling).
  - Calculates current character stats based on rarity, star level, rank, and equipped gear upgrades.
  - Cross-references traits, faction synergies, damage types, and summons.
- **Google NotebookLM Optimization**:
  - Clean Markdown formatting specifically tailored for NotebookLM's context window and grounding capabilities.
  - Unlocked heroes are prominently listed with exact stats, while locked heroes remain available for theorycrafting.
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
     - `Player` (**Required**): Grants read-only access to your unlocked characters, upgrades, ability levels, inventory, and account stats.
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

This project uses static game metadata for character base statistics, ability level matrices, traits, items, and upgrade trees.

- Static files are located in the `data/` folder (`heroes/`, `xref/`, `abilities/`, `items.json`, etc.).
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

2. **Generate Knowledge Base**:
   ```cmd
   generate.bat
   ```
   Runs `mvn compile exec:java -Pgenerate`. Generates `tacticus_notebooklm_kb.md` in the project root.

---

### Option B: Maven CLI (Cross-Platform)

1. **Download Player Data**:
   ```bash
   mvn compile exec:java -Pdownload
   ```

2. **Generate Knowledge Base**:
   ```bash
   mvn compile exec:java -Pgenerate
   ```

---

## Using with Google NotebookLM

Once `tacticus_notebooklm_kb.md` is generated:

1. Open [Google NotebookLM](https://notebooklm.google.com/).
2. Create a new notebook (e.g., *"Warhammer 40k: Tacticus Roster & Strategy"*).
3. Click **Add Source** -> **Upload File**, and select `tacticus_notebooklm_kb.md`.
4. Once processed, NotebookLM has complete, grounded context of your exact roster and all game mechanics.

### Example Prompts for NotebookLM

- **Guild Raid Team Optimization**:
  > *"Based on my unlocked heroes and their current ability levels, suggest the top 2 teams for Ghazghkull Thraka. Explain the synergies between Eldryon's Doom, multi-hit attackers, and survivability."*

- **Legendary Release Event (LRE) Planning**:
  > *"Analyze my unlocked roster against typical LRE track restrictions (e.g., Physical damage only, Piercing only, 3+ hits). Which characters fulfill the most criteria simultaneously and should be leveled up next?"*

- **Investment Prioritization**:
  > *"Review my roster and identify the top 3 characters who will gain the largest power spike if upgraded to Diamond 1 or if their passive ability is pushed to level 50."*

- **Synergy & Mechanic Analysis**:
  > *"Find all synergistic combinations in my roster centered around Mechanical units, Actus' repair mechanics, and Exitor-Rho's passive extra attacks."*

---

## Project Structure

```text
tacticus-ai-analytics/
├── pom.xml                     # Maven configuration with 'download' and 'generate' profiles
├── download.bat                # Shortcut to download player data
├── generate.bat                # Shortcut to generate the NotebookLM knowledge base
├── api_key.txt.example         # Template for your Snowprint API key
├── .gitignore                  # Excludes private keys, player data, and build artifacts
├── README.md                   # Project documentation
├── src/main/java/com/tacticus/analytics/
│   ├── TacticusApiDownloader.java       # HTTP client for Snowprint API
│   └── TacticusKnowledgeGenerator.java  # Markdown KB builder with stat & ability calculations
└── data/
    ├── api_key.txt             # (Ignored) Alternative location for API key
    ├── my_player_data.json     # (Ignored) Your downloaded player data
    ├── items.json              # Static gear and upgrade items
    ├── heroes/                 # Static character base stats
    ├── xref/                   # Static cross-reference tables
    └── ...
```

---

## Contributing & License

Contributions, issues, and feature requests are welcome!
Distributed under the MIT License.

