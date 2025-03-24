package com.example.androidllmuiapp

import android.os.AsyncTask
import android.os.Bundle
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {

    private lateinit var modelSelection: RadioGroup
    private lateinit var promptInput: EditText
    private lateinit var sendButton: Button
    private lateinit var cancelButton: Button
    private lateinit var responseOutput: TextView
    private var asyncTask: FetchLLMResponseTask? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Initialize UI components
        modelSelection = findViewById(R.id.modelSelection)
        promptInput = findViewById(R.id.promptInput)
        sendButton = findViewById(R.id.sendButton)
        cancelButton = findViewById(R.id.cancelButton)
        responseOutput = findViewById(R.id.responseOutput)

        sendButton.setOnClickListener {
            val prompt = promptInput.text.toString()
            if (prompt.isNotBlank()) {
                asyncTask = FetchLLMResponseTask()
                asyncTask?.execute(prompt)
            } else {
                Toast.makeText(this, "Please enter a prompt", Toast.LENGTH_SHORT).show()
            }
        }

        cancelButton.setOnClickListener {
            asyncTask?.cancel(true)
            responseOutput.text = "Request Cancelled"
        }
    }

    private inner class FetchLLMResponseTask : AsyncTask<String, Void, String>() {

        private val maxRetries = 3
        private val retryDelay = 2000L // 2 seconds

        override fun onPreExecute() {
            super.onPreExecute()
            responseOutput.text = "Fetching response..."
        }

        override fun doInBackground(vararg params: String?): String {
            val prompt = params[0] ?: return "Error: No prompt provided"
            val apiKey = "YOUR_TOGETHER_API_KEY" // Replace with your actual API key

            var attempt = 0
            while (attempt < maxRetries) {
                try {
                    val url = URL("https://api.together.xyz/v1/chat/completions")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Authorization", "Bearer $apiKey")
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.doOutput = true

                    // Construct the request body
                    val requestBody = JSONObject().apply {
                        put("model", "meta-llama/Llama-3.3-70B-Instruct-Turbo")
                        put("messages", JSONArray().apply {
                            put(JSONObject().apply {
                                put("role", "user")
                                put("content", prompt)
                            })
                        })
                    }

                    // Write the request body
                    val outputStream = conn.outputStream
                    val writer = BufferedWriter(OutputStreamWriter(outputStream, "UTF-8"))
                    writer.write(requestBody.toString())
                    writer.flush()
                    writer.close()
                    outputStream.close()

                    // Read and parse response
                    val responseCode = conn.responseCode
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val inputStream = BufferedReader(InputStreamReader(conn.inputStream))
                        val response = inputStream.readText()
                        inputStream.close()

                        val jsonResponse = JSONObject(response)
                        val choices = jsonResponse.getJSONArray("choices")
                        if (choices.length() > 0) {
                            return choices.getJSONObject(0).getJSONObject("message").getString("content")
                        }
                        return "No response from AI."
                    } else if (responseCode == 429) {
                        // Rate limit hit, retry after delay
                        attempt++
                        if (attempt < maxRetries) {
                            Thread.sleep(retryDelay)
                        }
                    } else {
                        val errorStream = BufferedReader(InputStreamReader(conn.errorStream))
                        val errorResponse = errorStream.readText()
                        errorStream.close()
                        return "Error: Response code $responseCode, $errorResponse"
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    return "Error: ${e.message}"
                }
            }
            return "Error: Max retries reached"
        }

        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)
            responseOutput.text = result ?: "No response received"
        }
    }
}
