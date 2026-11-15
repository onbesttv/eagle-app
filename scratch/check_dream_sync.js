const fs = require('fs');

async function checkDream() {
    const html = fs.readFileSync('painel-admin/index.html', 'utf8');
    const url = html.match(/const SUPABASE_URL = "([^"]+)"/)[1];
    const key = html.match(/const SUPABASE_KEY = "([^"]+)"/)[1];

    // Try rpc app_sync or admin_get_dashboard
    const res = await fetch(`${url}/rest/v1/rpc/app_sync`, {
        method: "POST",
        headers: {
            "apikey": key,
            "Authorization": `Bearer ${key}`,
            "Content-Type": "application/json"
        },
        body: JSON.stringify({
            p_device_id: "test",
            p_username: "",
            p_service: "best",
            p_device_name: "test",
            p_app_version: "3.17",
            p_reseller_id: "dream"
        })
    });
    const data = await res.json();
    console.log("app_sync for dream:", data);
}

checkDream().catch(console.error);
