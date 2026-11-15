// Radio deck mockup - chassis, tuning wheel, meters, switches, preset keys.
// Companion to ../faceplate-deck: same chassis, palettes and key geometry.
(function () {
  const SANS = "Arial,sans-serif", MONO = "Consolas,monospace";
  const label = (p, x, y, t, anchor, extra) =>
    `<text x="${x}" y="${y}" ${anchor ? `text-anchor="${anchor}"` : ""} font-family="${SANS}" font-size="8" font-weight="700" letter-spacing="1.2" fill="${p.print}" ${extra || ""}>${t}</text>`;

  function brush(p) {
    let lines = "", seed = 11;
    const rnd = () => ((seed = (seed * 9301 + 49297) % 233280) / 233280);
    for (let y = 0; y < 592; y += 1.5) {
      const light = rnd() > 0.5;
      const a = (light ? p.brushLight : p.brushDark) * rnd();
      lines += `<line x1="0" y1="${y}" x2="360" y2="${y}" stroke="${light ? "#FFFFFF" : "#000000"}" stroke-opacity="${a.toFixed(3)}" stroke-width="0.6"/>`;
    }
    return lines;
  }

  function body(p, v, st) {
    let s = `<defs><linearGradient id="rbody-${v}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="${p.bodyTop}"/><stop offset="1" stop-color="${p.bodyBot}"/></linearGradient></defs>`;
    s += `<rect x="0" y="0" width="360" height="592" fill="url(#rbody-${v})"/>`;
    s += brush(p);
    s += `<rect x="0" y="0" width="360" height="44" fill="${p.band}"/>`;
    s += `<text x="16" y="29" font-family="${SANS}" font-size="17" font-weight="800" letter-spacing="5" fill="${p.wordmark}">SPINDLE</text>`;
    s += `<rect x="134" y="24" width="5" height="5" fill="#F97316"/>`;
    s += `<text x="344" y="20" text-anchor="end" font-family="${SANS}" font-size="8" font-weight="700" letter-spacing="1.4" fill="${p.bandSub}">FM STEREO TUNER</text>`;
    const air = st.onAir === false ? p.ledOff : "#00E676";
    s += `<circle cx="296" cy="31" r="3" fill="${air}"/>`;
    if (st.onAir !== false) s += `<circle cx="296" cy="31" r="6" fill="#00E676" opacity="0.18"/>`;
    s += `<text x="344" y="34" text-anchor="end" font-family="${SANS}" font-size="8" font-weight="700" letter-spacing="1.4" fill="${p.bandSub}">ON AIR</text>`;
    return s;
  }

  // Seek buttons + knurled thumbwheel (0.1 MHz detents)
  function wheel(p, v, dim) {
    let s = `<g opacity="${dim ? 0.4 : 1}">`;
    s += label(p, 36, 293, "SEEK", "middle");
    s += label(p, 180, 293, "TUNING", "middle");
    s += label(p, 324, 293, "SEEK", "middle");
    [[36, -1], [324, 1]].forEach(([cx, dir]) => {
      s += `<circle cx="${cx}" cy="314" r="13" fill="${p.keyTop}" stroke="${p.keyFront}" stroke-width="1.5"/>`;
      const t = (x) => `M${x} 314 l${-7 * dir} -5 v10 z`;
      s += `<path d="${t(cx)} ${t(cx + 7 * dir)}" fill="${p.keyGlyph}"/>`;
    });

    s += `<defs><linearGradient id="wheel-${v}" x1="0" y1="0" x2="1" y2="0">` +
      `<stop offset="0" stop-color="${p.knobGroove}"/><stop offset="0.5" stop-color="${p.knob}"/><stop offset="1" stop-color="${p.knobGroove}"/></linearGradient></defs>`;
    s += `<rect x="58" y="300" width="244" height="28" rx="5" fill="${p.recess}"/>`;
    s += `<rect x="62" y="303" width="236" height="22" rx="3" fill="url(#wheel-${v})"/>`;
    for (let deg = -84; deg <= 84; deg += 6) {
      const r = deg * Math.PI / 180, c = Math.cos(r);
      const x = (180 + 116 * Math.sin(r)).toFixed(1);
      s += `<line x1="${x}" y1="305" x2="${x}" y2="323" stroke="${p.recess}" stroke-opacity="${(0.15 + 0.5 * c).toFixed(2)}" stroke-width="${(0.5 + 1.1 * c).toFixed(2)}"/>`;
    }
    s += `<rect x="62" y="303" width="236" height="4" rx="2" fill="#FFFFFF" opacity="0.12"/>`;
    s += `<path d="M176 296 h8 l-4 5 z" fill="#F97316"/>`;
    return s + `</g>`;
  }

  // Two-row meter. FM: SIG + centre-zero TUNE. NET: BUF + LINK.
  const SEGS = 26, X0 = 34, W = 9, GAP = 2.4;
  const segX = (i) => X0 + i * (W + GAP);

  function meter(p, m) {
    let s = `<rect x="12" y="338" width="336" height="52" rx="6" fill="${p.recess}"/>`;
    m.rows.forEach((row, r) => {
      const y = 346 + r * 14;
      s += `<text x="22" y="${y + 7}" text-anchor="middle" font-family="${SANS}" font-size="7" font-weight="700" fill="${p.lcdSub}">${row.name}</text>`;
      for (let i = 0; i < SEGS; i++) {
        const on = row.lit ? row.lit.includes(i) : i < row.level;
        s += `<rect x="${segX(i)}" y="${y}" width="${W}" height="8" rx="1" fill="${on ? p.ledOn : p.ledOff}"/>`;
      }
      if (row.lamp !== undefined) {
        s += `<circle cx="338" cy="${y + 4}" r="3" fill="${row.lamp ? p.ledOn : p.ledOff}"/>`;
      }
      // Threshold tick, e.g. the scan lock level
      if (row.mark !== undefined) {
        const mx = segX(row.mark) - GAP / 2;
        s += `<path d="M${mx - 3} ${y - 6} h6 l-3 4 z" fill="#F97316"/>`;
      }
    });
    const mid = segX(13) - GAP / 2;
    m.scale.forEach(([t, where]) => {
      const x = where === "l" ? segX(0) : where === "c" ? mid : segX(SEGS - 1) + W;
      const anchor = where === "l" ? "start" : where === "c" ? "middle" : "end";
      s += `<text x="${x}" y="384" text-anchor="${anchor}" font-family="${SANS}" font-size="7.5" font-weight="700" fill="${p.lcdSub}">${t}</text>`;
    });
    return s;
  }

  function slideSwitch(p, x, w, title, stops, active, dim) {
    let s = `<g opacity="${dim ? 0.4 : 1}">`;
    s += `<text x="${x}" y="406" font-family="${SANS}" font-size="8" font-weight="700" letter-spacing="1.2" fill="${p.print}">${title}</text>`;
    const step = w / stops.length;
    stops.forEach((t, i) => {
      const cx = x + step * i + step / 2;
      s += `<text x="${cx}" y="419" text-anchor="middle" font-family="${SANS}" font-size="8.5" font-weight="${i === active ? 800 : 600}" fill="${i === active ? p.print : p.printMuted}">${t}</text>`;
    });
    s += `<rect x="${x}" y="424" width="${w}" height="16" rx="8" fill="${p.recess}"/>`;
    const kx = x + step * active + 3, kw = step - 6;
    s += `<rect x="${kx}" y="422" width="${kw}" height="20" rx="4" fill="${p.knob}" stroke="${p.knobGroove}" stroke-width="0.8"/>`;
    for (let g = -2; g <= 2; g++) {
      const gx = kx + kw / 2 + g * 4;
      s += `<line x1="${gx}" y1="426" x2="${gx}" y2="438" stroke="${p.knobGroove}" stroke-width="1"/>`;
    }
    return s + `</g>`;
  }

  function switches(p, st) {
    let s = "";
    s += slideSwitch(p, 16, 140, "BAND", ["FM", "NET"], st.band === "FM" ? 0 : 1, false);
    s += slideSwitch(p, 176, 112, "MODE", ["AUTO", "MONO"], st.forceMono ? 1 : 0, st.band !== "FM" || st.fmDead);
    s += `<text x="344" y="406" text-anchor="end" font-family="${SANS}" font-size="8" font-weight="800" letter-spacing="1.2" fill="${p.print}">${st.pilotLabel}</text>`;
    const lampOn = st.pilot ? "#00E676" : p.ledOff;
    s += `<circle cx="326" cy="432" r="3.5" fill="${lampOn}"/>`;
    if (st.pilot) s += `<circle cx="326" cy="432" r="7" fill="#00E676" opacity="0.2"/>`;
    return s;
  }

  // 4 latching preset keys + SCAN, same 62x70dp geometry as the tape transport
  function keys(p, st) {
    let s = "";
    const KW = 62.4, G = 6;
    const items = st.stations.map((c, i) => ({ label: c.call, num: String(i + 1), freq: c.f.toFixed(1) }));
    items.push({ label: "SCAN", scan: true });

    items.forEach((k, i) => {
      const x = 12 + i * (KW + G), cx = x + KW / 2;
      const down = i === st.preset || (k.scan && st.scanning);
      const dim = (k.scan && st.band !== "FM") || st.fmDead;
      const top = down ? p.playTop : p.keyTop;
      const front = down ? p.playFront : p.keyFront;
      const glyph = down ? p.playGlyph : p.keyGlyph;
      const ty = down ? 488 : 478, th = down ? 68 : 70, fh = down ? 8 : 18;

      s += `<g opacity="${dim ? 0.4 : 1}">`;
      s += `<text x="${cx}" y="470" text-anchor="middle" font-family="${SANS}" font-size="8.5" font-weight="800" letter-spacing="${k.label.length > 7 ? 0.4 : 1.2}" fill="${p.print}">${k.label}</text>`;
      s += `<rect x="${x - 2}" y="474" width="${KW + 4}" height="98" rx="6" fill="#000000" opacity="0.28"/>`;
      s += `<rect x="${x}" y="${ty + th - 6}" width="${KW}" height="${fh + 6}" rx="5" fill="${front}"/>`;
      s += `<rect x="${x}" y="${ty}" width="${KW}" height="${th}" rx="5" fill="${top}"/>`;
      s += `<rect x="${x + 1}" y="${ty + 1}" width="${KW - 2}" height="2" rx="1" fill="${down ? "#FDBA74" : p.keyTopEdge}" opacity="0.7"/>`;
      for (let r = 0; r < 3; r++) {
        s += `<line x1="${x + 14}" y1="${ty + 9 + r * 4}" x2="${x + KW - 14}" y2="${ty + 9 + r * 4}" stroke="${front}" stroke-width="1" opacity="0.55"/>`;
      }
      const cy = ty + th / 2 + 6;
      if (k.scan) {
        s += `<path d="M${cx - 12} ${cy - 2} l6 -5 v10 z M${cx + 12} ${cy - 2} l-6 -5 v10 z" fill="${glyph}"/>`;
        s += `<rect x="${cx - 7}" y="${cy - 3}" width="14" height="2" fill="${glyph}"/>`;
        s += `<rect x="${cx - 1}" y="${cy - 9}" width="2" height="14" rx="1" fill="${down ? glyph : "#F97316"}"/>`;
      } else {
        s += `<text x="${cx}" y="${cy + 2}" text-anchor="middle" font-family="${SANS}" font-size="22" font-weight="800" fill="${glyph}">${k.num}</text>`;
        s += `<text x="${cx}" y="${cy + 16}" text-anchor="middle" font-family="${MONO}" font-size="8.5" font-weight="700" fill="${glyph}" fill-opacity="0.75">${k.freq}</text>`;
      }
      s += `</g>`;
    });

    s += `<text x="180" y="586" text-anchor="middle" font-family="${SANS}" font-size="8" font-weight="700" letter-spacing="1.2" fill="${p.hint}">${st.hint || "TAP PRESET TO TUNE · HOLD TO STORE"}</text>`;
    s += `<text x="344" y="586" text-anchor="end" font-family="${SANS}" font-size="8" font-weight="700" letter-spacing="1.2" fill="${p.hint}">TAPE ›</text>`;
    return s;
  }

  function navBar() {
    return `<rect x="0" y="592" width="360" height="48" fill="#000000"/>` +
      `<path d="M80 616 l12 -8 v16 z" fill="#FFFFFF"/>` +
      `<circle cx="180" cy="616" r="9" fill="none" stroke="#FFFFFF" stroke-width="2"/><circle cx="180" cy="616" r="6" fill="#FFFFFF"/>` +
      `<rect x="272" y="609" width="14" height="14" rx="1.5" fill="#FFFFFF"/>`;
  }

  // Stations mirror AnalogFmEngine.carrierStations / RadioStreamEngine.PRESET_STATIONS
  const STATIONS = [
    { f: 88.5, call: "LO-FI" },
    { f: 93.2, call: "ANIMEFM" },
    { f: 98.6, call: "INITIAL D" },
    { f: 104.2, call: "CITYPOP" }
  ];

  window.RADIO_STATES = {
    fm: {
      band: "FM", freq: 98.6, preset: 2, forceMono: false,
      pilot: true, pilotLabel: "STEREO",
      genre: "EUROBEAT / HIGH OCTANE",
      lineTag: "RDS", line: "INITIAL D WORLD RADIO • EUROBEAT SPEEDWAY",
      region: "87.5 – 108.0",
      meter: {
        rows: [
          { name: "SIG", level: 20 },
          { name: "TUNE", lit: [12, 13], lamp: true }
        ],
        scale: [["−100 kHz", "l"], ["▼ LOCK", "c"], ["+100 kHz", "r"]]
      }
    },
    net: {
      band: "NET", freq: 93.2, preset: 1, forceMono: false,
      pilot: true, pilotLabel: "LIVE",
      genre: "ANIME & J-POP",
      lineTag: "NOW", line: "Unravel — TK from Ling tosite sigure",
      region: "STREAM",
      meter: {
        rows: [
          { name: "BUF", level: 22 },
          { name: "LINK", level: 18 }
        ],
        scale: [["0 s", "l"], ["15 s", "c"], ["30 s", "r"]]
      }
    },
    // startScan(): sweeps up 0.1 MHz / 60 ms, locks at getSignalStrength >= 0.68
    scan: {
      band: "FM", freq: 95.7, preset: -1, forceMono: false, scanning: true,
      pilot: false, pilotLabel: "STEREO", needle: "trail",
      genre: "",
      lineTag: "SCAN", line: "SEARCHING LOCAL STATIONS ▸▸",
      region: "87.5 – 108.0",
      hint: "TAP SCAN AGAIN TO STOP",
      meter: {
        rows: [
          { name: "SIG", level: 4, mark: 18 },
          { name: "TUNE", lit: [4], lamp: false }
        ],
        scale: [["−100 kHz", "l"], ["▼ LOCK", "c"], ["+100 kHz", "r"]]
      }
    },
    // onHeadsetPlugStateChanged(false): the headphone cable is the FM antenna
    noant: {
      band: "FM", freq: 98.6, preset: 2, forceMono: false, fmDead: true, onAir: false,
      pilot: false, pilotLabel: "STEREO", needle: "parked", freqDim: true,
      warn: "NO ANT", genre: "",
      lineTag: "ANT", line: "PLUG IN WIRED HEADPHONES · OR SLIDE TO NET", lineWarn: true,
      region: "87.5 – 108.0",
      hint: "HEADPHONE CABLE = FM ANTENNA",
      meter: {
        rows: [
          { name: "SIG", level: 0 },
          { name: "TUNE", lit: [], lamp: false }
        ],
        scale: [["−100 kHz", "l"], ["▼ LOCK", "c"], ["+100 kHz", "r"]]
      }
    }
  };

  window.renderRadioDeck = function (v, stateKey) {
    const p = window.SPINDLE_PALETTES[v];
    const st = Object.assign({ stations: STATIONS }, window.RADIO_STATES[stateKey]);
    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 360 640" width="360" height="640">` +
      body(p, v, st) +
      window.renderTunerWindow(p, v, st) +
      wheel(p, v, st.fmDead) +
      meter(p, st.meter) +
      switches(p, st) +
      keys(p, st) +
      navBar() +
      `</svg>`;
  };
})();
