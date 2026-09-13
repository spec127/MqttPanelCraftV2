#include "mqttpanel.h"

#ifdef ESP32
  #include <WiFi.h>
  #include <FS.h>
  #include <LittleFS.h>
#elif defined(ESP8266)
  #include <ESP8266WiFi.h>
  #include <LittleFS.h>
#endif

#include <WiFiManager.h>
#include <ArduinoJson.h>

const char custom_style[] PROGMEM = R"=====(
<style>
  body { background-color: #ffffff !important; color: #555 !important; font-family: 'Verdana'; text-align: center; padding: 10px; margin: 0; }
  h1 { font-size: 1.5rem; margin: 10px 0 5px 0; color: #aa89c0; }
  p { font-size: 0.9rem; color: #999; margin-bottom: 15px; }
  input {
    background-color: #fafafa !important;
    color: #333 !important;
    border: 1px solid #e0e0e0 !important;
    padding: 10px;
    border-radius: 6px;
    width: 90%; max-width: 280px;
    margin-bottom: 10px;
    font-size: 0.95rem;
    outline: none;
  }
  input:focus { border: 2px solid #aa89c0 !important; background-color: #fff !important; }
  button {
    background: linear-gradient(135deg, #aa89c0 0%, #8860a0 100%) !important;
    color: #fff !important;
    border: none !important;
    border-radius: 20px !important;
    padding: 12px !important;
    cursor: pointer;
    width: 100%; max-width: 280px;
    display: block; margin: 15px auto;
    font-size: 1rem;
    box-shadow: 0 3px 10px rgba(170, 137, 192, 0.4);
    transition: transform 0.2s;
  }
  button:hover { transform: scale(1.02); }
  input::placeholder { color: #ccc; font-style: italic; }
</style>
<script>
  window.onload = function() {
    var h = document.createElement("div");
    h.innerHTML = "<h1>ANTIGRAVITY</h1><p>IoT Configuration</p>";
    document.body.insertBefore(h, document.body.firstChild);
    var inputs = document.getElementsByTagName('input');
    for(var i=0; i<inputs.length; i++){
       if(inputs[i].value === "your mqtt broker address") inputs[i].value = "";
       if(inputs[i].value === "test_topic") inputs[i].value = "";
    }
  };
</script>
)=====";

static PubSubClient* _client = NULL;
static MqttCallback _userCallback = NULL;

static char* _p_server = NULL;
static char* _p_port = NULL;
static char* _p_topic = NULL;
static char* _p_user = NULL;
static char* _p_pass = NULL;

static int _portal_sec = 3;
static int _factory_sec = 10;
static int _trigger_pin = 0;
static int _led_pin = 4;
static int _check_wifi_sec = 60;

static String _subs[MQTTPANEL_MAX_SUBS];
static int _subCount = 0;

bool shouldSaveConfig = false;

void _loadConfig();
void _saveConfig();
void _startPortal(const char* apName);
void _saveConfigCallback() { shouldSaveConfig = true; }
void _rememberSub(const String& topic);
void _resubscribeAll();
String _wildcardTopic();
bool _mqttConnect();

void _internal_callback(char* topic, byte* payload, unsigned int length) {
  if (_userCallback == NULL) return;
  String msg = "";
  for (unsigned int i = 0; i < length; i++) msg += (char)payload[i];
  _userCallback(String(topic), msg);
}

void mqttpanel_begin(PubSubClient* client, MqttCallback cb,
              char* srv, char* port, char* topic,
              int portal_sec, int factory_sec,
              int trigger_pin, int led_pin,
              int check_wifi_sec)
{
  _client = client;
  _userCallback = cb;
  _p_server = srv;
  _p_port = port;
  _p_topic = topic;
  _portal_sec = portal_sec;
  _factory_sec = factory_sec;
  _trigger_pin = trigger_pin;
  _led_pin = led_pin;
  _check_wifi_sec = check_wifi_sec;

  if (LittleFS.begin()) {
     _loadConfig();
  } else {
     #ifdef ESP32
     LittleFS.format();
     #endif
     LittleFS.begin();
  }

  if (digitalRead(_trigger_pin) == LOW) {
    delay(2000);
    if (digitalRead(_trigger_pin) == LOW) {
       Serial.println("[MP] BOOT RESET TRIGGERED");
       WiFiManager wm;
       wm.resetSettings();
       LittleFS.remove("/config.json");
       for(int i=0;i<5;i++) { digitalWrite(_led_pin,!digitalRead(_led_pin)); delay(100); }
       ESP.restart();
    }
  }

  WiFiManager wm;
  wm.setCustomHeadElement(custom_style);
  wm.setSaveConfigCallback(_saveConfigCallback);

  WiFiManagerParameter p_s("server", "MQTT Server", _p_server, MQTTPANEL_CFG_LEN, "placeholder='your mqtt broker address'");
  WiFiManagerParameter p_p("port", "MQTT Port", _p_port, MQTTPANEL_PORT_LEN, "placeholder='1883'");
  WiFiManagerParameter p_t("topic", "MQTT Topic", _p_topic, MQTTPANEL_CFG_LEN, "placeholder='topic/prefix'");
  WiFiManagerParameter p_u("user", "MQTT User", _p_user ? _p_user : "", MQTTPANEL_AUTH_LEN, "placeholder='optional'");
  WiFiManagerParameter p_pw("pass", "MQTT Password", _p_pass ? _p_pass : "", MQTTPANEL_AUTH_LEN, "placeholder='optional' type='password'");

  wm.addParameter(&p_s);
  wm.addParameter(&p_p);
  wm.addParameter(&p_t);
  wm.addParameter(&p_u);
  wm.addParameter(&p_pw);
  wm.setConnectTimeout(30);

  if (!wm.autoConnect("Antigravity_Setup")) {
     ESP.restart();
  }

  strlcpy(_p_server, p_s.getValue(), MQTTPANEL_CFG_LEN);
  strlcpy(_p_port, p_p.getValue(), MQTTPANEL_PORT_LEN);
  strlcpy(_p_topic, p_t.getValue(), MQTTPANEL_CFG_LEN);
  if (_p_user) strlcpy(_p_user, p_u.getValue(), MQTTPANEL_AUTH_LEN);
  if (_p_pass) strlcpy(_p_pass, p_pw.getValue(), MQTTPANEL_AUTH_LEN);

  if (shouldSaveConfig) _saveConfig();

  if (_client) {
     _client->setBufferSize(1024);
     _client->setCallback(_internal_callback);
     int p = atoi(_p_port);
     if (p > 0) _client->setServer(_p_server, p);
  }
}

void mqttpanel_set_auth(char* user, char* pass) {
  _p_user = user;
  _p_pass = pass;
}

void mqttpanel_loop() {
  static unsigned long pressStart = 0;
  if (digitalRead(_trigger_pin) == LOW) {
    if (pressStart == 0) pressStart = millis();
    unsigned long durWait = millis() - pressStart;
    if (durWait > _factory_sec * 1000) digitalWrite(_led_pin, (millis()/50)%2);
    else if (durWait > _portal_sec * 1000) digitalWrite(_led_pin, (millis()/200)%2);
  } else {
    if (pressStart > 0) {
       unsigned long duration = (millis() - pressStart) / 1000;
       pressStart = 0;
       if (duration >= _factory_sec) {
          Serial.println("[MP] FACTORY RESET!");
          WiFiManager wm;
          wm.resetSettings();
          LittleFS.remove("/config.json");
          ESP.restart();
       } else if (duration >= _portal_sec) {
          _startPortal("Antigravity_OnDemand");
       }
    }
  }

  static unsigned long lastWiFiCheck = 0;
  if (WiFi.status() == WL_CONNECTED) {
     lastWiFiCheck = millis();
     if (_client) {
        if (!_client->connected()) {
            static unsigned long lastTry = 0;
            static int retryCount = 0;
            if (millis() - lastTry > 5000) {
              lastTry = millis();
              Serial.println("[MQTT] Connecting...");
              if (_mqttConnect()) {
                  Serial.println("[MQTT] Connected!");
                  const String wildcard = _wildcardTopic();
                  if (wildcard.length() > 0) _client->subscribe(wildcard.c_str());
                  _resubscribeAll();
                  retryCount = 0;
              } else {
                  Serial.print("[MQTT] Failed rc=");
                  Serial.println(_client->state());
                  retryCount++;
                  if (retryCount >= 5) {
                    Serial.println("\n[MP] MQTT Failure Limit Reached. Opening Portal...");
                    _startPortal("Antigravity_Fix");
                  }
              }
            }
        } else {
            _client->loop();
        }
     }
  } else {
      unsigned long timeout = (unsigned long)_check_wifi_sec * 1000;
      if (millis() - lastWiFiCheck > timeout) {
          Serial.println("\n[MP] WiFi Failure (" + String(_check_wifi_sec) + "s). Opening Portal...");
          _startPortal("Antigravity_Fix");
          lastWiFiCheck = millis();
      }
  }
}

void mqttpanel_pub(String topic, String payload) {
  if (_client && _client->connected()) _client->publish(topic.c_str(), payload.c_str());
}

void mqttpanel_sub(String topic) {
  _rememberSub(topic);
  if (_client && _client->connected()) {
    _client->subscribe(topic.c_str());
    Serial.println("[Sub] " + topic);
  } else {
    Serial.println("[Sub queued] " + topic);
  }
}

bool mqttpanel_is_connected() {
  return WiFi.status() == WL_CONNECTED && _client && _client->connected();
}

void _rememberSub(const String& topic) {
  if (topic.length() == 0) return;
  for (int i = 0; i < _subCount; i++) {
    if (_subs[i] == topic) return;
  }
  if (_subCount < MQTTPANEL_MAX_SUBS) {
    _subs[_subCount++] = topic;
  } else {
    Serial.println("[MP] Subscription limit reached; increase MQTTPANEL_MAX_SUBS.");
  }
}

void _resubscribeAll() {
  if (!_client || !_client->connected()) return;
  for (int i = 0; i < _subCount; i++) {
    _client->subscribe(_subs[i].c_str());
    Serial.println("[Sub] " + _subs[i]);
  }
}

String _wildcardTopic() {
  String prefix = _p_topic ? String(_p_topic) : "";
  while (prefix.endsWith("/")) {
    prefix.remove(prefix.length() - 1);
  }
  if (prefix.length() == 0) return "";
  return prefix + "/#";
}

bool _mqttConnect() {
  if (!_client) return false;
  String id = "ESP-" + String(random(0xffff), HEX);
  if (_p_user && _p_user[0] != '\0') {
    return _client->connect(id.c_str(), _p_user, _p_pass ? _p_pass : "");
  }
  return _client->connect(id.c_str());
}

void _loadConfig() {
  if (!LittleFS.exists("/config.json")) return;
  File f = LittleFS.open("/config.json", "r");
  if (!f) return;
  JsonDocument doc;
  if (deserializeJson(doc, f)) {
    f.close();
    Serial.println("[MP] Invalid config; keeping sketch defaults.");
    return;
  }
  // Older files lack auth keys: preserve sketch defaults instead of silently clearing them.
  if (_p_server && doc["mqtt_server"].is<const char*>()) strlcpy(_p_server, doc["mqtt_server"], MQTTPANEL_CFG_LEN);
  if (_p_port && doc["mqtt_port"].is<const char*>()) strlcpy(_p_port, doc["mqtt_port"], MQTTPANEL_PORT_LEN);
  if (_p_topic && doc["mqtt_topic"].is<const char*>()) strlcpy(_p_topic, doc["mqtt_topic"], MQTTPANEL_CFG_LEN);
  if (_p_user && doc["mqtt_user"].is<const char*>()) strlcpy(_p_user, doc["mqtt_user"], MQTTPANEL_AUTH_LEN);
  if (_p_pass && doc["mqtt_pass"].is<const char*>()) strlcpy(_p_pass, doc["mqtt_pass"], MQTTPANEL_AUTH_LEN);
  f.close();
}

void _saveConfig() {
  JsonDocument doc;
  doc["mqtt_server"] = _p_server;
  doc["mqtt_port"] = _p_port;
  doc["mqtt_topic"] = _p_topic;
  if (_p_user) doc["mqtt_user"] = _p_user;
  if (_p_pass) doc["mqtt_pass"] = _p_pass;
  File f = LittleFS.open("/config.json", "w");
  if (f) serializeJson(doc, f);
  f.close();
}

void _startPortal(const char* apName) {
  Serial.println("[MP] Opening Portal: " + String(apName));
  WiFiManager wm;
  wm.setCustomHeadElement(custom_style);

  WiFiManagerParameter p_s("server", "MQTT Server", _p_server, MQTTPANEL_CFG_LEN, "placeholder='your mqtt broker address'");
  WiFiManagerParameter p_p("port", "MQTT Port", _p_port, MQTTPANEL_PORT_LEN, "placeholder='1883'");
  WiFiManagerParameter p_t("topic", "MQTT Topic", _p_topic, MQTTPANEL_CFG_LEN, "placeholder='topic/prefix'");
  WiFiManagerParameter p_u("user", "MQTT User", _p_user ? _p_user : "", MQTTPANEL_AUTH_LEN, "placeholder='optional'");
  WiFiManagerParameter p_pw("pass", "MQTT Password", _p_pass ? _p_pass : "", MQTTPANEL_AUTH_LEN, "placeholder='optional' type='password'");

  wm.addParameter(&p_s);
  wm.addParameter(&p_p);
  wm.addParameter(&p_t);
  wm.addParameter(&p_u);
  wm.addParameter(&p_pw);
  wm.setSaveConfigCallback(_saveConfigCallback);
  wm.setBreakAfterConfig(true);
  wm.startConfigPortal(apName);

  strlcpy(_p_server, p_s.getValue(), MQTTPANEL_CFG_LEN);
  strlcpy(_p_port, p_p.getValue(), MQTTPANEL_PORT_LEN);
  strlcpy(_p_topic, p_t.getValue(), MQTTPANEL_CFG_LEN);
  if (_p_user) strlcpy(_p_user, p_u.getValue(), MQTTPANEL_AUTH_LEN);
  if (_p_pass) strlcpy(_p_pass, p_pw.getValue(), MQTTPANEL_AUTH_LEN);
  _saveConfig();

  Serial.println("[MP] Params Saved. Restarting...");
  delay(1000);
  ESP.restart();
}
