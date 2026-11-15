// Radio deck mockup - tuner window: slide-rule dial + LCD.
// Coordinates are dp on a 360x640 canvas (SO-02J: 720x1280 @ xhdpi).
// Band limits mirror AnalogFmEngine (87.5-108.0 MHz, 0.1 MHz step).
(function () {
  const FMIN = 87.5, FMAX = 108.0, X0 = 44, X1 = 316;
  const fx = (f) => X0 + (f - FMIN) / (FMAX - FMIN) * (X1 - X0);
  const MONO = "Consolas,monospace", SANS = "Arial,sans-serif";

  function dial(p, v, st) {
    let s = "";
    s += `<defs><radialGradient id="backlight-${v}" cx="0.5" cy="0.55" r="0.7">` +
      `<stop offset="0" stop-color="#FDE68A" stop-opacity="0.10"/><stop offset="1" stop-color="#FDE68A" stop-opacity="0"/></radialGradient></defs>`;
    s += `<rect x="26" y="66" width="308" height="100" rx="3" fill="#0A0B10"/>`;
    s += `<rect x="26" y="66" width="308" height="100" rx="3" fill="url(#backlight-${v})"/>`;

    s += `<text x="34" y="82" font-family="${SANS}" font-size="8" font-weight="800" letter-spacing="1.4" fill="${p.lcdText}">FM</text>`;
    s += `<text x="326" y="82" text-anchor="end" font-family="${SANS}" font-size="8" font-weight="700" letter-spacing="1.4" fill="${p.lcdSub}">MHz</text>`;

    // Scale: major every 2 MHz, minor every 0.5 MHz
    s += `<line x1="${X0}" y1="114" x2="${X1}" y2="114" stroke="${p.lcdText}" stroke-opacity="0.55" stroke-width="1"/>`;
    for (let f = 88; f <= 108.001; f += 0.5) {
      const x = fx(f).toFixed(1);
      const major = Math.abs(f % 2) < 0.01;
      s += `<line x1="${x}" y1="${major ? 102 : 108}" x2="${x}" y2="114" stroke="${p.lcdText}" stroke-opacity="${major ? 0.85 : 0.4}" stroke-width="${major ? 1.2 : 0.8}"/>`;
      if (major) {
        s += `<text x="${x}" y="97" text-anchor="middle" font-family="${MONO}" font-size="10" font-weight="700" fill="${p.lcdText}" fill-opacity="0.9">${f}</text>`;
      }
    }

    // Carrier stations from AnalogFmEngine.carrierStations
    st.stations.forEach((c) => {
      const x = fx(c.f), on = c.f === st.freq && !st.freqDim;
      s += `<path d="M${x.toFixed(1)} 118 l4 6 h-8 z" fill="${on ? "#F97316" : p.lcdText}" fill-opacity="${on ? 1 : 0.6}"/>`;
      s += `<text x="${x.toFixed(1)}" y="136" text-anchor="middle" font-family="${SANS}" font-size="7.5" font-weight="800" letter-spacing="0.8" fill="${on ? p.lcdText : p.lcdSub}">${c.call}</text>`;
    });

    // Needle. "trail" = sweeping upward during SCAN, "parked" = no antenna.
    const nx = fx(st.freq);
    if (st.needle === "trail") {
      [[6, 0.45], [12, 0.26], [18, 0.14], [24, 0.06]].forEach(([dx, o]) => {
        s += `<rect x="${(nx - dx - 1).toFixed(1)}" y="70" width="2" height="92" fill="#F97316" opacity="${o}"/>`;
      });
    }
    const parked = st.needle === "parked";
    const nc = parked ? p.lcdSub : "#F97316";
    if (!parked) s += `<rect x="${(nx - 6).toFixed(1)}" y="70" width="12" height="92" fill="#F97316" opacity="0.14"/>`;
    s += `<rect x="${(nx - 1).toFixed(1)}" y="70" width="2" height="92" fill="${nc}" opacity="${parked ? 0.5 : 1}"/>`;
    s += `<rect x="${(nx - 4).toFixed(1)}" y="70" width="8" height="3" rx="1" fill="${nc}" opacity="${parked ? 0.5 : 1}"/>`;

    s += `<text x="34" y="158" font-family="${SANS}" font-size="7" font-weight="700" letter-spacing="1.2" fill="${p.lcdSub}">DRAG DIAL TO TUNE</text>`;
    s += `<text x="326" y="158" text-anchor="end" font-family="${MONO}" font-size="8" fill="${p.lcdSub}">${st.region}</text>`;
    return s;
  }

  function lcd(p, st) {
    let s = `<rect x="26" y="174" width="308" height="88" rx="3" fill="${p.recess}"/>`;
    const lit = p.lcdText, off = p.ledOff;

    // Left column: band badge, preset slot, pilot indicator
    s += `<rect x="34" y="184" width="30" height="14" rx="2" fill="none" stroke="${lit}" stroke-width="1"/>`;
    s += `<text x="49" y="194.5" text-anchor="middle" font-family="${MONO}" font-size="9.5" font-weight="700" fill="${lit}">${st.band}</text>`;
    if (st.warn) {
      s += `<rect x="70" y="184" width="46" height="14" rx="2" fill="${p.ledPeak}" fill-opacity="0.12" stroke="${p.ledPeak}" stroke-width="1"/>`;
      s += `<text x="93" y="194.5" text-anchor="middle" font-family="${MONO}" font-size="9.5" font-weight="700" fill="${p.ledPeak}">${st.warn}</text>`;
    }
    s += `<text x="34" y="214" font-family="${MONO}" font-size="10" font-weight="700" fill="${st.preset >= 0 ? lit : off}">P${st.preset >= 0 ? st.preset + 1 : "-"}</text>`;
    s += `<text x="34" y="230" font-family="${MONO}" font-size="8.5" font-weight="700" fill="${st.pilot ? lit : off}">${st.pilotLabel}</text>`;

    // Genre, top right
    s += `<text x="326" y="194" text-anchor="end" font-family="${SANS}" font-size="7.5" font-weight="700" letter-spacing="1" fill="${p.lcdSub}">${st.genre}</text>`;

    // Big frequency on ghost segments
    const freq = st.freq.toFixed(1);
    s += `<text x="282" y="232" text-anchor="end" font-family="${MONO}" font-size="44" font-weight="700" fill="${off}">188.8</text>`;
    s += `<text x="282" y="232" text-anchor="end" font-family="${MONO}" font-size="44" font-weight="700" fill="${st.freqDim ? p.lcdSub : lit}" fill-opacity="${st.freqDim ? 0.7 : 1}">${freq}</text>`;
    s += `<text x="288" y="232" font-family="${MONO}" font-size="11" font-weight="700" fill="${p.lcdSub}">MHz</text>`;

    // RDS / ICY line
    s += `<line x1="34" y1="241" x2="326" y2="241" stroke="${p.lcdSub}" stroke-opacity="0.18"/>`;
    s += `<text x="34" y="255" font-family="${MONO}" font-size="8" font-weight="700" fill="${p.lcdSub}">${st.lineTag}</text>`;
    s += `<text x="62" y="255" font-family="${MONO}" font-size="9.5" font-weight="700" fill="${st.lineWarn ? p.ledPeak : lit}">${st.line}</text>`;
    return s;
  }

  window.renderTunerWindow = function (p, v, st) {
    let s = "";
    s += `<rect x="12" y="52" width="336" height="224" rx="10" fill="${p.doorFrame}"/>`;
    s += `<rect x="18" y="58" width="324" height="212" rx="7" fill="${p.glass}"/>`;
    s += dial(p, v, st);
    s += lcd(p, st);
    // Restrained glass glare, same as the cassette door
    s += `<path d="M18 58 h120 l-70 212 h-50 z" fill="#FFFFFF" opacity="0.035"/>`;
    return s;
  };
})();
