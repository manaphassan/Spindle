# Workspace Rules

## UI Consistency & Design System
Follow all guidelines specified in [.agents/rules/ui-consistency.md](file:///d:/HaNa_Innovation/dap_launcher/.agents/rules/ui-consistency.md) and [.agents/skills/ui-consistency/SKILL.md](file:///d:/HaNa_Innovation/dap_launcher/.agents/skills/ui-consistency/SKILL.md).

- **Core Rule**: Do not redesign existing layouts or navigation unless explicitly requested.
- **Tokens & Hierarchy**: Use semantic theme tokens (`background`, `surface`, `text-primary`, `border`, `primary`, etc.) and ensure WCAG 2.2 contrast compliance in both Light and Dark modes.
- **Icon & Spacing Standardization**: Standardize icon sizes (16px, 20px, 24px) and consolidate spacing into a consistent scale.
- **Workflow**: Always perform a non-modifying audit first before proposing and executing fixes ordered by priority (P0 → P1 → P2 → P3).

## Target Module Rules & Safeguards
This workspace contains two distinct Android application modules:
1. **`app-main`** (`:app-main`): The primary flagship Spindle DAP Launcher (`com.hana.spindle`).
2. **`app-lite`** (`:app-lite`): The ultra-lightweight DAP Launcher variant for vintage/low-RAM hardware (`com.hana.spindle.lite`).

### Mandatory Execution Constraints:
- **Default Target**: ALL feature requests, UI refinements, playback changes, bug fixes, and tests default strictly to **`app-main`** (`app-main/`), NEVER `app-lite`.
- **Lite Isolation**: **`app-lite`** (`app-lite/`) is STRICTLY OFF-LIMITS and must NEVER be modified unless the user explicitly specifies `"app-lite"`, `"lite"`, or `"Spindle Lite"` in their prompt.
- **Ignore Open Editor Tab Bias**: Never infer that `app-lite` is the intended target simply because files from `app-lite/` are open in the user's IDE editor.
- **Gradle & ADB Scoping**: Always scope Gradle tasks and ADB target packages explicitly:
  - Default: `./gradlew :app-main:assembleDebug`, package `com.hana.spindle.debug`.
  - Lite (only when requested): `./gradlew :app-lite:assembleDebug`, package `com.hana.spindle.lite.debug`.
