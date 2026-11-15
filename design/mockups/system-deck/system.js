// System deck mockup - SOUND / SETTINGS / APPS on the faceplate chassis.
// Coordinates are dp on a 360x640 canvas (SO-02J: 720x1280 @ xhdpi).
(function () {
  const SANS = "Arial,sans-serif", MONO = "Consolas,monospace";
  const T = "#F97316";
  const txt = (x, y, t, o) => {
    o = o || {};
    return `<text x="${x}" y="${y}" ${o.anchor ? `text-anchor="${o.anchor}"` : ""} font-family="${o.mono ? MONO : SANS}" ` +
      `font-size="${o.size || 8}" font-weight="${o.weight || 700}" letter-spacing="${o.ls === undefined ? 1.2 : o.ls}" ` +
      `fill="${o.fill}" ${o.op !== undefined ? `fill-opacity="${o.op}"` : ""}>${t}</text>`;
  };

  // ---- Chassis -------------------------------------------------------------------
  function brush(p) {
    let lines = "", seed = 23;
    const rnd = () => ((seed = (seed * 9301 + 49297) % 233280) / 233280);
    for (let y = 0; y < 592; y += 1.5) {
      const light = rnd() > 0.5;
      const a = (light ? p.brushLight : p.brushDark) * rnd();
      lines += `<line x1="0" y1="${y}" x2="360" y2="${y}" stroke="${light ? "#FFFFFF" : "#000000"}" stroke-opacity="${a.toFixed(3)}" stroke-width="0.6"/>`;
    }
    return lines;
  }

  function lamp(x, y, r, on) {
    return (on ? `<circle cx="${x}" cy="${y}" r="${r * 2}" fill="#00E676" opacity="0.18"/>` : "") +
      `<circle cx="${x}" cy="${y}" r="${r}" fill="${on ? "#00E676" : "#2C3A33"}"/>`;
  }

  function body(p, v) {
    let s = `<defs><linearGradient id="sbody-${v}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="${p.bodyTop}"/><stop offset="1" stop-color="${p.bodyBot}"/></linearGradient></defs>`;
    s += `<rect x="0" y="0" width="360" height="592" fill="url(#sbody-${v})"/>` + brush(p);
    s += `<rect x="0" y="0" width="360" height="44" fill="${p.band}"/>`;
    s += `<text x="16" y="29" font-family="${SANS}" font-size="17" font-weight="800" letter-spacing="5" fill="${p.wordmark}">SPINDLE</text>`;
    s += `<rect x="134" y="24" width="5" height="5" fill="${T}"/>`;
    s += txt(344, 20, "SYSTEM TOOLS", { anchor: "end", fill: p.bandSub, ls: 1.4 });
    s += lamp(316, 31, 3, true);
    s += txt(344, 34, "RUN", { anchor: "end", fill: p.bandSub, ls: 1.4 });
    return s;
  }

  function slideSwitch(p, x, y, w, title, stops, active, opts) {
    opts = opts || {};
    let s = title ? txt(x, y, title, { fill: p.print }) : "";
    const step = w / stops.length;
    stops.forEach((t, i) => {
      s += txt(x + step * i + step / 2, y + 13, t, {
        anchor: "middle", size: opts.size || 8.5, weight: i === active ? 800 : 600,
        fill: i === active ? p.print : p.printMuted
      });
    });
    s += `<rect x="${x}" y="${y + 18}" width="${w}" height="16" rx="8" fill="${p.recess}"/>`;
    const kx = x + step * active + 3, kw = step - 6;
    s += `<rect x="${kx}" y="${y + 16}" width="${kw}" height="20" rx="4" fill="${p.knob}" stroke="${p.knobGroove}" stroke-width="0.8"/>`;
    for (let g = -2; g <= 2; g++) {
      const gx = kx + kw / 2 + g * 4;
      s += `<line x1="${gx}" y1="${y + 20}" x2="${gx}" y2="${y + 32}" stroke="${p.knobGroove}" stroke-width="1"/>`;
    }
    return s;
  }

  // FUNCTION selector replaces the rotating Zune pivot. Order: sound / settings / apps.
  const PAGES = ["SOUND", "SETTINGS", "APPS"];
  function selector(p, active) {
    return slideSwitch(p, 16, 62, 328, "FUNCTION", PAGES, active, { size: 9 });
  }

  function door(p, y0, y1) {
    return `<rect x="12" y="${y0}" width="336" height="${y1 - y0}" rx="10" fill="${p.doorFrame}"/>` +
      `<rect x="18" y="${y0 + 6}" width="324" height="${y1 - y0 - 12}" rx="7" fill="${p.glass}"/>`;
  }
  const glare = (y0, y1) =>
    `<path d="M18 ${y0 + 6} h120 l-70 ${y1 - y0 - 12} h-50 z" fill="#FFFFFF" opacity="0.035"/>`;

  function footer(p, hint) {
    return txt(16, 586, "‹ TAPE", { fill: p.hint }) +
      txt(196, 586, hint, { anchor: "middle", fill: p.hint });
  }

  function navBar() {
    return `<rect x="0" y="592" width="360" height="48" fill="#000000"/>` +
      `<path d="M80 616 l12 -8 v16 z" fill="#FFFFFF"/>` +
      `<circle cx="180" cy="616" r="9" fill="none" stroke="#FFFFFF" stroke-width="2"/><circle cx="180" cy="616" r="6" fill="#FFFFFF"/>` +
      `<rect x="272" y="609" width="14" height="14" rx="1.5" fill="#FFFFFF"/>`;
  }

  // ---- SOUND ---------------------------------------------------------------------
  const BANDS = ["31", "63", "125", "250", "500", "1k", "2k", "4k", "8k", "16k"];
  const GAINS = [2, 3, 2, 1, 1, 0, -1, -3, -7, -10];

  function curveLcd(p) {
    let s = door(p, 110, 250);
    s += txt(28, 133, "FX", { mono: true, size: 8, fill: p.lcdSub, ls: 0 });
    s += txt(46, 133, "FLAT + TAPE II + DOLBY B + SAT", { mono: true, size: 9.5, fill: p.lcdText, ls: 0 });
    s += txt(332, 133, "−1.1 dB", { anchor: "end", mono: true, size: 9.5, fill: p.lcdText, ls: 0 });
    const X0 = 44, X1 = 330, Y0 = 146, Y1 = 224, mid = (Y0 + Y1) / 2;
    const gx = (i) => X0 + i * (X1 - X0) / 9;
    const gy = (g) => mid - g / 12 * (Y1 - Y0) / 2;
    [12, 6, 0, -6, -12].forEach((g) => {
      s += `<line x1="${X0}" y1="${gy(g)}" x2="${X1}" y2="${gy(g)}" stroke="${p.lcdSub}" stroke-opacity="${g === 0 ? 0.35 : 0.12}" ${g === 0 ? 'stroke-dasharray="3 3"' : ""}/>`;
      if (g % 12 === 0) s += txt(38, gy(g) + 3, (g > 0 ? "+" : g < 0 ? "−" : "") + Math.abs(g), { anchor: "end", mono: true, size: 7.5, fill: p.lcdSub, ls: 0 });
    });
    let d = `M${gx(0)} ${gy(GAINS[0])}`;
    for (let i = 1; i < GAINS.length; i++) {
      const cx = (gx(i - 1) + gx(i)) / 2;
      d += ` C${cx} ${gy(GAINS[i - 1])} ${cx} ${gy(GAINS[i])} ${gx(i)} ${gy(GAINS[i])}`;
    }
    s += `<path d="${d} L${X1} ${mid} L${X0} ${mid} z" fill="${p.lcdText}" fill-opacity="0.08"/>`;
    s += `<path d="${d}" fill="none" stroke="${p.lcdText}" stroke-width="1.6"/>`;
    BANDS.forEach((b, i) => { s += txt(gx(i), 238, b, { anchor: "middle", mono: true, size: 7.5, fill: p.lcdSub, ls: 0 }); });
    return s + glare(110, 250);
  }

  function faders(p) {
    let s = txt(16, 268, "GRAPHIC EQ · 10 BAND", { fill: p.print });
    s += lamp(300, 265, 3, true);
    s += txt(344, 268, "LOCK", { anchor: "end", fill: p.print, weight: 800 });
    const step = 32.8, zero = 330;
    BANDS.forEach((b, i) => {
      const cx = 16 + step * (i + 0.5);
      const g = GAINS[i];
      s += txt(cx, 284, (g > 0 ? "+" : "") + g, { anchor: "middle", mono: true, size: 8.5, fill: p.print, ls: 0 });
      [-12, -6, 0, 6, 12].forEach((t) => {
        const ty = zero - t * 3;
        s += `<line x1="${cx - (t === 0 ? 9 : 6)}" y1="${ty}" x2="${cx + (t === 0 ? 9 : 6)}" y2="${ty}" stroke="${p.printMuted}" stroke-opacity="${t === 0 ? 0.9 : 0.45}"/>`;
      });
      s += `<rect x="${cx - 2}" y="${zero - 38}" width="4" height="76" rx="2" fill="${p.recess}"/>`;
      const cy = zero - g * 3;
      s += `<rect x="${cx - 9}" y="${cy - 6}" width="18" height="12" rx="2" fill="${p.knob}" stroke="${p.knobGroove}" stroke-width="0.8"/>`;
      s += `<line x1="${cx - 6}" y1="${cy}" x2="${cx + 6}" y2="${cy}" stroke="${T}" stroke-width="1.4"/>`;
      s += txt(cx, 384, b, { anchor: "middle", size: 7.5, fill: p.print, ls: 0.4 });
    });
    return s;
  }

  function knob(p, cx, cy, label, value) {
    // value -1..1 maps to -135..135 deg
    let s = "";
    for (let k = 0; k <= 10; k++) {
      const a = (-135 + k * 27) * Math.PI / 180;
      const lit = (k - 5) / 5 <= value + 0.001 && k >= 5 ? true : (k - 5) / 5 >= value - 0.001 && k <= 5;
      const x = cx + Math.sin(a) * 26, y = cy - Math.cos(a) * 26;
      s += `<circle cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="1.6" fill="${lit ? T : p.printMuted}" fill-opacity="${lit ? 1 : 0.5}"/>`;
    }
    s += `<circle cx="${cx}" cy="${cy}" r="19" fill="${p.knobGroove}"/>`;
    s += `<circle cx="${cx}" cy="${cy}" r="17" fill="${p.knob}"/>`;
    for (let r = 0; r < 24; r++) {
      const a = r * 15 * Math.PI / 180;
      s += `<line x1="${cx + Math.sin(a) * 15}" y1="${cy - Math.cos(a) * 15}" x2="${cx + Math.sin(a) * 17}" y2="${cy - Math.cos(a) * 17}" stroke="${p.knobGroove}" stroke-width="0.8"/>`;
    }
    const a = value * 135 * Math.PI / 180;
    s += `<line x1="${cx + Math.sin(a) * 5}" y1="${cy - Math.cos(a) * 5}" x2="${cx + Math.sin(a) * 14}" y2="${cy - Math.cos(a) * 14}" stroke="${T}" stroke-width="2.4" stroke-linecap="round"/>`;
    s += txt(cx, cy + 40, label, { anchor: "middle", size: 8.5, fill: p.print, weight: 800 });
    return s;
  }

  function soundPage(p, v) {
    let s = curveLcd(p) + faders(p);
    [["LOW", 0.4], ["MID", 0.1], ["HI", -0.6], ["FILTER", -0.8]].forEach(([l, val], i) => {
      s += knob(p, 57 + i * 82, 426, l, val);
    });
    s += txt(16, 494, "VOLUME", { fill: p.print });
    s += `<rect x="292" y="482" width="52" height="20" rx="4" fill="${p.recess}"/>`;
    s += txt(326, 496, "62", { anchor: "end", mono: true, size: 11, fill: p.lcdText, ls: 0 });
    s += txt(329, 496, "%", { mono: true, size: 8, fill: p.lcdSub, ls: 0 });
    s += `<rect x="16" y="506" width="328" height="8" rx="4" fill="${p.recess}"/>`;
    for (let k = 0; k <= 10; k++) s += `<line x1="${16 + k * 32.8}" y1="518" x2="${16 + k * 32.8}" y2="${k % 5 ? 521 : 523}" stroke="${p.printMuted}" stroke-opacity="0.7"/>`;
    const vx = 16 + 328 * 0.62;
    s += `<rect x="${vx - 8}" y="499" width="16" height="22" rx="3" fill="${p.knob}" stroke="${p.knobGroove}" stroke-width="0.8"/>`;
    s += `<line x1="${vx}" y1="503" x2="${vx}" y2="517" stroke="${T}" stroke-width="1.6"/>`;
    s += slideSwitch(p, 16, 536, 96, "BYPASS", ["OFF", "ON"], 0);
    s += slideSwitch(p, 132, 536, 96, "SATURATION", ["OFF", "ON"], 1);
    s += slideSwitch(p, 248, 536, 96, "CROSSFEED", ["OFF", "ON"], 0);
    return s + footer(p, "SCROLL FOR VU · CURVES · PRESETS ▾");
  }

  // ---- SETTINGS ------------------------------------------------------------------
  const MENU = [
    ["DISPLAY", "deck style, theme, status bar"],
    ["PLAYBACK", "buttons, dac, replaygain, crossfade, sounds"],
    ["SLEEP TIMER", "fade out and stop"],
    ["OUTPUT", "source, headphone jack, bluetooth"],
    ["LIBRARY", "music folders, rescan, art and lyrics"],
    ["RADIO", "internet stations"],
    ["GESTURES", "double tap and ambient mode"],
    ["ABOUT", "default launcher, studio, version"]
  ];

  function settingsPage(p, v, sel) {
    let s = door(p, 110, 570);
    s += txt(30, 136, "MENU", { mono: true, size: 8, fill: p.lcdSub, ls: 0.6 });
    s += txt(330, 136, `${sel + 1} / ${MENU.length}`, { anchor: "end", mono: true, size: 8, fill: p.lcdSub, ls: 0 });
    s += `<line x1="28" y1="144" x2="332" y2="144" stroke="${p.lcdSub}" stroke-opacity="0.2"/>`;
    MENU.forEach(([title, sub], i) => {
      const top = 150 + i * 51;
      const on = i === sel;
      if (on) s += `<rect x="24" y="${top + 1}" width="312" height="48" rx="3" fill="${p.lcdText}"/>`;
      else if (i > 0 && i !== sel + 1) s += `<line x1="28" y1="${top}" x2="332" y2="${top}" stroke="${p.lcdSub}" stroke-opacity="0.12"/>`;
      const main = on ? p.glass : p.lcdText, dim = on ? p.glass : p.lcdSub;
      s += txt(32, top + 23, String(i + 1).padStart(2, "0"), { mono: true, size: 9, fill: dim, ls: 0, op: on ? 0.7 : 1 });
      s += txt(56, top + 23, title, { mono: true, size: 13, fill: main, ls: 0.4 });
      s += txt(56, top + 39, sub, { size: 9, weight: 400, fill: dim, ls: 0, op: on ? 0.8 : 1 });
      s += `<path d="M322 ${top + 19} l6 6 l-6 6" fill="none" stroke="${main}" stroke-width="1.8"/>`;
    });
    return s + glare(110, 570) + selector(p, 1) + footer(p, "TAP A ROW TO OPEN · BACK RETURNS");
  }

  // ---- APPS ----------------------------------------------------------------------
  const APPS = [
    ["C", null], ["Calculator", "#5B6CF0"], ["Calendar", "#2F80ED"], ["Camera", "#4A4F6A"], ["Chrome", "#E8453C"], ["Clock", "#3D7DF5"],
    ["D", null], ["Drive", "#1FA463"],
    ["F", null], ["Files", "#2A7DE1"], ["FM Radio", "#E85D04"],
    ["G", null], ["Gallery", "#C2185B"], ["Gmail", "#D93025"],
    ["M", null], ["Maps", "#34A853"], ["Messages", "#1A73E8"]
  ];
  const AZ = "#ABCDEFGHIJKLMNOPQRSTUVWXYZ".split("");

  function appsPage(p, v) {
    let s = `<rect x="12" y="110" width="270" height="30" rx="6" fill="${p.recess}"/>`;
    s += txt(24, 129, "SEARCH", { mono: true, size: 8, fill: p.lcdSub, ls: 0.6 });
    s += txt(66, 129.5, "▸", { mono: true, size: 10, fill: p.lcdText, ls: 0 });
    s += `<rect x="78" y="121" width="7" height="11" fill="${p.lcdText}" opacity="0.85"/>`;
    // A–Z quick-jump key, same skin as the transport keys
    s += `<rect x="290" y="108" width="58" height="34" rx="5" fill="#000000" opacity="0.28"/>`;
    s += `<rect x="292" y="122" width="54" height="18" rx="4" fill="${p.keyFront}"/>`;
    s += `<rect x="292" y="110" width="54" height="26" rx="4" fill="${p.keyTop}"/>`;
    s += txt(319, 127, "A–Z", { anchor: "middle", size: 9, weight: 800, fill: p.keyGlyph, ls: 0.8 });

    s += door(p, 148, 570);
    s += `<defs><clipPath id="apps-clip-${v}"><rect x="18" y="154" width="324" height="410" rx="7"/></clipPath></defs><g clip-path="url(#apps-clip-${v})">`;
    let y = 160;
    APPS.forEach(([name, color]) => {
      if (!color) {
        s += txt(30, y + 13, name, { mono: true, size: 11, fill: p.lcdText, ls: 0 });
        s += `<line x1="46" y1="${y + 9}" x2="294" y2="${y + 9}" stroke="${p.lcdText}" stroke-opacity="0.22"/>`;
        y += 20;
        return;
      }
      s += `<rect x="30" y="${y + 5}" width="26" height="26" rx="6" fill="${color}"/>`;
      s += txt(43, y + 22.5, name[0], { anchor: "middle", size: 11, weight: 800, fill: "#FFFFFF", ls: 0 });
      s += txt(66, y + 22.5, name, { size: 12.5, weight: 400, fill: "#E4E4E7", ls: 0.1 });
      y += 36;
    });
    s += `</g>`;
    // Fade the last row so the list reads as scrollable
    s += `<defs><linearGradient id="apps-fade-${v}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="${p.glass}" stop-opacity="0"/><stop offset="1" stop-color="${p.glass}"/></linearGradient></defs>`;
    s += `<rect x="18" y="530" width="286" height="34" fill="url(#apps-fade-${v})"/>`;
    // A–Z rail, needle marks the current letter like the radio dial
    const rx = 322, ry0 = 166, rstep = (556 - ry0) / (AZ.length - 1);
    s += `<rect x="306" y="158" width="30" height="404" rx="4" fill="#FFFFFF" opacity="0.025"/>`;
    AZ.forEach((ch, i) => {
      const on = ch === "C";
      s += txt(rx, ry0 + i * rstep + 3, ch, { anchor: "middle", mono: true, size: 7.5, fill: on ? p.lcdText : p.lcdSub, ls: 0, op: on ? 1 : 0.7 });
    });
    const ny = ry0 + 3 * rstep;
    s += `<rect x="302" y="${ny - 1}" width="10" height="2" fill="${T}"/>`;
    s += `<rect x="302" y="${ny - 7}" width="38" height="14" rx="2" fill="${T}" opacity="0.14"/>`;
    return s + glare(148, 570) + selector(p, 2) + footer(p, "HOLD AN APP FOR OPTIONS");
  }

  window.renderSystemDeck = function (v, page) {
    const p = window.SPINDLE_PALETTES[v];
    let inner;
    if (page === "settings") inner = settingsPage(p, v, 0);
    else if (page === "apps") inner = appsPage(p, v);
    else inner = soundPage(p, v) + selector(p, 0);
    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 360 640" width="360" height="640">` +
      body(p, v) + inner + navBar() + `</svg>`;
  };
})();
