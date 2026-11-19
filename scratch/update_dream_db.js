const https = require("https");

const payload = JSON.stringify({
  p_reseller_id: "dream",
  p_password: "dream12345",
  p_best_url: "http://thedreamtv.site:80",
  p_best2_url: "http://thedreamtv.site:80",
  p_announcement: "||ACTIVATIONS:53:EE:3D:7C:8F:73=xsRuPyH:c8t5UxT8XD:http://gd.tugabest.xyz:8080 ||DNS_LIST:Dream TV (Principal)|http://thedreamtv.site:80,http://serrano.team:80,TESTE|http://gd.tugabest.xyz:8080,Etluso|http://etluso.site:80,SPLAY|https://topnextplay.online:443 ||VPN:pt,spain,france,uk,nl_amsterdam,de-frankfurt,swiss,italy||VPN_AUTH:p7426501:on4q5FT9vx||VPN_USERS:restricted|",
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
  let data = "";
  res.on("data", c => data += c);
  res.on("end", () => {
    console.log("SAVE STATUS:", res.statusCode);
    console.log("SAVE RESPONSE:", data);
  });
});
req.write(payload);
req.end();
