const https = require("https");

// Vamos chamar panel_save_own_config para suporte24 com a senha "suporte24"
// Deixando apenas os DNS: http://lux2026.xyz:8080 e http://juveleoscp.xyz:8080 (sem bmgroup2026.com)
const announcement = "||DNS_LIST:http://lux2026.xyz:8080,http://juveleoscp.xyz:8080 ||VPN:pt,spain,france,uk,nl_amsterdam,de-frankfurt,swiss,italy||VPN_AUTH:p7426501:on4q5FT9vx||VPN_USERS:restricted|us2mdz4s";

const payload = JSON.stringify({
  p_reseller_id: "suporte24",
  p_password: "suporte24",
  p_best_url: "http://juveleoscp.xyz:8080",
  p_best2_url: "http://juveleoscp.xyz:8080",
  p_announcement: announcement,
  p_maintenance_mode: false,
  p_new_password: ""
});

const req = https.request({
  hostname: "myreymyqsnikjdhnstjn.supabase.co",
  path: "/rest/v1/rpc/panel_save_own_config",
  method: "POST",
  headers: {
    "apikey": "sb_publishable_Jsb0b0Kcf5-bv6GCUFu3KA_wO1G9HjM",
    "Authorization": "Bearer sb_publishable_Jsb0b0Kcf5-bv6GCUFu3KA_wO1G9HjM",
    "Content-Type": "application/json"
  }
}, res => {
  let d = "";
  res.on("data", c => d += c);
  res.on("end", () => {
    console.log("SAVE STATUS:", res.statusCode, d);
  });
});
req.write(payload);
req.end();
