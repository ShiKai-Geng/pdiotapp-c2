package com.specknet.pdiotapp
import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.widget.CalendarView
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import android.app.AlertDialog
import android.content.Context
import android.os.Build
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.LinearLayoutManager
import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.TimeZone

class ReadCsvActivity : AppCompatActivity() {

    private lateinit var calendarView: CalendarView
    private lateinit var dataListView: ListView
    private lateinit var recyclerView: RecyclerView
    private lateinit var fileAdapter: FileAdapter
    private lateinit var sensorDataAdapter: SensorDataAdapter

    private lateinit var username: String

    val TAG = "ReadCsvActivity"

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.readcsv)

        username = intent.getStringExtra("username").toString()

        calendarView = findViewById(R.id.calendarView)
//        dataListView = findViewById(R.id.dataListView)

        recyclerView = findViewById(R.id.recyclerView)
        var Csv = mutableListOf<SensorData>()
        recyclerView.layoutManager = LinearLayoutManager(this)
        Log.d("what", "read csv activity")

        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
//            val selectedDate = "$dayOfMonth/${month + 1}/$year"
            val selectedDate = "$dayOfMonth-${month + 1}-$year"
            Log.d(TAG, "selectedDate = $selectedDate")
//            displayDataForDate(selectedDate)
            Log.d(TAG, "date = $selectedDate")
            val dataList = readCsvData(selectedDate)
            if (dataList.isEmpty()) {
                showNoDataAlert(this)
            }

            Log.d(TAG, "dataList = $dataList")
            fileAdapter = FileAdapter(dataList) { file ->
                Csv = readCsvFile(file.absolutePath)
                val sub_csv = Csv[0]
                Log.d(TAG, "Csv = $sub_csv")
                val sensorDataList = Csv
                sensorDataAdapter = SensorDataAdapter(sensorDataList)
                recyclerView.adapter = sensorDataAdapter
                // 处理文件点击事件，例如打开文件
            }

            recyclerView.adapter = fileAdapter
            //displayDataForDate(selectedDate)
        }
        // select today automatically
        val selectedDate = calendarView.date.toString()
        // Convert the timestamp to Instant
        val instant = Instant.ofEpochMilli(selectedDate.toLong())
        // Format the Instant using DateTimeFormatter
        val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
        val formattedDate = formatter.format(instant.atZone(ZoneId.systemDefault()))
//        val formatedDate = Date(selectedDate).toString()
        Log.d(TAG, "mimiced selectedDate = $formattedDate")
        val dataList = readCsvData(formattedDate)
        if (dataList.isEmpty()) {
            showNoDataAlert(this)
        }

        Log.d(TAG, "dataList = $dataList")
        fileAdapter = FileAdapter(dataList) { file ->
            Csv = readCsvFile(file.absolutePath)
            val sub_csv = Csv[0]
            Log.d(TAG, "Csv = $sub_csv")
            val sensorDataList = Csv
            sensorDataAdapter = SensorDataAdapter(sensorDataList)
            recyclerView.adapter = sensorDataAdapter
            // 处理文件点击事件，例如打开文件
        }

        recyclerView.adapter = fileAdapter
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
                    if (file.isFile &&
                        file.extension == "csv" &&
                        file.name.contains(date)) {
                        // username checks
                        val parts = file.name.split("_")
                        if (parts.size >= 2) {
                            val fileUsername = parts[1]
                            if (fileUsername == username) {
                                dataList.add(file)
                            }
                        }
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
//        val alertDialog = AlertDialog.Builder(context)
//            .setTitle("Warning")
//            .setMessage("No data for this date")
//            .setPositiveButton("accept") { dialog, which ->
//                // 这里可以处理确定按钮的点击事件
//            }
//            .setNegativeButton("cancel") { dialog, which ->
//                // 这里可以处理取消按钮的点击事件
//            }
//            .create()
        Toast.makeText(this, "No Data for this date", Toast.LENGTH_SHORT).show()
//        alertDialog.show()
    }
    class FileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.textView)
    }

    class FileAdapter(private val fileList: List<File>, private val onClick: (File) -> Unit) :
        RecyclerView.Adapter<FileViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.list_item, parent, false)
            return FileViewHolder(view)
        }

        override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
            val file = fileList[position]
            holder.textView.text = file.name
            holder.itemView.setOnClickListener { onClick(file) }
        }

        override fun getItemCount(): Int = fileList.size
    }
    data class SensorData(
        val timestamp: String,
        val accelX: String,
        val accelY: String,
        val accelZ: String,
        val gyroX: String,
        val gyroY: String,
        val gyroZ: String,
        val activityType: String,
        val activitySubtype: String,
        val index: String
    )

    fun readCsvFile(filePath: String): MutableList<SensorData> {
        val dataList = mutableListOf<SensorData>()
        try {
            val file = File(filePath)
            val br = BufferedReader(FileReader(file))
            br.readLine() // Skip header
            var line: String?
            var lineNumber = 0

            while (br.readLine().also { line = it } != null) {
                lineNumber++

                // Check if it's time to read a line (every 25 lines or last line)
                if (lineNumber % 25 == 0 || !br.ready()) {
                    val tokens = line!!.split(",")
                    if (tokens.size >= 10) {
                        val timestr = "${tokens[0].split(" ")[3]} ${tokens[0].split(" ")[4]}"
                        dataList.add(
                            SensorData(
                                timestamp = timestr,
                                accelX = tokens[1],
                                accelY = tokens[2],
                                accelZ = tokens[3],
                                gyroX = tokens[4],
                                gyroY = tokens[5],
                                gyroZ = tokens[6],
                                activityType = tokens[7],
                                activitySubtype = tokens[8],
                                index = tokens[9]
                            )
                        )
                    }
                }
            }
            br.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return dataList
    }


    class SensorDataAdapter(private val sensorDataList: List<SensorData>) : RecyclerView.Adapter<SensorDataAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val timestampTextView: TextView = view.findViewById(R.id.timestampTextView)
            val activityTypeTextView: TextView = view.findViewById(R.id.activityTypeTextView)
            val activitySubtypeTextView: TextView = view.findViewById(R.id.activitySubtypeTextView)
            // Define other TextViews for accelX, accelY, etc.
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.table_row_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val sensorData = sensorDataList[position]
            holder.timestampTextView.text = sensorData.timestamp
            holder.activityTypeTextView.text = sensorData.activityType
            holder.activitySubtypeTextView.text = sensorData.activitySubtype
            // Set text for other TextViews from sensorData
        }

        override fun getItemCount() = sensorDataList.size
    }

}