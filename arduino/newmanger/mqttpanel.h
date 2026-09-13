#ifndef MQTTPANEL_H
#define MQTTPANEL_H

#include <Arduino.h>
#include <PubSubClient.h>

#ifndef MQTTPANEL_CFG_LEN
#define MQTTPANEL_CFG_LEN 128
#endif
#ifndef MQTTPANEL_PORT_LEN
#define MQTTPANEL_PORT_LEN 8
#endif
#ifndef MQTTPANEL_AUTH_LEN
#define MQTTPANEL_AUTH_LEN 64
#endif
#ifndef MQTTPANEL_MAX_SUBS
#define MQTTPANEL_MAX_SUBS 32
#endif

typedef void (*MqttCallback)(String topic, String msg);

void mqttpanel_begin(PubSubClient* client, MqttCallback cb,
              char* srv, char* port, char* topic,
              int portal_sec, int factory_sec,
              int trigger_pin, int led_pin,
              int check_wifi_sec);

void mqttpanel_set_auth(char* user, char* pass);

void mqttpanel_loop();

void mqttpanel_pub(String topic, String payload);
void mqttpanel_sub(String topic);

bool mqttpanel_is_connected();

#endif
