package edu.utexas.mpc.samplerestweatherapp

import android.graphics.Bitmap
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
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

class MainActivity : AppCompatActivity() {

    val serverUri = "tcp://192.168.4.1:1883"
    val clientId = "EmergingTechMQTTClient"
    val subscribeTopic = "testTopic2"
    val publishTopic = "testTopic1"

    // I'm using lateinit for these widgets because I read that repeated calls to findViewById
    // are energy intensive
    lateinit var textView: TextView
    lateinit var cityView: TextView
    lateinit var tempView: TextView
    lateinit var imageView: ImageView
    lateinit var retrieveButton: Button
    lateinit var syncButton: Button

    lateinit var queue: RequestQueue
    lateinit var gson: Gson
    lateinit var mostRecentWeatherResult: WeatherResult
    lateinit var mqttAndroidClient: MqttAndroidClient


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textView = this.findViewById(R.id.text)
        cityView = this.findViewById(R.id.city)
        tempView = this.findViewById(R.id.temp)
        imageView = this.findViewById(R.id.imageView)
        retrieveButton = this.findViewById(R.id.retrieveButton)
        // when the user presses the syncbutton, this method will get called
        retrieveButton.setOnClickListener({ requestWeather() })
        queue = Volley.newRequestQueue(this)
        gson = Gson()

        syncButton = this.findViewById(R.id.syncButton)
        syncButton.setOnClickListener({ syncWithPi() })
        mqttAndroidClient = MqttAndroidClient(getApplicationContext(), serverUri, clientId);

        // when things happen in the mqtt client, these callbacks will be called
        mqttAndroidClient.setCallback(object: MqttCallbackExtended {

            // when the client is successfully connected to the broker, this method gets called
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                println("Connection Complete!!")
                // this subscribes the client to the subscribe topic
                mqttAndroidClient.subscribe(subscribeTopic, 0)
                val message = MqttMessage()
                message.payload = ("hello world").toByteArray()

                // this publishes a message to the publish topic
                mqttAndroidClient.publish(publishTopic, message)
            }

            // this method is called when a message is received that fulfills a subscription
            override fun messageArrived(topic: String?, message: MqttMessage?) {
                println(message)
                textView.text = message.toString()
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

    fun requestWeather(){
        val url = StringBuilder("https://api.openweathermap.org/data/2.5/weather?id=4254010&appid=3af6d2077570cd71fa2168839b7cb279&units=imperial").toString()
        val stringRequest = object : StringRequest(com.android.volley.Request.Method.GET, url,
                com.android.volley.Response.Listener<String> { response ->
                    //textView.text = response
                    mostRecentWeatherResult = gson.fromJson(response, WeatherResult::class.java)
                    textView.text = mostRecentWeatherResult.weather.get(0).main
                    cityView.text = mostRecentWeatherResult.name
                    tempView.text = mostRecentWeatherResult.main.temp.toString() + "°F"
                    val icon = mostRecentWeatherResult.weather.get(0).icon
                    requestIcon(icon)
                },
                com.android.volley.Response.ErrorListener { println("******That didn't work!") }) {}
        // Add the request to the RequestQueue.
        queue.add(stringRequest)
    }

    fun requestIcon(icon: String) {
        val url = "http://openweathermap.org/img/wn/" + icon + "@2x.png"
        Picasso.get().load(url).into(imageView)
    }

    // this method just connects the paho mqtt client to the broker
    fun syncWithPi(){
        println("+++++++ Connecting...")
        mqttAndroidClient.connect()
    }
}

class WeatherResult(val id: Int, val name: String, val temp: String, val cod: Int, val coord: Coordinates, val main: WeatherMain, val weather: Array<Weather>)
class Coordinates(val lon: Double, val lat: Double)
class Weather(val id: Int, val main: String, val description: String, val icon: String)
class WeatherMain(val temp: Double, val pressure: Int, val humidity: Int, val temp_min: Double, val temp_max: Double)

