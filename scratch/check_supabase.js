const fs = require('fs');

async function checkDream() {
    const html = fs.readFileSync('painel-admin/index.html', 'utf8');
    const urlMatch = html.match(/const SUPABASE_URL = "([^"]+)"/);
    const keyMatch = html.match(/const SUPABASE_KEY = "([^"]+)"/);
    if (!urlMatch || !keyMatch) {
        console.log("Could not find Supabase credentials");
        return;
    }
    const url = urlMatch[1];
    const key = keyMatch[1];

    console.log("Connecting to:", url);
    // Fetch resellers
    const res = await fetch(`${url}/rest/v1/resellers?select=*`, {
        headers: {
            "apikey": key,
            "Authorization": `Bearer ${key}`
        }
    });
    const resellers = await res.json();
    console.log("Resellers found:", resellers.map(r => ({ id: r.reseller_id, announcement: r.announcement, best_url: r.best_url })));
}

checkDream().catch(console.error);
