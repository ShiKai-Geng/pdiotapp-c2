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
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.system.measureNanoTime

class RecordingActivity : AppCompatActivity() {
    private val TAG = "RecordingActivity"
//    lateinit var sensorTypeSpinner: Spinner
//    lateinit var activityTypeSpinner: Spinner
//    lateinit var activitySubtypeSpinner: Spinner
    lateinit var startRecordingButton: Button
    lateinit var cancelRecordingButton: Button
    lateinit var stopRecordingButton: Button
    lateinit var univSubjectIdInput: TextView
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
    lateinit var activityEncodingsSubAct: Array<String>

    // inference models
    lateinit var tfLiteResAcc: Interpreter
    var inputIndex = 0
    lateinit var inputShape: IntArray
    var outputIndex = 0
    lateinit var outputShape: IntArray

    lateinit var tfLiteResAccSubActs: Interpreter
    var inputIndexSubActs = 0
    lateinit var inputShapeSubActs: IntArray
    var outputIndexSubActs = 0
    lateinit var outputShapeSubActs: IntArray

    // model paths
    var respeck_accel_model_path = "c2_res_accel_1116_s_26_bn_nonorm.tflite"
    var subacts_model_path = "c2_res_accel_11_18_cnn_subactivities_only_guifu.tflite"

    lateinit var respeck_both_model_path: String
    lateinit var respeck_thingy_accel_model_path: String
    var activity_type = "-"
    var activity_subtype ="-"
    var activity_subtype_SubAct = "-"
    var maxIndex =  -1
    var maxIndexSubAct = -1

    lateinit var username: String

    lateinit var stationaryActivities: Array<String>
    lateinit var movingActivities: Array<String>

    var time_main_model_init = 0L
    var time_main_inference = 0L
    var time_subact_model_init = 0L
    var time_subact_inference = 0L


    private val window_size = 25

    private fun loadModelFile(modelPath: String, context: Context): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate: here")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recording)

        // update username
        username = intent.getStringExtra("username").toString()
        Log.d(TAG, "onCreate: username = $username")
        val usernameTextView = findViewById<TextView>(R.id.universal_subject_id_input)
        runOnUiThread {
            usernameTextView.text = username
        }

        respeckOutputData = StringBuilder()
        thingyOutputData = StringBuilder()
        textView = findViewById<TextView>(R.id.output)

//        setupViews()

//        setupSpinners()

        setupButtons()
        setupCharts()

        setupInputs()

        stationaryActivities = arrayOf<String>("sitting/standing", "lyingBack", "lyingLeft", "lyingRight", "lyingStomach")
        movingActivities = arrayOf<String>("ascending", "descending", "miscMovement", "normalWalking", "running", "shuffleWalking")

        // read json file
        val jsonFile = "activity_classes_1115_26.json"
        val jsonStr = application.assets.open(jsonFile).bufferedReader().use { it.readText() }
        val jsonObj = JSONObject(jsonStr)
        val activityLabels = jsonObj.getJSONArray("activity_classes")
        // read list of list of strings as array of array of strings
        activityEncodings = Array(activityLabels.length()) { Array(2) { "" } }
        for (i in 0 until activityLabels.length()) {
            val activityLabel = activityLabels.getJSONArray(i)
            activityEncodings[i][0] = activityLabel.getString(0)
            activityEncodings[i][1] = activityLabel.getString(1)
        }
        Log.d(TAG, "onCreate: activityEncodings = " + activityEncodings.contentDeepToString())

        // read sub activities
        val jsonFileSubActs = "subactivities_only.json"
        val jsonStrSubActs = application.assets.open(jsonFileSubActs).bufferedReader().use { it.readText() }
        val jsonObjSubActs = JSONObject(jsonStrSubActs)
        val activityLabelsSubActs = jsonObjSubActs.getJSONArray("activity_classes")
        // read {"activity_classes": ["breathingNormal", "coughing", "hyperventilating", "other"]}
        activityEncodingsSubAct = Array(activityLabelsSubActs.length()) { "" }
        for (i in 0 until activityLabelsSubActs.length()) {
            val activityLabelSubAct = activityLabelsSubActs.getString(i)
            activityEncodingsSubAct[i] = activityLabelSubAct
        }
        Log.d(TAG, "onCreate: activityEncodingsSubAct = " + activityEncodingsSubAct.contentDeepToString())

        val user = intent.getStringExtra("user")

        Log.d(TAG, "onCreate: setting up respeck receiver")
        // register respeck receiver
        // TODO
        respeckReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {

                val action = intent.action

                if (action == Constants.ACTION_RESPECK_LIVE_BROADCAST) {
                    // init model if not, on first receive
                    if (!this@RecordingActivity::tfLiteResAcc.isInitialized) {
                        Log.d(TAG, "load model")
                        // print out CPU cycle
//                        val runtime = Runtime.getRuntime()
//                        timer_mainModelInitBegin = runtime.totalMemory() - runtime.freeMemory()

                        time_main_model_init = measureNanoTime {
                            tfLiteResAcc =
                                Interpreter(loadModelFile(respeck_accel_model_path, context))
                        }
//                        timer_mainModelInitEnd = runtime.totalMemory() - runtime.freeMemory()
                        // Get input and output details
                        inputIndex = 0
                        inputShape = tfLiteResAcc.getInputTensor(inputIndex).shape()
                        outputIndex = 0
                        outputShape = tfLiteResAcc.getOutputTensor(outputIndex).shape()
                        Log.d(TAG, "inputShape = " + inputShape.contentToString())
                        Log.d(TAG, "outputShape = " + outputShape.contentToString())
                    }
                    if (!this@RecordingActivity::tfLiteResAccSubActs.isInitialized) {
                        Log.d(TAG, "load sub activity model")
                        time_subact_model_init = measureNanoTime {
                            tfLiteResAccSubActs =
                                Interpreter(loadModelFile(subacts_model_path, context))
                        }
                        // Get input and output details
                        inputIndexSubActs = 0
                        inputShapeSubActs = tfLiteResAccSubActs.getInputTensor(inputIndexSubActs).shape()
                        outputIndexSubActs = 0
                        outputShapeSubActs = tfLiteResAccSubActs.getOutputTensor(outputIndexSubActs).shape()
                        Log.d(TAG, "inputShapeSubActs = " + inputShapeSubActs.contentToString())
                        Log.d(TAG, "outputShapeSubActs = " + outputShapeSubActs.contentToString())
                    }

                    val liveData = intent.getSerializableExtra(Constants.RESPECK_LIVE_DATA) as RESpeckLiveData
                    Log.d("Live", "onReceive: liveData = " + liveData)

                    val phoneTimestamp = liveData.phoneTimestamp
                    val respeckTimestamp = liveData.respeckTimestamp
                    val currentTime = System.currentTimeMillis()
                    Log.d(TAG, "onReceive: phoneTimestamp = $phoneTimestamp")
                    Log.d(TAG, "onReceive: respeckTimestamp = $respeckTimestamp")
                    Log.d(TAG, "onReceive: currentTime = $currentTime")

                    updateRespeckData(liveData)

                    respeckOn = true
                    val accelData = floatArrayOf(liveData.accelX, liveData.accelY, liveData.accelZ)
                    respeckPool.add(accelData);
                    if (respeckPool.size >= window_size) {
                        // Convert ArrayList<FloatArray> to 25x3 float array
                        val array2D = Array(window_size) { FloatArray(3) }
                        for (i in 0 until window_size) {
                            array2D[i][0] = ((respeckPool[i][0]))
                            array2D[i][1] = ((respeckPool[i][1]))
                            array2D[i][2] = ((respeckPool[i][2]))
                        }
                        // Clear respeckPool
                        respeckPool.clear()
                        Log.d(TAG, "onReceive: array2D = " + array2D.contentDeepToString())
                        // TODO: see if by using rewind() can we make inputBuffer a field, not to allocate it every time
                        val inputBuffer = ByteBuffer.allocateDirect(4 * 25 * 3)
                        val inputBufferSubActs = ByteBuffer.allocateDirect(4 * 25 * 3)
                        inputBuffer.order(ByteOrder.nativeOrder())
                        inputBufferSubActs.order(ByteOrder.nativeOrder())
                        inputBuffer.rewind();
                        inputBufferSubActs.rewind();
                        // Converting 2D array data into ByteBuffer format
                        for (i in array2D.indices) {
                            for (j in array2D[i].indices) {
                                inputBuffer.putFloat(array2D[i][j])
                                inputBufferSubActs.putFloat(array2D[i][j])
                            }
                        }
//                        val bufferStr = inputBuffer.asCharBuffer().toString()
//                        Log.d(TAG, "Buffer: $bufferStr")
                        // Run inference
                        val outputBuffer = ByteBuffer.allocateDirect(4 * 26)
                        val outputBufferSubActs = ByteBuffer.allocateDirect(4 * 4)
                        outputBuffer.order(ByteOrder.nativeOrder())
                        outputBufferSubActs.order(ByteOrder.nativeOrder())
                        time_main_inference = measureNanoTime {
                            tfLiteResAcc.run(inputBuffer, outputBuffer)
                        }
                        time_subact_inference = measureNanoTime {
                            tfLiteResAccSubActs.run(inputBufferSubActs, outputBufferSubActs)
                        }
                        // Converting ByteBuffer output to float array
                        val outputData = FloatArray(outputShape[1])
                        val outputDataSubActs = FloatArray(outputShapeSubActs[1])
                        outputBuffer.rewind()
                        outputBufferSubActs.rewind()
                        outputBuffer.asFloatBuffer().get(outputData)
                        outputBufferSubActs.asFloatBuffer().get(outputDataSubActs)
                        Log.d(TAG, "onCreate: outputData = " + outputData.contentToString())
                        Log.d(TAG, "onCreate: outputDataSubActs = " + outputDataSubActs.contentToString())

                        // Find the index of the maximum value in the outputData
                        maxIndex = outputData.indices.maxByOrNull { outputData[it] } ?: -1
                        maxIndexSubAct = outputDataSubActs.indices.maxByOrNull { outputDataSubActs[it] } ?: -1
                        Log.d(TAG, "onCreate: maxIndex = $maxIndex")
                        Log.d(TAG, "onCreate: maxIndexSubAct = $maxIndexSubAct")
                        // get activity type and subtype from maxIndex
                        activity_type = activityEncodings[maxIndex][0]
                        activity_subtype = activityEncodings[maxIndex][1]
                        activity_subtype_SubAct = activityEncodingsSubAct[maxIndexSubAct]
                        var outputStr = "";
                        if (activity_type in stationaryActivities) {
                            outputStr = "$activity_type / $activity_subtype_SubAct"
                        } else if (activity_type in movingActivities) {
                            outputStr = activity_type
                        } else {
                            outputStr = "Unrecognized activity"
                        }
                        // concat them as one string  // TODO: remove this comparison
//                        outputStr = "$activity_type / $activity_subtype_SubAct"
                        Log.d(TAG, "outputStr: $outputStr")
                        runOnUiThread { textView.text = outputStr }
                        // print out timer stats
                        Log.d(TAG, "onCreate: time_main_model_init = $time_main_model_init")
                        Log.d(TAG, "onCreate: time_main_inference = $time_main_inference")
                        Log.d(TAG, "onCreate: time_subact_model_init = $time_subact_model_init")
                        Log.d(TAG, "onCreate: time_subact_inference = $time_subact_inference")
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

    private fun softmax(input: FloatArray): FloatArray {
        // Find the maximum value in the input array
        var max = input[0]
        for (i in 1 until input.size) {
            if (input[i] > max) {
                max = input[i]
            }
        }

        // Apply softmax function
        val result = FloatArray(input.size)
        var sum = 0.0f
        for (i in input.indices) {
            result[i] = exp((input[i] - max).toDouble()).toFloat()
            sum += result[i]
        }

        // Normalize to get probabilities
        for (i in result.indices) {
            result[i] /= sum
        }
        return result
    }

    private fun getStd(numbers: List<Float>, mean: Double): Float {
        val variance = numbers.sumOf { (it - mean).pow(2) } / numbers.size
        return sqrt(variance).toFloat()
    }

    private fun updateRespeckData(liveData: RESpeckLiveData) {
        if (mIsRespeckRecording) {
            val timestamp = liveData.phoneTimestamp
            val date = Date(timestamp)
            val output = date.toString()  + "," +
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

//    private fun setupSpinners() {
//        Log.d(TAG, "setupSpinners: here")
////        sensorTypeSpinner = findViewById(R.id.sensor_type_spinner)
//
//        ArrayAdapter.createFromResource(
//            this,
//            R.array.sensor_type_array,
//            android.R.layout.simple_spinner_item
//        ).also { adapter ->
//            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
//            sensorTypeSpinner.adapter = adapter
//        }
//
//        sensorTypeSpinner.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
//            override fun onItemSelected(parent: AdapterView<*>, viwq: View, position: Int, id: Long) {
//                val selectedItem = parent.getItemAtPosition(position).toString()
//                sensorType = selectedItem
//            }
//
//            override fun onNothingSelected(p0: AdapterView<*>?) {
//                sensorType = "Respeck"
//            }
//        }


//    }
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

//            if (universalSubjectId.length != 8) {
//                Toast.makeText(this, "Input a correct student id", Toast.LENGTH_SHORT).show()
//                return@setOnClickListener
//            }

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

//            disableView(sensorTypeSpinner)

            disableView(univSubjectIdInput)
            disableView(notesInput)

            startRecording()
        }

        cancelRecordingButton.setOnClickListener {
            Toast.makeText(this, "Cancelling recording", Toast.LENGTH_SHORT).show()

            enableView(startRecordingButton)
            disableView(cancelRecordingButton)
            disableView(stopRecordingButton)

//            enableView(sensorTypeSpinner)

            enableView(univSubjectIdInput)
            enableView(notesInput)

            cancelRecording()

        }

        stopRecordingButton.setOnClickListener {
            Toast.makeText(this, "Stop recording", Toast.LENGTH_SHORT).show()

            enableView(startRecordingButton)
            disableView(cancelRecordingButton)
            disableView(stopRecordingButton)

//            enableView(sensorTypeSpinner)

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
//        val filename = "${sensorType}_${universalSubjectId}_${activityType}_${activitySubtype}_${formattedDate}.csv" // TODO format this to human readable
        val filename = "${sensorType}_${universalSubjectId}_${formattedDate}.csv" // TODO format this to human readable
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
//                dataWriter.append("# Activity type: $activityType").append("\n")
//                dataWriter.append("# Activity subtype: $activitySubtype").append("\n")
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

        universalSubjectId = univSubjectIdInput.text.toString().trim()
        activityType = "activity"
        activitySubtype = "activity_subtype"
//        sensorType = sensorTypeSpinner.selectedItem.toString()
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
