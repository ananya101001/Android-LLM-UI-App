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

        modelSelection = findViewById(R.id.modelSelection)
        promptInput = findViewById(R.id.promptInput)
        sendButton = findViewById(R.id.sendButton)
        cancelButton = findViewById(R.id.cancelButton)
        responseOutput = findViewById(R.id.responseOutput)

        sendButton.setOnClickListener {
            val prompt = promptInput.text.toString()
            asyncTask = FetchLLMResponseTask()
            asyncTask?.execute(prompt)
        }

        cancelButton.setOnClickListener {
            asyncTask?.cancel(true)
            responseOutput.text = "Cancelled"
        }
    }

    private inner class FetchLLMResponseTask : AsyncTask<String, Void, String>() {

        private val maxRetries = 3
        private val retryDelay = 2000L // Delay in milliseconds (2 seconds)

        override fun onPreExecute() {
            super.onPreExecute()
            responseOutput.text = "Fetching response..."
        }

        override fun doInBackground(vararg params: String?): String {
            val prompt = params[0] ?: return "Error: No prompt provided"
            val apiKey = "AIzaSyDWnvLYU8-uNKfilmf6BcB3zszTOQyfjak" // Replace with your Gemini API key

            var attempt = 0
            while (attempt < maxRetries) {
                try {
                    val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.doOutput = true

                    // Construct the request body
                    val requestBody = JSONObject().apply {
                        put("contents", JSONArray().apply {
                            put(JSONObject().apply {
                                put("parts", JSONArray().apply {
                                    put(JSONObject().apply {
                                        put("text", prompt)
                                    })
                                })
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

                    // Check the response code
                    val responseCode = conn.responseCode
                    println("Response Code: $responseCode")
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val inputStream = BufferedReader(InputStreamReader(conn.inputStream))
                        val response = inputStream.readText()
                        inputStream.close()

                        // Parse the response
                        val jsonResponse = JSONObject(response)
                        val candidates = jsonResponse.getJSONArray("candidates")
                        val candidate = candidates.getJSONObject(0)
                        val content = candidate.getJSONObject("content")
                        val parts = content.getJSONArray("parts")
                        val part = parts.getJSONObject(0)
                        return part.getString("text") // Return the generated text
                    } else if (responseCode == 429) {
                        // Handle rate limit error (429)
                        attempt++
                        if (attempt < maxRetries) {
                            Thread.sleep(retryDelay)
                        }
                    } else {
                        // Handle other errors
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