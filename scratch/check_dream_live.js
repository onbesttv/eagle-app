const https = require("https");
const payload = JSON.stringify({
  p_device_id: "test_device_123",
  p_username: "",
  p_service: "BEST",
  p_device_name: "PC",
  p_app_version: "3.35",
  p_reseller_id: "dream"
});
const req = https.request({
  hostname: "myreymyqsnikjdhnstjn.supabase.co",
  path: "/rest/v1/rpc/app_sync",
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
    console.log("STATUS:", res.statusCode);
    console.log("RESPONSE:", data);
  });
});
req.write(payload);
req.end();
