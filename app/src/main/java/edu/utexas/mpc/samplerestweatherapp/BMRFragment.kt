package edu.utexas.mpc.samplerestweatherapp


import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import kotlinx.android.synthetic.main.fragment_bmr.*
import kotlinx.android.synthetic.main.fragment_main2.view.*
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttMessage

class BMRFragment : Fragment() {

    val serverUri = "tcp://192.168.4.1:1883"
    val clientId = "EmergingTechMQTTClient"
    val subscribeTopic = "steps"
    val publishTopic = "weather"
    lateinit var mqttAndroidClient: MqttAndroidClient

    lateinit var check_goal_button: Button
    lateinit var weight: EditText
    lateinit var height: EditText
    lateinit var age: EditText
    lateinit var food: EditText
    lateinit var bmrData: String



    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_bmr, container, false)

        val mainButton = view.findViewById<View>(R.id.mainPage)
        mainButton.setOnClickListener({ navigateToMain() })
        check_goal_button = view.findViewById(R.id.check_goal_button)
        check_goal_button.setOnClickListener({ calculateBMR() })

        mqttAndroidClient = MqttAndroidClient(context, serverUri, clientId)

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
                if (parts[0] == "calGoal") {
                    goal_text_view.text = parts[1].toString()
                }
                    // need to write else for prediction
//                else {
//                    stepsView.text = parts[1].toString()
//                }

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

    fun navigateToMain(){
        val fragManager = fragmentManager
        if (fragManager != null) {
            val transaction = fragManager
                    .beginTransaction()
                    .replace(R.id.container, MainFragment())
                    .addToBackStack("main_fragment")

            transaction.commit()
        }
    }

    fun calculateBMR(){

        var bmr:Double = 0.0
        var message: String = ""

        var weightint = Integer.parseInt(weight.getText().toString())
        var heightint = Integer.parseInt(height.getText().toString())
        var ageint = Integer.parseInt(age.getText().toString())
        var foodint = Integer.parseInt(food.getText().toString())
        var gender = "male"



        if (gender == "male") {
            bmr = 66 + 6.23 * weightint + 12.7 * heightint - 6.8 * ageint
        }

        if (gender == "female") {
            bmr = 655 + 4.36 * weightint + 4.7 * heightint - 4.7 * ageint
        }

        bmrData = "c" + gender +  bmr.toString() + foodint.toString()

        sendBmrInfo()
    }


    fun sendBmrInfo(){
        val message = MqttMessage()
        message.payload = (bmrData).toByteArray()
        mqttAndroidClient.publish(publishTopic, message)
    }
}
