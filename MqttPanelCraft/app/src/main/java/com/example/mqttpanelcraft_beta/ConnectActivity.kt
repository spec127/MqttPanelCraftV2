package com.example.mqttpanelcraft_beta

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConnectActivity : AppCompatActivity() {

    private lateinit var editBrokerUri: EditText
    private lateinit var editAccount: EditText
    private lateinit var editProject: EditText
    private lateinit var spinnerHistory: Spinner
    private lateinit var btnConnect: Button
    private lateinit var sharedPreferences: SharedPreferences
    private val historyKey = "broker_history"
    private val historyList = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connect)

        editBrokerUri = findViewById(R.id.editBrokerUri)
        editAccount = findViewById(R.id.editAccount)
        editProject = findViewById(R.id.editProject)
        spinnerHistory = findViewById(R.id.spinnerHistory)
        btnConnect = findViewById(R.id.btnConnect)
        sharedPreferences = getSharedPreferences("MqttApp", Context.MODE_PRIVATE)

        loadHistory()
        setupSpinner()

        // Load saved account/project
        editAccount.setText(sharedPreferences.getString("account", "jin"))
        editProject.setText(sharedPreferences.getString("project", "mqtest"))

        btnConnect.setOnClickListener { connectToBroker() }
    }

    private fun loadHistory() {
        val historySet = sharedPreferences.getStringSet(historyKey, setOf("tcp://broker.hivemq.com:1883"))
        historyList.clear()
        historyList.addAll(historySet ?: emptySet())
    }

    private fun setupSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, historyList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerHistory.adapter = adapter

        spinnerHistory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                editBrokerUri.setText(historyList[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun saveHistory(uri: String) {
        if (!historyList.contains(uri)) {
            historyList.add(uri)
            sharedPreferences.edit().putStringSet(historyKey, historyList.toSet()).apply()
            (spinnerHistory.adapter as ArrayAdapter<*>).notifyDataSetChanged()
        }
        // Save account/project
        sharedPreferences.edit()
            .putString("account", editAccount.text.toString())
            .putString("project", editProject.text.toString())
            .apply()
    }

    private fun connectToBroker() {
        val brokerUri = editBrokerUri.text.toString()
        val account = editAccount.text.toString()
        val project = editProject.text.toString()

        if (brokerUri.isEmpty() || account.isEmpty() || project.isEmpty()) {
            Toast.makeText(this, "Please enter all fields", Toast.LENGTH_SHORT).show()
            return
        }

        btnConnect.isEnabled = false
        val clientId = MqttClient.generateClientId()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = MqttClient(brokerUri, clientId, MemoryPersistence())
                val options = MqttConnectOptions()
                options.isCleanSession = true
                options.connectionTimeout = 10
                options.keepAliveInterval = 20
                options.isAutomaticReconnect = true

                client.connect(options)
                
                MqttRepository.initialize(client, account, project)
                
                client.setCallback(object : MqttCallback {
                    override fun connectionLost(cause: Throwable?) {
                        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                        MqttRepository.addLog("Connection lost: ${cause?.message}", timestamp)
                    }

                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        val payload = message?.toString() ?: ""
                        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                        MqttRepository.processMessage(topic, payload, timestamp)
                    }

                    override fun deliveryComplete(token: IMqttDeliveryToken?) {
                    }
                })

                withContext(Dispatchers.Main) {
                    saveHistory(brokerUri)
                    Toast.makeText(this@ConnectActivity, "Connected!", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this@ConnectActivity, MainActivity::class.java)
                    startActivity(intent)
                    btnConnect.isEnabled = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ConnectActivity, "Connection failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    btnConnect.isEnabled = true
                    e.printStackTrace()
                }
            }
        }
    }
}
