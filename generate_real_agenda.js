const fs = require('fs');
const https = require('https');

function fetchHtml(url) {
  return new Promise((resolve, reject) => {
    https.get(url, { headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)' } }, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => resolve(data));
    }).on('error', reject);
  });
}

function decodeHtml(html) {
  return html
    .replace(/&#233;/g, 'é')
    .replace(/&#225;/g, 'á')
    .replace(/&#227;/g, 'ã')
    .replace(/&#243;/g, 'ó')
    .replace(/&#237;/g, 'í')
    .replace(/&#250;/g, 'ú')
    .replace(/&#234;/g, 'ê')
    .replace(/&#226;/g, 'â')
    .replace(/&#231;/g, 'ç')
    .replace(/&#193;/g, 'Á')
    .replace(/&#195;/g, 'Ã')
    .replace(/&#201;/g, 'É')
    .replace(/&#211;/g, 'Ó')
    .replace(/&amp;/g, '&')
    .replace(/&quot;/g, '"');
}

async function main() {
  let html = '';
  try {
    console.log('Fetching live games from ondebola.com...');
    html = await fetchHtml('https://ondebola.com');
  } catch (e) {
    console.log('Failed to fetch online, using sample fallback:', e.message);
    if (fs.existsSync('ondebola_sample.html')) {
      html = fs.readFileSync('ondebola_sample.html', 'utf8');
    }
  }

  const rows = html.match(/<tr data-game-id=[\s\S]*?<\/tr>/gi) || [];
  const games = [];
  const now = new Date();
  const currentHour = now.getHours();
  const currentMinute = now.getMinutes();
  const currentMinutesSinceMidnight = currentHour * 60 + currentMinute;

  rows.forEach((row) => {
    const colDate = (row.match(/<td class="col-date">([\s\S]*?)<\/td>/i) || [])[1] || '';
    const colDesc = (row.match(/<td class="col-desc">([\s\S]*?)<\/td>/i) || [])[1] || '';
    const colChannel = (row.match(/<td class="col-channel">([\s\S]*?)<\/td>/i) || [])[1] || '';

    if (!colDesc) return;

    // Teams
    const teams = colDesc.match(/<span class="team">([\s\S]*?)<\/span>/gi) || [];
    const home = decodeHtml((teams[0] || '').replace(/<[^>]+>/g, '').trim());
    const away = decodeHtml((teams[1] || '').replace(/<[^>]+>/g, '').trim());

    if (!home || !away) return;

    // League
    const extraMatch = colDesc.match(/<span class="extra">([\s\S]*?)<\/span>/i);
    let ligaRaw = extraMatch ? decodeHtml(extraMatch[1].replace(/<[^>]+>/g, '').trim()) : '';

    // Date and Time
    const cleanDate = decodeHtml(colDate.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim());
    const timeMatch = cleanDate.match(/\b(\d{1,2}:\d{2})\b/);
    const hora = timeMatch ? timeMatch[1] : '';

    // Extract exact calendar date (ex: "10 Nov", "11 Nov", "12 Nov")
    const dateMatch = cleanDate.match(/\b(\d{1,2}\s+[a-zA-Záéíóúçãõ]+)\b/);
    const day = dateMatch ? dateMatch[1] : 'Hoje';

    // Determine if today
    const isToday = colDate.includes('gametoday') || cleanDate.toLowerCase().includes('hoje');

    // Filter out games that already finished today (more than 2h15m after start)
    if (isToday && hora) {
      const parts = hora.split(':').map(Number);
      if (parts.length === 2) {
        const gameMinutes = parts[0] * 60 + parts[1];
        // If game started more than 135 minutes ago, it has already finished
        if (currentMinutesSinceMidnight > gameMinutes + 135) {
          return; // Skip finished match
        }
      }
    }

    // Channels
    const channelMatches = colChannel.match(/<a[^>]*title="[^"]*"[^>]*>([\s\S]*?)<\/a>/gi) || [];
    let channelNames = channelMatches.map(m => decodeHtml(m.replace(/<[^>]+>/g, '').trim()));
    let cleanCanal = channelNames[0] || '';

    // Format channel nicely
    cleanCanal = cleanCanal
      .replace(/sport\.tv\s*1/i, 'Sport TV 1')
      .replace(/sport\.tv\s*2/i, 'Sport TV 2')
      .replace(/sport\.tv\s*3/i, 'Sport TV 3')
      .replace(/sport\.tv\s*4/i, 'Sport TV 4')
      .replace(/sport\.tv\s*5/i, 'Sport TV 5')
      .replace(/sport\.tv\s*6/i, 'Sport TV 6')
      .replace(/dazn\s*1/i, 'DAZN 1')
      .replace(/dazn\s*2/i, 'DAZN 2')
      .replace(/dazn\s*3/i, 'DAZN 3')
      .replace(/dazn\s*4/i, 'DAZN 4')
      .replace(/dazn\s*5/i, 'DAZN 5')
      .replace(/dazn\s*6/i, 'DAZN 6')
      .replace(/canal\s*11/i, 'Canal 11')
      .replace(/benfica\s*tv|btv/i, 'BTV 1')
      .replace(/porto\s*canal/i, 'Porto Canal')
      .replace(/rtp\s*1/i, 'RTP 1')
      .replace(/sic/i, 'SIC')
      .replace(/tvi/i, 'TVI');

    if (!cleanCanal || cleanCanal.toLowerCase().includes('betano') || cleanCanal.toLowerCase().includes('livestream')) {
      cleanCanal = 'A anunciar';
    }

    // Format league nicely
    let cleanLiga = ligaRaw;
    if (ligaRaw.toLowerCase().includes('liga portugal')) cleanLiga = 'Liga Portugal';
    else if (ligaRaw.toLowerCase().includes('inglaterra') || ligaRaw.toLowerCase().includes('premier')) cleanLiga = 'Premier League';
    else if (ligaRaw.toLowerCase().includes('espanha') || ligaRaw.toLowerCase().includes('laliga')) cleanLiga = 'La Liga';
    else if (ligaRaw.toLowerCase().includes('itália') || ligaRaw.toLowerCase().includes('serie a')) cleanLiga = 'Serie A';
    else if (ligaRaw.toLowerCase().includes('alemanha') || ligaRaw.toLowerCase().includes('bundesliga')) cleanLiga = 'Bundesliga';
    else if (ligaRaw.toLowerCase().includes('champions')) cleanLiga = 'Champions League';
    else if (ligaRaw.toLowerCase().includes('europa')) cleanLiga = 'Liga Europa';
    else if (ligaRaw.toLowerCase().includes('futsal')) cleanLiga = 'Futsal';

    games.push({
      id: String(games.length + 1),
      data: day,
      hora: hora,
      liga: cleanLiga || 'Futebol',
      equipa_casa: home,
      equipa_fora: away,
      canal: cleanCanal,
      destaque: cleanLiga.includes('Portugal') || cleanCanal.includes('Sport TV 1') || cleanCanal.includes('DAZN 1')
    });
  });

  console.log('Successfully parsed active/future games:', games.length);
  const output = {
    updated_at: new Date().toISOString(),
    jogos: games
  };

  fs.writeFileSync('app/src/main/assets/agenda_jogos.json', JSON.stringify(output, null, 2));
  if (!fs.existsSync('data')) fs.mkdirSync('data');
  fs.writeFileSync('data/agenda_jogos.json', JSON.stringify(output, null, 2));
  console.log('Saved to app/src/main/assets/agenda_jogos.json and data/agenda_jogos.json');
}

main();
