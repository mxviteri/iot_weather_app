package edu.utexas.mpc.samplerestweatherapp


import android.content.Intent
import android.icu.text.SimpleDateFormat
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.support.annotation.RequiresApi
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.android.volley.RequestQueue
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.fragment_main2.*
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttMessage
import java.util.*

class MainFragment : Fragment() {

    val serverUri = "tcp://192.168.4.1:1883"
    val clientId = "EmergingTechMQTTClient"
    val subscribeTopic = "steps"
    val publishTopic = "weather"

    lateinit var textView: TextView
    lateinit var cityView: TextView
    lateinit var tempView: TextView
    lateinit var stepsView: TextView
    lateinit var imageView: ImageView
    lateinit var predsView: TextView
    lateinit var retrieveButton: Button
    lateinit var forecastButton: Button
    lateinit var sendButton: Button
    lateinit var syncButton: Button
    lateinit var wifiButton: Button
    lateinit var predsButton: Button
    lateinit var testModelButton: Button
    lateinit var bmrButton: Button

    lateinit var queue: RequestQueue
    lateinit var gson: Gson
    lateinit var mostRecentWeatherResult: WeatherResult
    lateinit var weatherForecastResult: WeatherForecast
    lateinit var mqttAndroidClient: MqttAndroidClient
    lateinit var weatherData: String
    lateinit var forecastData: String

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_main2, container, false)

        textView = view.findViewById(R.id.text)
        cityView = view.findViewById(R.id.city)
        tempView = view.findViewById(R.id.temp)
        stepsView = view.findViewById(R.id.stepsView)
        imageView = view.findViewById<ImageView>(R.id.imageView)
        predsView = view.findViewById(R.id.predsView)
        retrieveButton = view.findViewById(R.id.retrieveButton)
        retrieveButton.setOnClickListener({ requestAll() })
        forecastButton = view.findViewById(R.id.forecastButton)
        forecastButton.setOnClickListener({ sendForecast() })
        predsButton = view.findViewById(R.id.predsButton)
        predsButton.setOnClickListener({ sendToModel() })
        testModelButton = view.findViewById(R.id.testModel)
        testModelButton.setOnClickListener({ testModel() })
        bmrButton = view.findViewById(R.id.bmr)
        bmrButton.setOnClickListener({ navigateToBMR() })

        queue = Volley.newRequestQueue(activity)
        gson = Gson()

        sendButton = view.findViewById(R.id.sendButton)
        sendButton.setOnClickListener({ sendWeather() })
        syncButton = view.findViewById(R.id.syncButton)
        syncButton.setOnClickListener({ syncWithPi() })
        wifiButton = view.findViewById(R.id.wifiButton)
        wifiButton.setOnClickListener({ launchWifiSettings() })

        mqttAndroidClient = MqttAndroidClient(context, serverUri, clientId);

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
                val parts = message.toString().split(":")
                if (parts[0] == "model") {
                    predsView.text = parts[1].toString()
                } else {
                    stepsView.text = parts[1].toString()
                }

            }

            override fun connectionLost(cause: Throwable?) {
                println("Connection Lost")
            }

            // this method is called when the client succcessfully publishes to the broker
            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                println("Delivery Complete")
            }
        })


        return view
    }

    fun navigateToBMR(){
        val fragManager = fragmentManager
        if (fragManager != null) {
            val transaction = fragManager
                    .beginTransaction()
                    .replace(R.id.container, BMRFragment())
                    .addToBackStack("bmr_fragment")

            transaction.commit()
        }
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

    fun randomNumber(to: Int, from: Int): Int {
        val random = Random()
        return random.nextInt(to - from) + from
    }

    fun createWeather(): Any {
        val w: MutableMap<String, Any> = mutableMapOf()
        val max = randomNumber(89, 55)
        w.set("temp_max", max)
        w.set("temp_min", randomNumber(max - 5, 50))
        w.set("precip", randomNumber(3, 1))
        return w
    }

    fun createSteps(): Any {
        val s: MutableMap<String, Any> = mutableMapOf()
        s.set("predicted", randomNumber(12000, 3000))
        s.set("actual", randomNumber(12000, 3000))
        return s
    }

    fun sendToModel() {
        val t: MutableMap<String, Any> = mutableMapOf()
        t.set("weather", createWeather())
        t.set("steps", createSteps())
        val json = gson.toJson((t))

        val message = MqttMessage()
        message.payload = (json).toByteArray()
        mqttAndroidClient.publish(publishTopic, message)
    }

    fun testModel() {
        val pred = forecastData.replaceFirst("f", "t")
        val message = MqttMessage()
        message.payload = (pred).toByteArray()
        mqttAndroidClient.publish(publishTopic, message)
    }

    fun launchWifiSettings(){
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
        startActivity(Intent.createChooser(intent, "Wifi Settings"));
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
