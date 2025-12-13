package com.example.mqttpanelcraft_beta

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.paho.client.mqttv3.MqttMessage
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

class MainActivity : AppCompatActivity() {

    private lateinit var textDisplay: TextView
    private lateinit var imageDisplay: ImageView
    private lateinit var viewLedDisplay: View
    private lateinit var spinnerType: Spinner
    private lateinit var editId: EditText
    private lateinit var btnSubscribe: Button
    private lateinit var btnPublish: Button
    private lateinit var btnPickImage: Button
    private lateinit var editContent: EditText
    private lateinit var recyclerLog: RecyclerView
    private lateinit var logAdapter: LogAdapter

    private val types = listOf("text", "image", "led")
    private var selectedType = "text"

    // Permission Launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            openGallery()
        } else {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    // Image Picker (GetContent)
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            processAndSendImage(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Views
        textDisplay = findViewById(R.id.textDisplay)
        imageDisplay = findViewById(R.id.imageDisplay)
        viewLedDisplay = findViewById(R.id.viewLedDisplay)
        spinnerType = findViewById(R.id.spinnerType)
        editId = findViewById(R.id.editId)
        btnSubscribe = findViewById(R.id.btnSubscribe)
        btnPublish = findViewById(R.id.btnPublish)
        btnPickImage = findViewById(R.id.btnPickImage)
        editContent = findViewById(R.id.editContent)
        recyclerLog = findViewById(R.id.recyclerLog)

        // Setup RecyclerView
        logAdapter = LogAdapter()
        recyclerLog.layoutManager = LinearLayoutManager(this)
        recyclerLog.adapter = logAdapter

        // Setup Spinner
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, types)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerType.adapter = adapter
        spinnerType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedType = types[position]
                btnPickImage.visibility = if (selectedType == "image") View.VISIBLE else View.GONE
                btnPublish.visibility = if (selectedType == "image") View.GONE else View.VISIBLE
                editContent.visibility = if (selectedType == "image") View.GONE else View.VISIBLE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Observe Logs
        MqttRepository.logs.observe(this) { logs ->
            logAdapter.submitList(logs)
            if (logs.isNotEmpty()) {
                recyclerLog.smoothScrollToPosition(logs.size - 1)
            }
        }

        // Observe Display Item
        MqttRepository.displayItem.observe(this) { item ->
            updateDisplayArea(item)
        }

        // Set Listeners
        btnSubscribe.setOnClickListener { subscribeToTopic() }
        btnPublish.setOnClickListener { publishMessage() }
        btnPickImage.setOnClickListener { checkPermissionAndPickImage() }
    }

    private fun checkPermissionAndPickImage() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            openGallery()
        } else {
            requestPermissionLauncher.launch(permission)
        }
    }

    private fun openGallery() {
        pickImageLauncher.launch("image/*")
    }

    private fun updateDisplayArea(item: LogItem) {
        textDisplay.visibility = View.GONE
        imageDisplay.visibility = View.GONE
        viewLedDisplay.visibility = View.GONE

        when (item) {
            is LogItem.Text -> {
                textDisplay.visibility = View.VISIBLE
                textDisplay.text = item.content
            }
            is LogItem.Image -> {
                imageDisplay.visibility = View.VISIBLE
                try {
                    val decodedString = Base64.decode(item.base64, Base64.DEFAULT)
                    val decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
                    imageDisplay.setImageBitmap(decodedByte)
                } catch (e: Exception) {
                    textDisplay.visibility = View.VISIBLE
                    textDisplay.text = "Error displaying image"
                }
            }
            is LogItem.Led -> {
                viewLedDisplay.visibility = View.VISIBLE
                val background = viewLedDisplay.background as GradientDrawable
                background.setColor(if (item.isOn) Color.GREEN else Color.GRAY)
            }
        }
    }

    private fun getTopic(): String {
        val id = editId.text.toString()
        if (id.isEmpty()) return ""
        return "${MqttRepository.account}/${MqttRepository.project}/$selectedType/$id"
    }

    private fun subscribeToTopic() {
        val topic = getTopic()
        if (topic.isEmpty()) {
            Toast.makeText(this, "Please enter ID", Toast.LENGTH_SHORT).show()
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                MqttRepository.mqttClient?.subscribe(topic)
                val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                MqttRepository.addLog("Subscribed to $topic", timestamp)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Subscribe failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun publishMessage() {
        val topic = getTopic()
        if (topic.isEmpty()) {
            Toast.makeText(this, "Please enter ID", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Auto-Subscribe
        subscribeToTopic()
        
        if (selectedType == "text" || selectedType == "led") {
            val content = editContent.text.toString()
            if (content.isEmpty()) {
                Toast.makeText(this, "Please enter Content", Toast.LENGTH_SHORT).show()
                return
            }
            sendMqttMessage(topic, content)
        }
    }
    
    private fun sendMqttMessage(topic: String, payload: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val message = MqttMessage(payload.toByteArray())
                message.qos = 0
                MqttRepository.mqttClient?.publish(topic, message)
                val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                MqttRepository.addLog("TX [$topic]: $payload", timestamp)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Publish failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun processAndSendImage(uri: Uri) {
        val topic = getTopic()
        if (topic.isEmpty()) {
             Toast.makeText(this, "Please enter ID", Toast.LENGTH_SHORT).show()
             return
        }
        
        // Auto-Subscribe
        subscribeToTopic()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                
                // Local Preview
                withContext(Dispatchers.Main) {
                    textDisplay.visibility = View.GONE
                    viewLedDisplay.visibility = View.GONE
                    imageDisplay.visibility = View.VISIBLE
                    imageDisplay.setImageBitmap(bitmap)
                }
                
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
                val byteArray = outputStream.toByteArray()
                val base64 = Base64.encodeToString(byteArray, Base64.NO_WRAP)

                val chunkSize = 1000
                val totalLength = base64.length
                val totalChunks = ceil(totalLength.toDouble() / chunkSize).toInt()

                for (i in 0 until totalChunks) {
                    val start = i * chunkSize
                    val end = (start + chunkSize).coerceAtMost(totalLength)
                    val chunkData = base64.substring(start, end)
                    
                    val payload = "${i + 1}|$totalChunks|$chunkData"
                    
                    val message = MqttMessage(payload.toByteArray())
                    message.qos = 0
                    MqttRepository.mqttClient?.publish(topic, message)
                    
                    val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    MqttRepository.addLog("TX Chunk ${i+1}/$totalChunks to $topic", timestamp)
                    
                    Thread.sleep(100)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Image send failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
