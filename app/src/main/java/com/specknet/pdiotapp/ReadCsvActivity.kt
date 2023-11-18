package com.specknet.pdiotapp
import android.os.Bundle
import android.util.Log
import android.widget.CalendarView
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity

class ReadCsvActivity : AppCompatActivity() {

    private lateinit var calendarView: CalendarView
    private lateinit var dataListView: ListView
    private val TAG = "ReadCsvActivity"

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.readcsv)

        calendarView = findViewById(R.id.calendarView)
        dataListView = findViewById(R.id.dataListView)

        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val selectedDate = "$dayOfMonth/${month + 1}/$year"
//            displayDataForDate(selectedDate)
            Log.d(TAG, "date = $selectedDate")
        }
    }


}