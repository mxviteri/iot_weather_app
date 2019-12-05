package edu.utexas.mpc.samplerestweatherapp


import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

class BMRFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_bmr, container, false)

        val mainButton = view.findViewById<View>(R.id.mainPage)
        mainButton.setOnClickListener({ navigateToMain() })

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
}
