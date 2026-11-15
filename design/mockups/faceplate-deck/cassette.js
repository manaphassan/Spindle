// Cassette door + horizontal cassette seen through the smoked window.
// Coordinates are dp on a 360x640 canvas (SO-02J: 720x1280 @ xhdpi).
(function () {
  function reel(cx, cy, packR, p, spin) {
    let s = "";
    // Tape pack with faint winding rings
    s += `<circle cx="${cx}" cy="${cy}" r="${packR}" fill="#4A3426"/>`;
    for (let r = 18; r < packR; r += 4) {
      s += `<circle cx="${cx}" cy="${cy}" r="${r}" fill="none" stroke="#5C4231" stroke-width="0.6" opacity="0.7"/>`;
    }
    // Hub with 6 drive teeth; one tangerine notch shows rotation
    s += `<circle cx="${cx}" cy="${cy}" r="15" fill="#F5F5F4"/>`;
    s += `<circle cx="${cx}" cy="${cy}" r="9" fill="#23252F"/>`;
    for (let i = 0; i < 6; i++) {
      const a = (spin + i * 60) * Math.PI / 180;
      const x = cx + Math.cos(a) * 7.2, y = cy + Math.sin(a) * 7.2;
      const fill = i === 0 ? "#F97316" : "#F5F5F4";
      s += `<rect x="${x - 1.6}" y="${y - 2.6}" width="3.2" height="5.2" rx="0.8" fill="${fill}" transform="rotate(${spin + i * 60 + 90} ${x} ${y})"/>`;
    }
    s += `<circle cx="${cx}" cy="${cy}" r="3.2" fill="#0E0F14"/>`;
    return s;
  }

  window.renderCassetteDoor = function (p, track) {
    let s = "";
    // Door frame + smoked glass
    s += `<rect x="12" y="52" width="336" height="224" rx="10" fill="${p.doorFrame}"/>`;
    s += `<rect x="18" y="58" width="324" height="212" rx="7" fill="${p.glass}"/>`;

    // Cassette shell
    s += `<rect x="26" y="64" width="308" height="198" rx="8" fill="#2E3039" stroke="#3A3D48" stroke-width="1"/>`;
    // Screws
    [[34, 72], [326, 72], [34, 254], [326, 254]].forEach(([x, y]) => {
      s += `<circle cx="${x}" cy="${y}" r="3.4" fill="#5A5E6B"/><path d="M${x - 2} ${y}h4M${x} ${y - 2}v4" stroke="#2E3039" stroke-width="0.9"/>`;
    });

    // Paper label
    s += `<rect x="42" y="72" width="276" height="136" rx="4" fill="#FDE68A"/>`;
    s += `<rect x="42" y="180" width="276" height="12" fill="#F97316"/>`;
    s += `<rect x="42" y="192" width="276" height="3" fill="#2A2E45"/>`;
    // Handwritten title + artist
    s += `<text x="54" y="96" font-family="'Segoe Print','Bradley Hand','Comic Sans MS',cursive" font-size="14" font-weight="700" fill="#2A2E45">${track.title}</text>`;
    s += `<text x="54" y="112" font-family="'Segoe Print','Bradley Hand','Comic Sans MS',cursive" font-size="10" fill="#4A4F6A">${track.artist} · ${track.album}</text>`;
    // Side letter
    s += `<text x="58" y="166" font-family="Arial Black,Arial,sans-serif" font-size="30" font-weight="900" fill="#2A2E45">A</text>`;
    // Album-art sticker
    s += `<defs><linearGradient id="art" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#FB7185"/><stop offset="0.55" stop-color="#9F7AEA"/><stop offset="1" stop-color="#2A2E45"/></linearGradient></defs>`;
    s += `<g transform="rotate(4 296 148)"><rect x="275" y="127" width="42" height="42" fill="#FFFFFF"/><rect x="278" y="130" width="36" height="36" fill="url(#art)"/></g>`;
    // Footer print on label
    s += `<text x="54" y="204" font-family="Arial,sans-serif" font-size="7" font-weight="700" letter-spacing="1.2" fill="#2A2E45">C-90 · 45 MIN PER SIDE · NORMAL POSITION</text>`;

    // Label window showing tape packs + hubs (supply left larger at ~30%)
    s += `<clipPath id="win"><rect x="100" y="124" width="164" height="48" rx="24"/></clipPath>`;
    s += `<rect x="100" y="124" width="164" height="48" rx="24" fill="#16171D"/>`;
    s += `<g clip-path="url(#win)">`;
    s += `<rect x="100" y="124" width="164" height="48" fill="#1B1C22"/>`;
    s += `<g opacity="0.95">${reel(132, 148, 40, p, 18)}${reel(232, 148, 26, p, 52)}</g>`;
    // Window ruler ticks
    for (let i = 0; i <= 10; i++) {
      const x = 152 + i * 6;
      s += `<line x1="${x}" y1="126" x2="${x}" y2="${i % 5 === 0 ? 133 : 130}" stroke="#FDE68A" stroke-width="0.6" opacity="0.6"/>`;
    }
    s += `</g>`;
    s += `<rect x="100" y="124" width="164" height="48" rx="24" fill="none" stroke="#2A2E45" stroke-width="1.4"/>`;

    // Head-access trapezoid
    s += `<path d="M98 222 L262 222 L282 262 L78 262 Z" fill="#262830" stroke="#3A3D48" stroke-width="1"/>`;
    s += `<circle cx="124" cy="244" r="5" fill="#0E0F14"/><circle cx="236" cy="244" r="5" fill="#0E0F14"/>`;
    s += `<rect x="102" y="240" width="7" height="8" fill="#0E0F14"/><rect x="251" y="240" width="7" height="8" fill="#0E0F14"/>`;
    s += `<rect x="170" y="236" width="20" height="16" rx="2" fill="#0E0F14"/><rect x="175" y="248" width="10" height="4" fill="#C9B79C"/>`;
    // Exposed tape along the bottom edge
    s += `<line x1="82" y1="259.5" x2="278" y2="259.5" stroke="#5C4231" stroke-width="2.2"/>`;

    // Restrained glass reflection
    s += `<defs><linearGradient id="glare" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#FFFFFF" stop-opacity="0.10"/><stop offset="0.45" stop-color="#FFFFFF" stop-opacity="0.02"/><stop offset="1" stop-color="#FFFFFF" stop-opacity="0"/></linearGradient></defs>`;
    s += `<rect x="18" y="58" width="324" height="212" rx="7" fill="url(#glare)"/>`;
    return s;
  };
})();
