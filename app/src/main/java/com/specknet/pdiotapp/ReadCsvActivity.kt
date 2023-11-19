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
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager

class ReadCsvActivity : AppCompatActivity() {

    private lateinit var calendarView: CalendarView
    private lateinit var dataListView: ListView
    private lateinit var recyclerView: RecyclerView
    private lateinit var fileAdapter: FileAdapter
    val TAG = "ReadCsvActivity"

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.readcsv)

        calendarView = findViewById(R.id.calendarView)
//        dataListView = findViewById(R.id.dataListView)

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

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
            fileAdapter = FileAdapter(dataList) { file ->
                // 处理文件点击事件，例如打开文件
            }

            recyclerView.adapter = fileAdapter
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
}