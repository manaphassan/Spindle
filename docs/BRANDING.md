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

## 3. Official System Themes & Brand Color Palette

Spindle's visual identity is anchored in a nostalgic, sunny, and friendly throwback mixtape aesthetic:

<div align="center">
  <b>HEX: <code>#2A2E45</code> · <code>#F97316</code> · <code>#FB7185</code> · <code>#FDE68A</code> · <code>#FAFAF9</code></b>
  <p><i>Mood: nostalgic, sunny, friendly · Vintage mixtapes, handwritten tracklists, and weekend drives</i></p>
</div>

### 3.1 The 60 : 30 : 10 Color Harmony Rule

The palette follows strict audiophile industrial design ratios ensuring high legibility and zero eye fatigue:

- **60% Dominant Base**:
  - **Dark Theme ("Indigo Mixtape")**: Deep Dark Indigo (`#2A2E45`). Provides a deep, velvety chassis foundation that lets warm tones look intentional, grounded, and physical.
  - **Light Theme ("Sunny Mixtape")**: Pale Warm Stone (`#FAFAF9`). Keeps small typography, track metadata, and technical badges razor-sharp at compact DAP screen sizes.
- **30% Secondary Structure & Framing**:
  - **Dark Theme**: Rich Indigo Surfaces (`#202334`), Structural Bevels (`#1F2233`), Dividers (`#353A54`), and Crisp Pale Typography (`#FAFAF9` / `#B0B4CE`).
  - **Light Theme**: Solid Dark Indigo Framing Bevels (`#2A2E45`), Crisp Dark Indigo Text (`#2A2E45`), and Soft Warm Neutral Cards (`#FFFFFF` with `#E5E5E2` borders).
  - **Cassette Label Body**: Nostalgic Butter Yellow (`#FDE68A`) mimicking authentic paper mixtape labels with dark indigo handwritten titles.
- **10% High-Impact Accent**:
  - **Hero Warm Tangerine Orange (`#F97316`)**: Dedicated exclusively to active playback states, transport keys, kinetic reel indicator notches, tuner dial hairline needle, and progress sliders/LEDs.
  - **Secondary Peachy Coral Pink (`#FB7185`)**: Favorite heart toggle, audio peak VU meter alerts, and capsule badges.
  - **Tertiary Butter Yellow (`#FDE68A`)**: Star ratings, warm VFD 7-segment clock digits, and FM frequency display glow.

---

### 3.2 Official Brand Color Tokens

| Token Name | Hex Code | Color Swatch | Role / Application |
| :--- | :--- | :--- | :--- |
| **Dark Indigo** | `#2A2E45` | `■` `#2A2E45` | 60% Dark chassis base, Light theme structural framing bevels, crisp dark text |
| **Warm Tangerine** | `#F97316` | `■` `#F97316` | 10% Hero accent, active playback, kinetic reel notches, tuner needle, progress LEDs |
| **Peachy Coral Pink** | `#FB7185` | `■` `#FB7185` | Secondary accent, favorite heart, 0dB peak meter alert LED, audio format capsule |
| **Butter Yellow** | `#FDE68A` | `■` `#FDE68A` | Cassette tape label body, star ratings, warm VFD clock glow & radio frequency |
| **Pale Warm Stone** | `#FAFAF9` | `■` `#FAFAF9` | 60% Light chassis base, crisp high-contrast text in Dark theme |

---

### 3.3 The 3 System Hardware Themes

1. **Dark Theme ("Indigo Mixtape")** *(Default)*:
   - 60% Dark Indigo chassis (`#2A2E45`) with `#202334` elevated cards.
   - 10% Warm Tangerine Orange (`#F97316`) hero controls & Butter Yellow (`#FDE68A`) tape label.
   - Tailored for OLED and compact LCD screens with zero eye strain in low-light listening sessions.

2. **Light Theme ("Sunny Mixtape")**:
   - 60% Pale Warm Stone chassis (`#FAFAF9`) framed by bold Dark Indigo borders (`#2A2E45`).
   - Authentic Butter Yellow cassette label (`#FDE68A`) with handwritten dark indigo tracklists.
   - Warm Tangerine Orange (`#F97316`) transport buttons and progress indicators.
   - High legibility outdoors and in bright environments.

3. **Monochrome E-Ink Theme**:
   - 100% 1-bit Pure Black (`#000000`) and Pure White (`#FFFFFF`).
   - Zero gradients, zero anti-aliased gray shadows, solid hairline borders for instantaneous e-paper refresh.
   - Engineered for Onyx Boox, Hisense, and InkPalm devices.

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
