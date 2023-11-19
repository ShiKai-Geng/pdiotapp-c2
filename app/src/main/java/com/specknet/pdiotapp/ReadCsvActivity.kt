package com.specknet.pdiotapp
import android.os.Bundle
import android.util.Log
import android.widget.CalendarView
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import android.app.AlertDialog
import android.content.Context


class ReadCsvActivity : AppCompatActivity() {

    private lateinit var calendarView: CalendarView
    private lateinit var dataListView: ListView
    val TAG = "ReadCsvActivity"

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.readcsv)

        calendarView = findViewById(R.id.calendarView)
        dataListView = findViewById(R.id.dataListView)

        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
//            val selectedDate = "$dayOfMonth/${month + 1}/$year"
            val selectedDate = "$dayOfMonth-${month + 1}-$year"
//            displayDataForDate(selectedDate)
            Log.d(TAG, "date = $selectedDate")
            val dataList = readCsvData(selectedDate)
            if (dataList.isEmpty()) {
                showNoDataAlert(this)
            }

            Log.d(TAG, "dataList = $dataList")
            //displayDataForDate(selectedDate)
        }
    }

//    private fun displayDataForDate(date: String) {
//        val dataList = readCsvData(date)
//        // Update your ListView or RecyclerView here with dataList
//    }
//
    private fun readCsvData(date: String): List<File> {
        val dataList = mutableListOf<File>()

        val directory = getExternalFilesDir(null)
        if (directory != null) {
            if (directory.exists() && directory.isDirectory) {
                directory.listFiles()?.forEach { file ->
//                    if (file.isDirectory) {
//                        dataList.addAll(findCsvFilesInDirectory(file))
                    if (file.isFile && file.extension == "csv"&& file.name.contains(date)) {
                        dataList.add(file)
                    }
                }
            }
        }

//        val file = File(getExternalFilesDir(null), filename)
//        val date = filename.split("_")[2].split("-")
//        val day = date[0]
//        val month = date[1]
//        val year = date[2]
//        val date = "$day/$month/$year"

//        val bufferedReader = BufferedReader(FileReader(file))
//        bufferedReader.useLines { lines ->
//            lines.forEach { line ->
//                if (line.contains(date)) {
//                    // Parse the line into YourDataModel and add to dataList
//                }
//            }
//        }
        return dataList
}
    fun showNoDataAlert(context: Context) {
        val alertDialog = AlertDialog.Builder(context)
            .setTitle("Warning")
            .setMessage("No data for this date")
            .setPositiveButton("accept") { dialog, which ->
                // 这里可以处理确定按钮的点击事件
            }
            .setNegativeButton("cancel") { dialog, which ->
                // 这里可以处理取消按钮的点击事件
            }
            .create()

        alertDialog.show()
    }

}