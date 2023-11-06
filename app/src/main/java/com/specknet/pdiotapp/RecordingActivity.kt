package com.specknet.pdiotapp

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.specknet.pdiotapp.utils.Constants
import com.specknet.pdiotapp.utils.CountUpTimer
import com.specknet.pdiotapp.utils.RESpeckLiveData
import com.specknet.pdiotapp.utils.ThingyLiveData
import java.io.*
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.*
import java.lang.StringBuilder

import org.json.JSONObject;

class RecordingActivity : AppCompatActivity() {
    private val TAG = "RecordingActivity"
    lateinit var sensorTypeSpinner: Spinner
    lateinit var activityTypeSpinner: Spinner
    lateinit var activitySubtypeSpinner: Spinner
    lateinit var startRecordingButton: Button
    lateinit var cancelRecordingButton: Button
    lateinit var stopRecordingButton: Button
    lateinit var univSubjectIdInput: EditText
    lateinit var notesInput: EditText

    lateinit var timer: TextView
    lateinit var countUpTimer: CountUpTimer

    lateinit var respeckReceiver: BroadcastReceiver
    lateinit var thingyReceiver: BroadcastReceiver
    lateinit var respeckLooper: Looper
    lateinit var thingyLooper: Looper

    val respeckFilterTest = IntentFilter(Constants.ACTION_RESPECK_LIVE_BROADCAST)
    val thingyFilterTest = IntentFilter(Constants.ACTION_THINGY_BROADCAST)

    var sensorType = ""
    var universalSubjectId = "s1234567"
    var activityType = ""
    var activitySubtype = "Normal"
    var notes = ""

    private var mIsRespeckRecording = false
    private var mIsThingyRecording = false
    private lateinit var respeckOutputData: StringBuilder
    private lateinit var thingyOutputData: StringBuilder

//    private lateinit var respeckAccel: TextView
//    private lateinit var respeckGyro: TextView

//    private lateinit var thingyAccel: TextView
//    private lateinit var thingyGyro: TextView
//    private lateinit var thingyMag: TextView

    var thingyOn = false
    var respeckOn = false

    var respeckPool = ArrayList<FloatArray>();
    lateinit var textView: TextView

    // Live Data Fields
    lateinit var dataSet_res_accel_x: LineDataSet
    lateinit var dataSet_res_accel_y: LineDataSet
    lateinit var dataSet_res_accel_z: LineDataSet
    lateinit var allRespeckData: LineData
    lateinit var respeckChart: LineChart
    var time = 0f

    // activity mapping
    lateinit var activityEncodings: Array<Array<String>>

    // inference models
    lateinit var tfLiteResAcc: MyTFLiteInference
    // model paths
//    var respeck_accel_model_path = "c2_res_accel_1104.tflite"
    var respeck_accel_model_path = "t_c2_res_accel_1017.tflite"
    lateinit var respeck_both_model_path: String
    lateinit var respeck_thingy_accel_model_path: String
    var activity_type = "-"
    var activity_subtype ="-"
    var maxIndex =  1



    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate: here")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recording)

        respeckOutputData = StringBuilder()
        thingyOutputData = StringBuilder()
        textView = findViewById<TextView>(R.id.output)

//        setupViews()

        setupSpinners()

        setupButtons()
        setupCharts()

        setupInputs()

        // read json file
        val jsonFile = "activity_encodings.json"
        val jsonStr = application.assets.open(jsonFile).bufferedReader().use { it.readText() }
        val jsonObj = JSONObject(jsonStr)
        val activityLabels = jsonObj.getJSONArray("activity_encodings")
        // read list of list of strings as array of array of strings
        activityEncodings = Array(activityLabels.length()) { Array(2) { "" } }
        for (i in 0 until activityLabels.length()) {
            val activityLabel = activityLabels.getJSONArray(i)
            activityEncodings[i][0] = activityLabel.getString(0)
            activityEncodings[i][1] = activityLabel.getString(1)
        }
        Log.d(TAG, "onCreate: activityEncodings = " + activityEncodings.contentDeepToString())

        Log.d(TAG, "onCreate: setting up respeck receiver")
        // register respeck receiver
        // TODO
        respeckReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {

                val action = intent.action

                if (action == Constants.ACTION_RESPECK_LIVE_BROADCAST) {
                    // init model if not, on first receive
                    if (!this@RecordingActivity::tfLiteResAcc.isInitialized) {
                        tfLiteResAcc = MyTFLiteInference(context, modelFilePath = respeck_accel_model_path)  // initialize your inference class
                    }

                    val liveData = intent.getSerializableExtra(Constants.RESPECK_LIVE_DATA) as RESpeckLiveData
                    Log.d("Live", "onReceive: liveData = " + liveData)

                    updateRespeckData(liveData)

                    respeckOn = true
                    val accelData = floatArrayOf(liveData.accelX, liveData.accelY, liveData.accelZ)
                    respeckPool.add(accelData);
                    if (respeckPool.size >= 25) {
                        // Convert ArrayList<FloatArray> to 25x3 float array
                        val array2D = Array(25) { FloatArray(3) }
                        for (i in 0..24) {
                            array2D[i] = respeckPool[i]
                        }
                        // Clear respeckPool
                        respeckPool.clear()

                        val outputData = tfLiteResAcc.runInference(array2D)  // directly pass your 25x3 2D array
                        // Find the index of the maximum value in the outputData
                        maxIndex = outputData.indices.maxByOrNull { outputData[it] } ?: -1

                        // get activity type and subtype from maxIndex
                        activity_type = activityEncodings[maxIndex][0]
                        activity_subtype = activityEncodings[maxIndex][1]
                        // concat them as one string
                        val outputStr = "Predicted class: $activity_type - $activity_subtype";
                        runOnUiThread { textView.text = outputStr }
                    }
                    time += 1
                    updateGraph("respeck", liveData.accelX, liveData.accelY, liveData.accelZ)

                }

            }
        }

        // important to set this on a background thread otherwise it will block the UI
        val respeckHandlerThread = HandlerThread("bgProcThreadRespeck")
        respeckHandlerThread.start()
        respeckLooper = respeckHandlerThread.looper
        val respeckHandler = Handler(respeckLooper)
        this.registerReceiver(respeckReceiver, respeckFilterTest, null, respeckHandler)

        Log.d(TAG, "onCreate: registering thingy receiver")
        // register thingy receiver
        thingyReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                // TODO: modify logic here if we decide on respeck both or res+thingy accel
                val action = intent.action

                if (action == Constants.ACTION_THINGY_BROADCAST) {

                    val liveData = intent.getSerializableExtra(Constants.THINGY_LIVE_DATA) as ThingyLiveData
                    Log.d("Live", "onReceive: thingyLiveData = " + liveData)

                    updateThingyData(liveData)

                    thingyOn = true

                }

            }
        }

        // important to set this on a background thread otherwise it will block the UI
        val thingyHandlerThread = HandlerThread("bgProcThreadThingy")
        thingyHandlerThread.start()
        thingyLooper = thingyHandlerThread.looper
        val thingyHandler = Handler(thingyLooper)
        this.registerReceiver(thingyReceiver, thingyFilterTest, null, thingyHandler)

        timer = findViewById(R.id.count_up_timer_text)
        timer.visibility = View.INVISIBLE

        countUpTimer = object: CountUpTimer(1000) {
            override fun onTick(elapsedTime: Long) {
                val date = Date(elapsedTime)
                val formatter = SimpleDateFormat("mm:ss")
                val dateFormatted = formatter.format(date)
                timer.text = "Time elapsed: " + dateFormatted
            }
        }
        Log.d(TAG, "onCreate: done")

    }

//    private fun setupViews() {
//        respeckAccel = findViewById(R.id.respeck_accel)
//        respeckGyro = findViewById(R.id.respeck_gyro)
//
//        thingyAccel = findViewById(R.id.thingy_accel)
//        thingyGyro = findViewById(R.id.thingy_gyro)
//        thingyMag = findViewById(R.id.thingy_mag)
//    }

    private fun updateRespeckData(liveData: RESpeckLiveData) {
        if (mIsRespeckRecording) {
            val output = liveData.phoneTimestamp.toString() + "," +
                    liveData.accelX + "," + liveData.accelY + "," + liveData.accelZ + "," +
                    liveData.gyro.x + "," + liveData.gyro.y + "," + liveData.gyro.z + "," + activity_type + "," + activity_subtype + "," + maxIndex + "\n"

            respeckOutputData.append(output)
            Log.d(TAG, "updateRespeckData: appended to respeckoutputdata = " + output)

        }

        // update UI thread
//        runOnUiThread {
//            respeckAccel.text = getString(R.string.respeck_accel, liveData.accelX, liveData.accelY, liveData.accelZ)
//            respeckGyro.text = getString(R.string.respeck_gyro, liveData.gyro.x, liveData.gyro.y, liveData.gyro.z)
//        }
    }

    private fun updateThingyData(liveData: ThingyLiveData) {
        if (mIsThingyRecording) {
            val output = liveData.phoneTimestamp.toString() + "," +
                    liveData.accelX + "," + liveData.accelY + "," + liveData.accelZ + "," +
                    liveData.gyro.x + "," + liveData.gyro.y + "," + liveData.gyro.z + "," +
                    liveData.mag.x + "," + liveData.mag.y + "," + liveData.mag.z + "\n"

            thingyOutputData.append(output)
            Log.d(TAG, "updateThingyData: appended to thingyOutputData = " + output)
        }

//        // update UI thread
//        runOnUiThread {
//            thingyAccel.text = getString(R.string.thingy_accel, liveData.accelX, liveData.accelY, liveData.accelZ)
//            thingyGyro.text = getString(R.string.thingy_gyro, liveData.gyro.x, liveData.gyro.y, liveData.gyro.z)
//            thingyMag.text = getString(R.string.thingy_mag, liveData.mag.x, liveData.mag.y, liveData.mag.z)
//        }
    }

    private fun setupInputs() {
        Log.d(TAG, "setupInputs: here")
        univSubjectIdInput = findViewById(R.id.universal_subject_id_input)
        notesInput = findViewById(R.id.notes_input)
    }

    private fun setupSpinners() {
        Log.d(TAG, "setupSpinners: here")
        sensorTypeSpinner = findViewById(R.id.sensor_type_spinner)

        ArrayAdapter.createFromResource(
            this,
            R.array.sensor_type_array,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            sensorTypeSpinner.adapter = adapter
        }

        sensorTypeSpinner.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, viwq: View, position: Int, id: Long) {
                val selectedItem = parent.getItemAtPosition(position).toString()
                sensorType = selectedItem
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {
                sensorType = "Respeck"
            }
        }

//        activityTypeSpinner = findViewById(R.id.activity_type_spinner)
//        ArrayAdapter.createFromResource(
//            this,
//            R.array.activity_type_array,
//            android.R.layout.simple_spinner_item
//        ).also { adapter ->
//            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
//            activityTypeSpinner.adapter = adapter
//        }
//
//        activitySubtypeSpinner = findViewById(R.id.activity_subtype_spinner)
//        ArrayAdapter.createFromResource(
//            this,
//            R.array.activity_subtype_array,
//            android.R.layout.simple_spinner_item
//        ).also { adapter ->
//            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
//            activitySubtypeSpinner.adapter = adapter
//        }
    }
    fun setupCharts() {
        respeckChart = findViewById(R.id.respeck_chart)

        // Respeck

        time = 0f
        val entries_res_accel_x = ArrayList<Entry>()
        val entries_res_accel_y = ArrayList<Entry>()
        val entries_res_accel_z = ArrayList<Entry>()

        dataSet_res_accel_x = LineDataSet(entries_res_accel_x, "Accel X")
        dataSet_res_accel_y = LineDataSet(entries_res_accel_y, "Accel Y")
        dataSet_res_accel_z = LineDataSet(entries_res_accel_z, "Accel Z")

        dataSet_res_accel_x.setDrawCircles(false)
        dataSet_res_accel_y.setDrawCircles(false)
        dataSet_res_accel_z.setDrawCircles(false)

        dataSet_res_accel_x.setColor(
            ContextCompat.getColor(
                this,
                R.color.red
            )
        )
        dataSet_res_accel_y.setColor(
            ContextCompat.getColor(
                this,
                R.color.green
            )
        )
        dataSet_res_accel_z.setColor(
            ContextCompat.getColor(
                this,
                R.color.blue
            )
        )

        val dataSetsRes = ArrayList<ILineDataSet>()
        dataSetsRes.add(dataSet_res_accel_x)
        dataSetsRes.add(dataSet_res_accel_y)
        dataSetsRes.add(dataSet_res_accel_z)

        allRespeckData = LineData(dataSetsRes)
        respeckChart.data = allRespeckData
        respeckChart.invalidate()

        // Thingy

    }

    fun updateGraph(graph: String, x: Float, y: Float, z: Float) {
        // take the first element from the queue
        // and update the graph with it
        if (graph == "respeck") {
            dataSet_res_accel_x.addEntry(Entry(time, x))
            dataSet_res_accel_y.addEntry(Entry(time, y))
            dataSet_res_accel_z.addEntry(Entry(time, z))

            runOnUiThread {
                allRespeckData.notifyDataChanged()
                respeckChart.notifyDataSetChanged()
                respeckChart.invalidate()
                respeckChart.setVisibleXRangeMaximum(150f)
                respeckChart.moveViewToX(respeckChart.lowestVisibleX + 40)
            }
        }
    }
    private fun enableView(view: View) {
        view.isClickable = true
        view.isEnabled = true
    }

    private fun disableView(view: View) {
        view.isClickable = false
        view.isEnabled = false
    }

    private fun setupButtons() {
        startRecordingButton = findViewById(R.id.start_recording_button)
        cancelRecordingButton = findViewById(R.id.cancel_recording_button)
        stopRecordingButton = findViewById(R.id.stop_recording_button)

        disableView(stopRecordingButton)
        disableView(cancelRecordingButton)

        startRecordingButton.setOnClickListener {

            getInputs()

            if (universalSubjectId.length != 8) {
                Toast.makeText(this, "Input a correct student id", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (sensorType == "Respeck" && !respeckOn) {
                Toast.makeText(this, "Respeck is not on! Check connection.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }


            if (sensorType == "Thingy" && !thingyOn) {
                Toast.makeText(this, "Thingy is not on! Check connection.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Toast.makeText(this, "Starting recording", Toast.LENGTH_SHORT).show()

            disableView(startRecordingButton)

            enableView(cancelRecordingButton)
            enableView(stopRecordingButton)

            disableView(sensorTypeSpinner)
//            disableView(activityTypeSpinner)
//            disableView(activitySubtypeSpinner)
            disableView(univSubjectIdInput)
            disableView(notesInput)

            startRecording()
        }

        cancelRecordingButton.setOnClickListener {
            Toast.makeText(this, "Cancelling recording", Toast.LENGTH_SHORT).show()

            enableView(startRecordingButton)
            disableView(cancelRecordingButton)
            disableView(stopRecordingButton)

            enableView(sensorTypeSpinner)
//            enableView(activityTypeSpinner)
//            enableView(activitySubtypeSpinner)
            enableView(univSubjectIdInput)
            enableView(notesInput)

            cancelRecording()

        }

        stopRecordingButton.setOnClickListener {
            Toast.makeText(this, "Stop recording", Toast.LENGTH_SHORT).show()

            enableView(startRecordingButton)
            disableView(cancelRecordingButton)
            disableView(stopRecordingButton)

            enableView(sensorTypeSpinner)
//            enableView(activityTypeSpinner)
//            enableView(activitySubtypeSpinner)
            enableView(univSubjectIdInput)
            enableView(notesInput)

            stopRecording()
        }

    }

    private fun cancelRecording() {
        countUpTimer.stop()
        countUpTimer.reset()
        timer.text = "Time elapsed: 00:00"

        // reset output data
        respeckOutputData = StringBuilder()
        thingyOutputData = StringBuilder()

        mIsRespeckRecording = false
        mIsThingyRecording = false
    }

    private fun startRecording() {
        timer.visibility = View.VISIBLE

        countUpTimer.start()

        if (sensorType.equals("Thingy")) {
            mIsThingyRecording = true
            mIsRespeckRecording = false
        }
        else {
            mIsRespeckRecording = true
            mIsThingyRecording = false
        }

    }

    private fun stopRecording() {

        countUpTimer.stop()
        countUpTimer.reset()
        timer.text = "Time elapsed: 00:00"

        Log.d(TAG, "stopRecording")

        mIsRespeckRecording = false
        mIsThingyRecording = false

        saveRecording()

    }

    private fun saveRecording() {
        val currentTime = System.currentTimeMillis()
        var formattedDate = ""
        try {
            formattedDate = SimpleDateFormat("dd-MM-yyyy_HH-mm-ss", Locale.UK).format(Date())
            Log.i(TAG, "saveRecording: formattedDate = " + formattedDate)
        } catch (e: Exception) {
            Log.i(TAG, "saveRecording: error = ${e.toString()}")
            formattedDate = currentTime.toString()
        }
        val filename = "${sensorType}_${universalSubjectId}_${activityType}_${activitySubtype}_${formattedDate}.csv" // TODO format this to human readable

        val file = File(getExternalFilesDir(null), filename)

        Log.d(TAG, "saveRecording: filename = " + file.toString())

        val dataWriter: BufferedWriter

        // Create file for current day and append header, if it doesn't exist yet
        try {
            val exists = file.exists()
            dataWriter = BufferedWriter(OutputStreamWriter(FileOutputStream(file, true)))

            if (!exists) {
                Log.d(TAG, "saveRecording: filename doesn't exist")

                // the header columns in here
                dataWriter.append("# Sensor type: $sensorType").append("\n")
                dataWriter.append("# Activity type: $activityType").append("\n")
                dataWriter.append("# Activity subtype: $activitySubtype").append("\n")
                dataWriter.append("# Subject id: $universalSubjectId").append("\n")
                dataWriter.append("# Notes: $notes").append("\n")

                if (sensorType.equals("Thingy")) {
                    dataWriter.write(Constants.RECORDING_CSV_HEADER_THINGY)
                }
                else {
                    dataWriter.write(Constants.RECORDING_CSV_HEADER_RESPECK)
                }
                dataWriter.newLine()
                dataWriter.flush()
            }
            else {
                Log.d(TAG, "saveRecording: filename exists")
            }

            if (sensorType.equals("Thingy")) {
                if (thingyOutputData.isNotEmpty()) {
                    dataWriter.write(thingyOutputData.toString())
                    dataWriter.flush()

                    Log.d(TAG, "saveRecording: thingy recording saved")
                }
                else {
                    Log.d(TAG, "saveRecording: no data from thingy during recording period")
                }
            }
            else {
                if (respeckOutputData.isNotEmpty()) {
                    dataWriter.write(respeckOutputData.toString())
                    dataWriter.flush()

                    Log.d(TAG, "saveRecording: respeck recording saved")
                }
                else {
                    Log.d(TAG, "saveRecording: no data from respeck during recording period")
                }
            }

            dataWriter.close()

            respeckOutputData = StringBuilder()
            thingyOutputData = StringBuilder()

            Toast.makeText(this, "Recording saved!", Toast.LENGTH_SHORT).show()
        }
        catch (e: IOException) {
            Toast.makeText(this, "Error while saving recording!", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "saveRespeckRecording: Error while writing to the respeck file: " + e.message )
        }
    }

    private fun getInputs() {

        universalSubjectId = univSubjectIdInput.text.toString().toLowerCase().trim()
        activityType = "activity"
        activitySubtype = "activity_subtype"
        sensorType = sensorTypeSpinner.selectedItem.toString()
        notes = notesInput.text.toString().trim()

    }

    override fun onDestroy() {
        unregisterReceiver(respeckReceiver)
        unregisterReceiver(thingyReceiver)
        respeckLooper.quit()
        thingyLooper.quit()

        if (mIsThingyRecording || mIsRespeckRecording) {
            saveRecording()
        }

        super.onDestroy()

    }

}