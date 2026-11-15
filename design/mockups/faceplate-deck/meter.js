// Real L/R peak meter + TAPE EQ / DOLBY NR slide switches.
(function () {
  const SEGS = 26, X0 = 34, W = 9, GAP = 2.4, ZERO = 21;
  const segX = (i) => X0 + i * (W + GAP);

  window.renderMeter = function (p, levels) {
    let s = `<rect x="12" y="338" width="336" height="52" rx="6" fill="${p.recess}"/>`;
    [["L", 346, levels.l, levels.lPeak], ["R", 360, levels.r, levels.rPeak]].forEach(([ch, y, lit, peak]) => {
      s += `<text x="22" y="${y + 7}" text-anchor="middle" font-family="Arial,sans-serif" font-size="8" font-weight="700" fill="${p.lcdSub}">${ch}</text>`;
      for (let i = 0; i < SEGS; i++) {
        const on = i < lit || i === peak;
        const hot = i >= ZERO;
        const fill = on ? (hot ? p.ledPeak : p.ledOn) : p.ledOff;
        s += `<rect x="${segX(i)}" y="${y}" width="${W}" height="8" rx="1" fill="${fill}"/>`;
      }
      const clip = peak >= ZERO;
      s += `<circle cx="338" cy="${y + 4}" r="3" fill="${clip ? p.ledPeak : p.ledOff}"/>`;
    });
    [["-20", 0], ["-10", 8], ["-7", 11], ["-5", 14], ["-3", 17], ["0", ZERO], ["+3", 25]].forEach(([t, i]) => {
      s += `<text x="${segX(i) + W / 2}" y="384" text-anchor="middle" font-family="Arial,sans-serif" font-size="7.5" font-weight="700" fill="${i >= ZERO ? p.ledPeak : p.lcdSub}">${t}</text>`;
    });

    return s;
  };

  function slideSwitch(p, x, w, title, stops, active) {
    let s = `<text x="${x}" y="406" font-family="Arial,sans-serif" font-size="8" font-weight="700" letter-spacing="1.2" fill="${p.print}">${title}</text>`;
    const step = w / stops.length;
    stops.forEach((t, i) => {
      const cx = x + step * i + step / 2;
      s += `<text x="${cx}" y="419" text-anchor="middle" font-family="Arial,sans-serif" font-size="8.5" font-weight="${i === active ? 800 : 600}" fill="${i === active ? p.print : p.printMuted}">${t}</text>`;
    });
    s += `<rect x="${x}" y="424" width="${w}" height="16" rx="8" fill="${p.recess}"/>`;
    const kx = x + step * active + 3, kw = step - 6;
    s += `<rect x="${kx}" y="422" width="${kw}" height="20" rx="4" fill="${p.knob}" stroke="${p.knobGroove}" stroke-width="0.8"/>`;
    for (let g = -2; g <= 2; g++) {
      const gx = kx + kw / 2 + g * 4;
      s += `<line x1="${gx}" y1="426" x2="${gx}" y2="438" stroke="${p.knobGroove}" stroke-width="1"/>`;
    }
    return s;
  }

  window.renderSwitches = function (p) {
    let s = "";
    s += slideSwitch(p, 16, 160, "TAPE EQ", ["FLAT", "I", "II", "IV"], 0);
    s += slideSwitch(p, 196, 148, "DOLBY NR", ["OFF", "B", "C"], 0);
    // DIRECT lamp: lit only when both switches bypass all processing
    s += `<circle cx="286" cy="403" r="3" fill="#00E676"/><circle cx="286" cy="403" r="6" fill="#00E676" opacity="0.2"/>`;
    s += `<text x="344" y="406" text-anchor="end" font-family="Arial,sans-serif" font-size="8" font-weight="800" letter-spacing="1.2" fill="${p.print}">DIRECT</text>`;
    return s;
  };
})();
