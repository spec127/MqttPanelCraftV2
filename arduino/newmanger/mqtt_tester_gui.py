import tkinter as tk
from tkinter import ttk, scrolledtext
import paho.mqtt.client as mqtt
import json
import threading
import time
import random

# --- CONFIGURATION (Default) ---
DEFAULT_BROKER = "broker.hivemq.com"
DEFAULT_PORT = 1883
DEFAULT_TOPIC = "test/topic"

class MqttTesterApp:
    def __init__(self, root):
        self.root = root
        self.root.title("ESP32 MQTT Panel Tester (Full Duplex with Visualization)")
        self.root.geometry("600x800")
        
        self.client = None
        self.connected = False
        
        self._init_ui()
        
    def _init_ui(self):
        # --- Connection Frame ---
        conn_frame = ttk.LabelFrame(self.root, text="Connection Settings", padding=10)
        conn_frame.pack(fill="x", padx=10, pady=5)
        
        ttk.Label(conn_frame, text="Broker:").grid(row=0, column=0, padx=5)
        self.entry_broker = ttk.Entry(conn_frame, width=20)
        self.entry_broker.insert(0, DEFAULT_BROKER)
        self.entry_broker.grid(row=0, column=1, padx=5)
        
        ttk.Label(conn_frame, text="Port:").grid(row=0, column=2, padx=5)
        self.entry_port = ttk.Entry(conn_frame, width=6)
        self.entry_port.insert(0, str(DEFAULT_PORT))
        self.entry_port.grid(row=0, column=3, padx=5)
        
        ttk.Label(conn_frame, text="Topic:").grid(row=0, column=4, padx=5)
        self.entry_topic = ttk.Entry(conn_frame, width=15)
        self.entry_topic.insert(0, DEFAULT_TOPIC)
        self.entry_topic.grid(row=0, column=5, padx=5)
        
        self.btn_connect = ttk.Button(conn_frame, text="Connect", command=self.toggle_connect)
        self.btn_connect.grid(row=0, column=6, padx=10)

        # --- TX Frame (Control Index 1) ---
        tx_frame = ttk.LabelFrame(self.root, text="TX Control (Send to Index 1)", padding=10)
        tx_frame.pack(fill="x", padx=10, pady=10)
        
        self.var_switch = tk.BooleanVar()
        self.btn_switch = ttk.Checkbutton(tx_frame, text="Switch (switch/1/set)", variable=self.var_switch, command=self.send_switch)
        self.btn_switch.pack(anchor="w", pady=5)
        
        ttk.Label(tx_frame, text="Dimmer (dimmer/1/set):").pack(anchor="w")
        self.scale_dimmer = ttk.Scale(tx_frame, from_=0, to=100, orient="horizontal", command=self.send_dimmer)
        self.scale_dimmer.set(50)
        self.scale_dimmer.pack(fill="x", pady=5)
        
        ttk.Label(tx_frame, text="Select (select/1/set):").pack(anchor="w")
        self.combo_select = ttk.Combobox(tx_frame, values=["0", "1", "2", "3", "4"])
        self.combo_select.current(0)
        self.combo_select.bind("<<ComboboxSelected>>", self.send_select)
        self.combo_select.pack(fill="x", pady=5)
        
        ttk.Label(tx_frame, text="Text (text/1/set):").pack(anchor="w")
        txt_frame = ttk.Frame(tx_frame)
        txt_frame.pack(fill="x", pady=5)
        self.entry_text = ttk.Entry(txt_frame)
        self.entry_text.pack(side="left", fill="x", expand=True)
        ttk.Button(txt_frame, text="Send", command=self.send_text).pack(side="right", padx=5)

        # --- RX Visualization Frame (Index 2) ---
        vis_frame = ttk.LabelFrame(self.root, text="RX Visualization (Index 2)", padding=10)
        vis_frame.pack(fill="x", padx=10, pady=5)
        
        # LED Indicator (Canvas)
        top_row = ttk.Frame(vis_frame)
        top_row.pack(fill="x", pady=5)
        ttk.Label(top_row, text="Switch (LED):").pack(side="left")
        self.canvas_led = tk.Canvas(top_row, width=30, height=30, bg="gray") # Init gray
        self.canvas_led.pack(side="left", padx=10)
        self.led_oval = self.canvas_led.create_oval(5, 5, 25, 25, fill="black")

        # Linear Gauges (Progress Bars)
        ttk.Label(vis_frame, text="Dimmer (0-100):").pack(anchor="w", pady=(5,0))
        self.pb_dimmer = ttk.Progressbar(vis_frame, orient="horizontal", length=100, mode="determinate")
        self.pb_dimmer.pack(fill="x", pady=5)
        
        ttk.Label(vis_frame, text="Number (Raw 0-50):").pack(anchor="w", pady=(5,0))
        self.pb_number = ttk.Progressbar(vis_frame, orient="horizontal", length=100, mode="determinate")
        self.pb_number.pack(fill="x", pady=5)
        
        ttk.Label(vis_frame, text="Received Text:").pack(anchor="w", pady=(5,0))
        self.lbl_text_rx = ttk.Label(vis_frame, text="---", relief="sunken", anchor="w")
        self.lbl_text_rx.pack(fill="x", pady=5)

        # --- RX Log ---
        rx_log_frame = ttk.LabelFrame(self.root, text="Raw Log", padding=10)
        rx_log_frame.pack(fill="both", expand=True, padx=10, pady=5)
        self.log_area = scrolledtext.ScrolledText(rx_log_frame, height=8, state='disabled')
        self.log_area.pack(fill="both", expand=True)

    def log(self, msg):
        self.log_area.config(state='normal')
        self.log_area.insert(tk.END, msg + "\n")
        self.log_area.see(tk.END)
        self.log_area.config(state='disabled')
        
    def _update_led(self, is_on):
        color = "#00FF00" if is_on else "#003300" # Green vs Dark Green
        self.canvas_led.itemconfig(self.led_oval, fill=color)
        
    def _update_dimmer(self, val):
        self.pb_dimmer["value"] = val
        
    def _update_number(self, val):
        # Assuming wave is 15-35, map roughly to 0-50 for bar
        self.pb_number["value"] = val
        
    def _update_text(self, txt):
        self.lbl_text_rx.config(text=txt)

    def toggle_connect(self):
        if self.connected:
            self.client.disconnect()
            self.client.loop_stop()
            self.connected = False
            self.btn_connect.config(text="Connect")
            self.log("[System] Disconnected.")
            self.canvas_led.config(bg="gray")
        else:
            broker = self.entry_broker.get()
            port = int(self.entry_port.get())
            topic = self.entry_topic.get()
            
            self.client = mqtt.Client()
            self.client.on_connect = self.on_connect
            self.client.on_message = self.on_message
            
            try:
                self.client.connect(broker, port, 60)
                self.client.loop_start()
                self.connected = True
                self.btn_connect.config(text="Disconnect")
                self.log(f"[System] Connecting to {broker}...")
                self.canvas_led.config(bg="white") 
            except Exception as e:
                self.log(f"[Error] {e}")

    def on_connect(self, client, userdata, flags, rc):
        if rc == 0:
            self.log("[System] Connected!")
            topic = self.entry_topic.get()
            sub_topic = f"{topic}/#" 
            client.subscribe(sub_topic)
            self.log(f"[System] Subscribed to {sub_topic}")
        else:
            self.log(f"[Error] Connection failed code {rc}")

    def on_message(self, client, userdata, msg):
        try:
            topic = msg.topic
            payload = msg.payload.decode()
            
            # --- RX Visual Updates (Index 2) ---
            if "switch/2/val" in topic:
                is_on = (payload == "1")
                self.root.after(0, self._update_led, is_on) # UI Safe Update
                
            elif "dimmer/2/val" in topic:
                val = int(payload)
                self.root.after(0, self._update_dimmer, val)
                
            elif "number/2/val" in topic:
                val = float(payload)
                self.root.after(0, self._update_number, val)
                
            elif "text/2/val" in topic:
                self.root.after(0, self._update_text, payload)

            # Log
            if "/2/val" in topic:
                self.log(f"[RX-Auto] {topic} -> {payload}")
            elif "/1/val" in topic:
                self.log(f"[RX-Feedback] {topic} -> {payload}")
                
        except Exception as e:
            print(e)

    # --- TX Functions ---
    def _get_base_topic(self):
        t = self.entry_topic.get()
        if t.endswith("/"): t = t[:-1]
        return t

    def send_switch(self):
        if not self.connected: return
        val = "1" if self.var_switch.get() else "0"
        self.client.publish(f"{self._get_base_topic()}/switch/1/set", val)

    def send_dimmer(self, val):
        if not self.connected: return
        ival = int(float(val))
        self.client.publish(f"{self._get_base_topic()}/dimmer/1/set", str(ival))

    def send_select(self, event):
        if not self.connected: return
        self.client.publish(f"{self._get_base_topic()}/select/1/set", self.combo_select.get())

    def send_text(self):
        if not self.connected: return
        self.client.publish(f"{self._get_base_topic()}/text/1/set", self.entry_text.get())

if __name__ == "__main__":
    root = tk.Tk()
    app = MqttTesterApp(root)
    root.mainloop()
