package edu.utexas.mpc.samplerestweatherapp

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.icu.text.SimpleDateFormat
import android.os.Build
import android.os.Bundle
import android.os.SystemClock.sleep
import android.support.v7.app.AppCompatActivity
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.provider.Settings
import android.support.annotation.RequiresApi
import com.android.volley.RequestQueue
import com.android.volley.toolbox.ImageRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_main.*
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttMessage
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.collections.HashMap
import kotlin.text.Typography.tm

class MainActivity : AppCompatActivity() {

    val serverUri = "tcp://192.168.4.1:1883"
    val clientId = "EmergingTechMQTTClient"
    val subscribeTopic = "steps"
    val publishTopic = "weather"

    // I'm using lateinit for these widgets because I read that repeated calls to findViewById
    // are energy intensive
    lateinit var textView: TextView
    lateinit var cityView: TextView
    lateinit var tempView: TextView
    lateinit var stepsView: TextView
    lateinit var imageView: ImageView
    lateinit var retrieveButton: Button
    lateinit var forecastButton: Button
    lateinit var sendButton: Button
    lateinit var syncButton: Button
    lateinit var wifiButton: Button

    lateinit var queue: RequestQueue
    lateinit var gson: Gson
    lateinit var mostRecentWeatherResult: WeatherResult
    lateinit var weatherForecastResult: WeatherForecast
    lateinit var mqttAndroidClient: MqttAndroidClient
    lateinit var weatherData: String
    lateinit var forecastData: String


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textView = this.findViewById(R.id.text)
        cityView = this.findViewById(R.id.city)
        tempView = this.findViewById(R.id.temp)
        stepsView = this.findViewById(R.id.stepsView)
        imageView = this.findViewById(R.id.imageView)
        retrieveButton = this.findViewById(R.id.retrieveButton)
        retrieveButton.setOnClickListener({ requestAll() })
        forecastButton = this.findViewById(R.id.forecastButton)
        forecastButton.setOnClickListener({ sendForecast() })

        queue = Volley.newRequestQueue(this)
        gson = Gson()

        sendButton = this.findViewById(R.id.sendButton)
        sendButton.setOnClickListener({ sendWeather() })
        syncButton = this.findViewById(R.id.syncButton)
        syncButton.setOnClickListener({ syncWithPi() })
        wifiButton = this.findViewById(R.id.wifiButton)
        wifiButton.setOnClickListener({ launchWifiSettings() })

        mqttAndroidClient = MqttAndroidClient(getApplicationContext(), serverUri, clientId);

        // when things happen in the mqtt client, these callbacks will be called
        mqttAndroidClient.setCallback(object: MqttCallbackExtended {

            // when the client is successfully connected to the broker, this method gets called
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                println("Connection Complete!!")
                // this subscribes the client to the subscribe topic
                mqttAndroidClient.subscribe(subscribeTopic, 0)
            }

            // this method is called when a message is received that fulfills a subscription
            override fun messageArrived(topic: String?, message: MqttMessage?) {
                println(message)
                stepsView.text = message.toString()
            }

            override fun connectionLost(cause: Throwable?) {
                println("Connection Lost")
            }

            // this method is called when the client succcessfully publishes to the broker
            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                println("Delivery Complete")
            }
        })
    }

    fun requestAll(){
        requestWeather()
        requestWeatherForcecast()
    }

    fun requestWeather(){
        val url = StringBuilder("https://api.openweathermap.org/data/2.5/weather?id=4671654&appid=3af6d2077570cd71fa2168839b7cb279&units=imperial").toString()
        val stringRequest = object : StringRequest(com.android.volley.Request.Method.GET, url,
                com.android.volley.Response.Listener<String> { response ->
                    //textView.text = response
                    mostRecentWeatherResult = gson.fromJson(response, WeatherResult::class.java)
                    textView.text = mostRecentWeatherResult.weather.get(0).main
                    cityView.text = mostRecentWeatherResult.name
                    tempView.text = mostRecentWeatherResult.main.temp.toString() + "°F"

                    val temp_max = mostRecentWeatherResult.main.temp_max.toString()
                    val temp_min = mostRecentWeatherResult.main.temp_min.toString()
                    val precip = mostRecentWeatherResult.rain

                    weatherData = "w," + temp_max + "," + temp_min + "," + precip.toString()
                    val icon = mostRecentWeatherResult.weather.get(0).icon
                    requestIcon(icon)
                },
                com.android.volley.Response.ErrorListener { println("******That didn't work!") }) {}
        // Add the request to the RequestQueue.
        queue.add(stringRequest)
    }

    fun requestWeatherForcecast() {
        val url = StringBuilder("https://api.openweathermap.org/data/2.5/forecast?id=4671654&appid=3af6d2077570cd71fa2168839b7cb279&units=imperial").toString()
        val stringRequest = @RequiresApi(Build.VERSION_CODES.N)
        object : StringRequest(com.android.volley.Request.Method.GET, url,
                com.android.volley.Response.Listener<String> { response ->
                    weatherForecastResult = gson.fromJson(response, WeatherForecast::class.java)
                    var temp_max = 0.0
                    var temp_min = 1000.0
                    var precip = mutableListOf<Any?>()

                    val tomorrow = getTomorrowsDate()
                    val tomorrowsForecast = weatherForecastResult.list.filter { it.dt_txt.substring(0, 10) == tomorrow }
                    for (f in tomorrowsForecast) {
                        var tmax = f.main.temp_max
                        var tmin = f.main.temp_min
                        if (tmax > temp_max) {
                            temp_max = tmax
                        }
                        if (tmin < temp_min) {
                            temp_min = tmin
                        }
                        precip.add(f.rain)
                    }
                    forecastData = "f-" + temp_max.toString() + "-" + temp_min.toString() + "-" + precip.toString()
                },
                com.android.volley.Response.ErrorListener { println("******That didn't work!") }) {}
        // Add the request to the RequestQueue.
        queue.add(stringRequest)
    }


    fun requestIcon(icon: String) {
        val url = "https://openweathermap.org/img/wn/" + icon + "@2x.png"
        Picasso.get().load(url).resize(100,100).into(imageView)
    }

    // this method just connects the paho mqtt client to the broker
    fun syncWithPi(){
        mqttAndroidClient.connect()
    }

    fun sendWeather(){
        val message = MqttMessage()
        message.payload = (weatherData.toString()).toByteArray()
        mqttAndroidClient.publish(publishTopic, message)
    }

    fun sendForecast(){
        val message = MqttMessage()
        message.payload = (forecastData).toByteArray()
        mqttAndroidClient.publish(publishTopic, message)
    }

    fun launchWifiSettings(){
        val intent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
//        startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
        startActivity(Intent.createChooser(intent, "dialogTitle"));
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun getTomorrowsDate(): String {
        val sdf = SimpleDateFormat("yyyy-M-dd")
        val calendar = GregorianCalendar()
        calendar.time = Date()
        calendar.add(Calendar.DATE, 1)
        val cal = calendar.time
        val tomorrow = sdf.format(cal)
        return tomorrow
    }
}

class WeatherResult(val dt_txt: String, val id: Int, val name: String, val rain: Any?, val temp: String, val cod: Int, val coord: Coordinates, val main: WeatherMain, val weather: Array<Weather>)
class WeatherForecast(val list: Array<WeatherResult>)
class Coordinates(val lon: Double, val lat: Double)
class Weather(val id: Int, val main: String, val description: String, val icon: String)
class WeatherMain(val temp: Double, val pressure: Int, val humidity: Int, val temp_min: Double, val temp_max: Double)

