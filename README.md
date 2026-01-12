# Supermaven for JetBrains

AI-powered code completion plugin for JetBrains IDEs.

## Requirements

- JDK 21+
- IntelliJ IDEA 2025.2+ (or compatible JetBrains IDE)

## Local Installation

### Option 1: Run in Development Mode

1. Clone the repository:
   ```bash
   git clone https://github.com/yaroslavborbat/supermaven-jetbrains.git
   cd supermaven-jetbrains
   ```

2. Run the plugin in a sandboxed IDE:
   ```bash
   ./gradlew runIde
   ```
   
   This will download the required IDE and start it with the plugin installed.

### Option 2: Build and Install Manually

1. Build the plugin:
   ```bash
   ./gradlew buildPlugin
   ```

2. The plugin archive will be created at:
   ```
   build/distributions/supermaven-jetbrains-1.0-SNAPSHOT.zip
   ```

3. Install in your IDE:
   - Open your JetBrains IDE
   - Go to **Settings** → **Plugins** → ⚙️ (gear icon) → **Install Plugin from Disk...**
   - Select the `.zip` file from `build/distributions/`
   - Restart the IDE

## Usage

After installation:

1. The plugin will automatically start and download the Supermaven agent
2. You'll see a notification to activate Supermaven (Pro or Free version)
3. Code completions will appear as you type

### Keyboard Shortcuts

| Action | Shortcut |
|--------|----------|
| Accept completion | `Tab` |
| Accept word | `Ctrl+Right` (configurable) |
| Dismiss completion | `Escape` |

### Actions

- **Supermaven: Use Pro** — Open activation page for Pro version
- **Supermaven: Use Free Version** — Use free tier
- **Supermaven: Logout** — Log out from Supermaven

## Development

### Project Structure

```
src/
├── main/
│   ├── kotlin/supermaven/supermavenjetbrains/
│   │   ├── actions/          # IDE actions (menu items, shortcuts)
│   │   ├── binary/           # Communication with Supermaven agent
│   │   ├── completion/       # Completion rendering and text processing
│   │   ├── config/           # Plugin configuration
│   │   ├── SupermavenService.kt        # Main service
│   │   ├── SupermavenProjectActivity.kt # Startup activity
│   │   └── SupermavenToolWindowFactory.kt
│   └── resources/
│       └── META-INF/plugin.xml
```

### Useful Commands

```bash
# Run plugin in sandbox IDE
./gradlew runIde

# Build plugin archive
./gradlew buildPlugin

# Run tests
./gradlew test

# Verify plugin compatibility
./gradlew verifyPlugin
```

## License

MIT
