// Piano-key transport, nav bar, and full-screen assembly.
(function () {
  const glyphs = {
    rew: (cx, cy, c) => `<path d="M${cx - 1} ${cy} l10 -7 v14 z M${cx - 11} ${cy} l10 -7 v14 z" fill="${c}"/>`,
    play: (cx, cy, c) => `<path d="M${cx - 6} ${cy - 9} l15 9 l-15 9 z" fill="${c}"/>`,
    ff: (cx, cy, c) => `<path d="M${cx + 1} ${cy} l-10 -7 v14 z M${cx + 11} ${cy} l-10 -7 v14 z" fill="${c}"/>`,
    stop: (cx, cy, c) => `<rect x="${cx - 7}" y="${cy - 7}" width="14" height="14" rx="1.5" fill="${c}"/>`,
    eject: (cx, cy, c) => `<path d="M${cx} ${cy - 9} l9 9 h-18 z" fill="${c}"/><rect x="${cx - 9}" y="${cy + 3}" width="18" height="3.5" rx="1" fill="${c}"/>`
  };
  const KEYS = [["REW", "rew"], ["PLAY", "play"], ["FF", "ff"], ["STOP", "stop"], ["EJECT", "eject"]];

  window.renderKeys = function (p, latched) {
    let s = "";
    const W = 62.4, G = 6;
    KEYS.forEach(([label, g], i) => {
      const x = 12 + i * (W + G), cx = x + W / 2;
      const down = g === latched;
      const isPlay = g === "play";
      const top = isPlay ? p.playTop : p.keyTop;
      const front = isPlay ? p.playFront : p.keyFront;
      const glyph = isPlay ? p.playGlyph : p.keyGlyph;
      const ty = down ? 488 : 478, th = down ? 68 : 70, fh = down ? 8 : 18;

      s += `<text x="${cx}" y="470" text-anchor="middle" font-family="Arial,sans-serif" font-size="8.5" font-weight="800" letter-spacing="1.2" fill="${p.print}">${label}</text>`;
      // Key well
      s += `<rect x="${x - 2}" y="474" width="${W + 4}" height="98" rx="6" fill="#000000" opacity="0.28"/>`;
      // Front face (travel) then top face
      s += `<rect x="${x}" y="${ty + th - 6}" width="${W}" height="${fh + 6}" rx="5" fill="${front}"/>`;
      s += `<rect x="${x}" y="${ty}" width="${W}" height="${th}" rx="5" fill="${top}"/>`;
      s += `<rect x="${x + 1}" y="${ty + 1}" width="${W - 2}" height="2" rx="1" fill="${isPlay ? "#FDBA74" : p.keyTopEdge}" opacity="0.7"/>`;
      // Grip ridges
      for (let r = 0; r < 3; r++) {
        s += `<line x1="${x + 14}" y1="${ty + 9 + r * 4}" x2="${x + W - 14}" y2="${ty + 9 + r * 4}" stroke="${front}" stroke-width="1" opacity="0.55"/>`;
      }
      s += glyphs[g](cx, ty + th / 2 + 6, glyph);
    });
    s += `<text x="16" y="586" font-family="Arial,sans-serif" font-size="8" font-weight="700" letter-spacing="1.2" fill="${p.hint}">‹ RADIO</text>`;
    s += `<text x="180" y="586" text-anchor="middle" font-family="Arial,sans-serif" font-size="8" font-weight="700" letter-spacing="1.2" fill="${p.hint}">TAP CASSETTE FOR J-CARD</text>`;
    s += `<text x="344" y="586" text-anchor="end" font-family="Arial,sans-serif" font-size="8" font-weight="700" letter-spacing="1.2" fill="${p.hint}">SYSTEM ›</text>`;
    return s;
  };

  function navBar() {
    return `<rect x="0" y="592" width="360" height="48" fill="#000000"/>` +
      `<path d="M80 616 l12 -8 v16 z" fill="#FFFFFF"/>` +
      `<circle cx="180" cy="616" r="9" fill="none" stroke="#FFFFFF" stroke-width="2"/><circle cx="180" cy="616" r="6" fill="#FFFFFF"/>` +
      `<rect x="272" y="609" width="14" height="14" rx="1.5" fill="#FFFFFF"/>`;
  }

  window.renderDeck = function (v) {
    const p = window.SPINDLE_PALETTES[v];
    const track = {
      title: "(You Drive Me) Crazy", artist: "Britney Spears", album: "...Baby One More Time",
      counter: "047", format: "MP3 · 160 kbps", rate: "44.1 kHz · 16 bit"
    };
    const levels = { l: 19, lPeak: 22, r: 16, rPeak: 19 };
    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 360 640" width="360" height="640">` +
      window.renderBody(p, v) +
      window.renderCassetteDoor(p, track) +
      window.renderInfoStrip(p, track) +
      window.renderMeter(p, levels) +
      window.renderSwitches(p) +
      window.renderKeys(p, "play") +
      navBar() +
      `</svg>`;
  };
})();
