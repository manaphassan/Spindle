# Spindle Brand Identity & Design System Guide

<div align="center">
  <img src="assets/spindle_app_icon.png" width="128" height="128" alt="Spindle Logo"/>
  <h2>SPINDLE BRANDING & INDUSTRIAL DESIGN GUIDE</h2>
  <p><b>Version 1.2.0 · Revision 2026</b></p>
</div>

---

## 1. Brand Essence & Vision

### 1.1 The Name: Spindle
In analog tape recording, the **spindle** is the drive shaft that grips the cogged teeth of a cassette tape hub to rotate magnetic tape across the read head at exactly $4.7625\text{ cm/s}$. It represents physical movement, mechanical synchronization, and tangible audio playback.

### 1.2 The Mission
To breathe second life into compact smartphones (such as the Sony Xperia X Compact) and legacy Android DAPs by providing a dedicated, distraction-free, physical-feeling audiophile music launcher with **zero cloud bloat and zero background AI**.

### 1.3 Core Brand Pillars
- **Analog Authenticity**: Every interface element behaves like real hardware with tactile inertia, detent vibrations, and physical feedback.
- **Audiophile Fidelity**: Bit-perfect direct ALSA audio routing, FLAC/DSD native decoding, and zero lossy algorithmic processing.
- **Hardware Preservation**: Ultra-low RAM consumption ($<25\text{MB}$), zero GC hiccups, extending the lifespan of older compact devices.
- **Absolute Privacy (NO AI)**: 100% offline, deterministic, with zero telemetry or data collection.

---

## 2. Official Logo & App Icon

<div align="center">
  <img src="assets/spindle_app_icon.png" width="200" height="200" alt="Official Spindle Icon" />
  <p><i>The Spindle Gear Hub Emblem</i></p>
</div>

### 2.1 Geometry & Symbolism
The Spindle emblem combines the precision geometry of an internal cassette tape drive gear with the rugged industrial lines of Japanese audio engineering:
- **Outer Cog Teeth**: 12 precision teeth finished in anodized Walkman Crimson Red.
- **Inner Core**: Matte obsidian brushed aluminum rotor with 6 circular weight-reduction ports.
- **Center Spindle Arbor**: Deep-set mechanical shaft opening.

### 2.2 Usage Guidelines
- **Clear Space**: Maintain a minimum margin equal to 25% of the icon width on all sides.
- **Minimum Size**: Legible down to $16 \times 16\text{px}$ in notification trays and task switchers.
- **Color Consistency**: Do not alter the crimson red and obsidian color relationship.

---

## 3. Official System Themes & Color Palettes

Spindle provides 3 meticulously balanced system themes tailored for diverse display technologies:

### 3.1 The 3 System Hardware Themes

1. **Audiophile Dark (Obsidian)** *(Default)*:
   - Primary Background: Deep Obsidian Charcoal (`#141518` / `#0D0E11`).
   - Accent: Radiant Mint-Emerald (`#00E676`) and Walkman Crimson Red (`#D71920`).
   - Optimized for OLED / AMOLED displays with zero pixel bleed and maximum contrast.

2. **Monochrome E-Ink**:
   - Primary Background: Pure Paper White (`#FFFFFF`).
   - Ink & Details: Pure Ink Black (`#000000`).
   - Borders: High-contrast 1-bit solid hairline borders (`#000000`).
   - Engineered specifically for e-paper music players (Onyx Boox, Hisense A-series, InkPalm) with disabled drop shadows and maximum e-ink refresh clarity.

3. **Clean Light (Brushed Aluminum)**:
   - Primary Background: Matte Anodized Silver (`#F5F5F7` / `#E5E5EA`).
   - Surfaces: Soft Warm Slate (`#FFFFFF` with subtle `#D1D1D6` bevels).
   - Accents: Deep Graphite (`#1C1C1E`) and Crimson Red (`#C8102E`).
   - Inspired by classic Dieter Rams Braun functionalism.

### 3.2 Primary Industrial Color Palette

| Color Name | Hex Code | RGB | Role / Application |
| :--- | :--- | :--- | :--- |
| **Walkman Crimson Red** | `#D71920` | `215, 25, 32` | Flagship WM-2 Red Chassis, Playhead cursor, Brand Primary |
| **Obsidian Hardware** | `#141518` | `20, 21, 24` | Music Catalog background, Now Playing background |
| **Braun Ivory Neumorphic**| `#E8E8EC` | `232, 232, 236` | Online FM Radio chassis, soft recessed control wells |
| **Mint Emerald Radiant** | `#00E676` | `0, 230, 118` | Active Play states, Waveform bars, Bit-Perfect Native badge |
| **Backlit LCD Green** | `#DCE6DA` | `220, 230, 218` | Braun FM Radio vintage matrix display window |
| **Matrix LCD Dark Text** | `#1F2E1E` | `31, 46, 30` | 7-Segment frequency numbers & RDS marquee text |
| **VFD Amber Gold** | `#FFB300` | `255, 179, 0` | Dynamic Bitrate, Warning telemetry, Active folder icons |
| **Peak Alert Coral** | `#EF4444` | `239, 68, 68` | VU Meter 0dB clipping indicator, Radio active preset text |

---

## 4. Typography & Layout Specifications

### 4.1 Home Screen Cassette Deck (Centered Spindle Layout)
- **Clear Spindle Window**: Centered horizontally across the display width.
- **Left Telemetry Column (`columnCenterX`)**:
  - **Brand Header**: `WALKMAN` (Bold Sans-Serif, 18sp, tracking +1.5px).
  - **Real-Time Clock**: `HH:MM:SS` (Bold Monospace, 24sp).
  - **Hi-Res Format Capsule Badge**: Drawn directly below the clock with rounded pill borders (e.g. `FLAC 16-BIT / 44.1 KHZ`, `MP3 320 KBPS`, 10sp Monospace).
  - **Line 0 (Song Title)**: Bold crisp white (16sp, automatic text wrap / marquee).
  - **Line 1 (Artist & Duration)**: Regular slate subtext (`Artist Name • mm:ss / mm:ss`, 12sp).
- **Transport Bar**: Increased length (80% vertical height allocation) with physical haptic click buttons.

### 4.2 Dedicated Single Audio Now Playing Overlay
- **Circular Album Art & Radial Arc**: Centered circular cover framed by a 360° touch radial scrub arc (`CircularCoverArcView`).
- **Dynamic 24-Band Waveform**: Hardware-accelerated frequency bars reacting live to playback ballistics (`AudioWaveformView`).
- **Format Telemetry Chip**: Interactive pill displaying bit depth, sample frequency, and live bitrate (`FLAC 16-bit / 44.1kHz • 846 kbps`).
- **Expandable Synced Lyrics**: Slide-up drawer displaying synchronized `.lrc` lyrics with auto-scroll highlighting.

### 4.3 Catalog A-Z Quick Scroller
- **A-Z Alphabet Rail**: Vertical rail on the right edge of the screen.
- **Detent Haptics**: Subtle mechanical vibration tick when dragging across character thresholds.

---

## 5. Industrial Design Disciplines

### 5.1 The Braun / Dieter Rams Functionalism
In accordance with Dieter Rams' *Ten Principles for Good Design*:
- **Acoustic Speaker Grille**: Concentric radial hole spacing derived from the Braun T3 (1959).
- **Tactile Ribbed Thumbwheel**: Physical finger groove geometry with detent feedback.
- **Uncluttered Readouts**: Only pertinent frequency, RDS, and volume information are displayed.

### 5.2 The 1981 Sony Walkman II (WM-2) Heritage
- **Rectangular Minimalism**: Form defined by the exact cassette dimensions with minimal bezel.
- **Mechanical Levers**: Buttons feature realistic spring-loaded press depth and dual-stage tactile click releases.
- **True Differential Kinematics**: Reel spools rotate with non-linear angular velocity obeying magnetic tape thickness equations.

---

## 6. Tone of Voice & Copywriting

- **Clear, Technical & Confident**: Speak to audiophiles and hardware enthusiasts without corporate buzzwords.
- **No AI Jargon**: Never use terms like "AI-powered", "Smart Recommendations", or "Cloud Magic". Spindle is proudly **100% deterministic, local, and analog-inspired**.
- **Hardware-First Terminology**: Refer to controls as *chassis*, *potentiometers*, *reels*, *spindles*, *eject lever*, *detents*, and *heads*.

<div align="center">
  <sub>Spindle · Designed by HaNa Innovation · Built for the Audiophile Community</sub>
</div>
