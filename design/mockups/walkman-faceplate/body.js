// Chassis body, header band, counter, direction and format readout.
(function () {
  function brush(id, p) {
    let lines = "";
    let seed = 7;
    const rnd = () => ((seed = (seed * 9301 + 49297) % 233280) / 233280);
    for (let y = 0; y < 592; y += 1.5) {
      const light = rnd() > 0.5;
      const a = (light ? p.brushLight : p.brushDark) * rnd();
      lines += `<line x1="0" y1="${y}" x2="360" y2="${y}" stroke="${light ? "#FFFFFF" : "#000000"}" stroke-opacity="${a.toFixed(3)}" stroke-width="0.6"/>`;
    }
    return `<g id="${id}">${lines}</g>`;
  }

  window.renderBody = function (p, v) {
    let s = "";
    s += `<defs><linearGradient id="body-${v}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="${p.bodyTop}"/><stop offset="1" stop-color="${p.bodyBot}"/></linearGradient></defs>`;
    s += `<rect x="0" y="0" width="360" height="592" fill="url(#body-${v})"/>`;
    s += brush(`brush-${v}`, p);

    // Header band
    s += `<rect x="0" y="0" width="360" height="44" fill="${p.band}"/>`;
    s += `<text x="16" y="29" font-family="Arial,Helvetica,sans-serif" font-size="17" font-weight="800" letter-spacing="5" fill="${p.wordmark}">SPINDLE</text>`;
    s += `<rect x="134" y="24" width="5" height="5" fill="#F97316"/>`;
    s += `<text x="344" y="20" text-anchor="end" font-family="Arial,sans-serif" font-size="8" font-weight="700" letter-spacing="1.4" fill="${p.bandSub}">STEREO CASSETTE PLAYER</text>`;
    s += `<circle cx="308" cy="31" r="3" fill="#00E676"/><circle cx="308" cy="31" r="6" fill="#00E676" opacity="0.18"/>`;
    s += `<text x="344" y="34" text-anchor="end" font-family="Arial,sans-serif" font-size="8" font-weight="700" letter-spacing="1.4" fill="${p.bandSub}">RUN</text>`;
    return s;
  };

  window.renderInfoStrip = function (p, track) {
    let s = "";
    const label = (x, y, t, anchor) =>
      `<text x="${x}" y="${y}" ${anchor ? `text-anchor="${anchor}"` : ""} font-family="Arial,sans-serif" font-size="8" font-weight="700" letter-spacing="1.2" fill="${p.print}">${t}</text>`;

    // 3-digit mechanical counter
    s += label(16, 293, "COUNTER");
    track.counter.split("").forEach((d, i) => {
      const x = 16 + i * 20;
      s += `<rect x="${x}" y="298" width="18" height="26" rx="2" fill="${p.recess}"/>`;
      s += `<rect x="${x}" y="298" width="18" height="6" fill="#FFFFFF" opacity="0.06"/>`;
      s += `<rect x="${x}" y="318" width="18" height="6" fill="#000000" opacity="0.35"/>`;
      s += `<text x="${x + 9}" y="317" text-anchor="middle" font-family="Consolas,monospace" font-size="17" font-weight="700" fill="#FAFAF9">${d}</text>`;
    });
    s += `<circle cx="92" cy="311" r="8" fill="${p.keyTop}" stroke="${p.keyFront}" stroke-width="1.5"/>`;
    s += label(92, 330, "RESET", "middle");

    // Auto-reverse direction
    s += label(160, 293, "DIRECTION", "middle");
    s += `<path d="M142 311 l10 -7 v14 z" fill="${p.printMuted}" opacity="0.45"/>`;
    s += `<path d="M178 311 l-10 -7 v14 z" fill="#F97316"/>`;
    s += label(160, 330, "SIDE A", "middle");

    // Format readout
    s += `<rect x="216" y="290" width="132" height="40" rx="4" fill="${p.recess}"/>`;
    s += `<text x="226" y="307" font-family="Consolas,monospace" font-size="11" font-weight="700" fill="${p.lcdText}">${track.format}</text>`;
    s += `<text x="226" y="322" font-family="Consolas,monospace" font-size="9.5" fill="${p.lcdSub}">${track.rate}</text>`;
    return s;
  };
})();
