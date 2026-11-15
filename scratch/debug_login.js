const fs = require('fs');

async function debugLogin() {
    const html = fs.readFileSync('painel-admin/index.html', 'utf8');
    const url = html.match(/const SUPABASE_URL = "([^"]+)"/)[1];
    const key = html.match(/const SUPABASE_KEY = "([^"]+)"/)[1];

    const res = await fetch(`${url}/rest/v1/rpc/panel_login_dashboard`, {
        method: "POST",
        headers: {
            "apikey": key,
            "Authorization": `Bearer ${key}`,
            "Content-Type": "application/json"
        },
        body: JSON.stringify({
            p_reseller_id: "dream",
            p_password: "dream"
        })
    });
    const txt = await res.text();
    console.log("Response text:", txt);
}

debugLogin().catch(console.error);
