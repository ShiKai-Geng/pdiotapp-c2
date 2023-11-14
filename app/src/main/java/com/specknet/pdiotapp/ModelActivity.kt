
package com.specknet.pdiotapp

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.specknet.pdiotapp.utils.Constants
import com.specknet.pdiotapp.utils.RESpeckLiveData
import org.tensorflow.lite.task.audio.classifier.AudioClassifier
import java.util.*
import kotlin.collections.ArrayList
import kotlin.concurrent.scheduleAtFixedRate

import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel


class ModelActivity : AppCompatActivity() {
    var TAG = "ModelActivity"

    // TODO 2.1: defines the model to be used
    var modelPath = "t_c2_res_accel_1017.tflite"

//    // TODO 2.2: defining the minimum threshold
//    var probabilityThreshold: Float = 0.3f

    var respeckPool = ArrayList<FloatArray>();
    lateinit var respeckReceiver: BroadcastReceiver
    val filterTestRespeck = IntentFilter(Constants.ACTION_RESPECK_LIVE_BROADCAST)
    lateinit var looperRespeck: Looper

    lateinit var textView: TextView

    // Live Data Fields
    lateinit var dataSet_res_accel_x: LineDataSet
    lateinit var dataSet_res_accel_y: LineDataSet
    lateinit var dataSet_res_accel_z: LineDataSet
    lateinit var allRespeckData: LineData
    lateinit var respeckChart: LineChart
    var time = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        //
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_model)
        textView = findViewById<TextView>(R.id.output)
        setupCharts()
//        val outputStr = "fuck you";
//                        runOnUiThread { textView.text = "Predicted class: $maxIndex" }
//        runOnUiThread { textView.text = outputStr }
        Log.i("yes", "what")
        // GET LIVE DATA
        respeckReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                println("received")
//
                Log.i("yes", "received")
                val action = intent.action

//                val tempStr = "received";
//                runOnUiThread { textView.text = tempStr }

                if (action == Constants.ACTION_RESPECK_LIVE_BROADCAST) {

                    val liveData =
                        intent.getSerializableExtra(Constants.RESPECK_LIVE_DATA) as RESpeckLiveData
//                    Log.d("Live", "onReceive: liveData = " + liveData)
                    // TODO convert to xyz float only
                    val accelData = floatArrayOf(liveData.accelX, liveData.accelY, liveData.accelZ)
                    respeckPool.add(accelData);
//                    val tempStr = respeckPool.size.toString();
//                    runOnUiThread { textView.text = tempStr }
                    if (respeckPool.size >= 25) {
                        // Convert ArrayList<FloatArray> to 25x3 float array
                        val array2D = Array(25) { FloatArray(3) }
                        for (i in 0..24) {
                            array2D[i] = respeckPool[i]
                        }
                        // Clear respeckPool
                        respeckPool.clear()

                        val myTFLite = MyTFLiteInference(context)  // initialize your inference class

                        val outputData = myTFLite.runInference(array2D)  // directly pass your 25x3 2D array
                        // TODO: display max index
                        // Find the index of the maximum value in the outputData
                        val maxIndex = outputData.indices.maxByOrNull { outputData[it] } ?: -1

                        // Assuming you just want to display the index
                        val outputStr = "Predicted class: $maxIndex";
//                        runOnUiThread { textView.text = "Predicted class: $maxIndex" }
                        runOnUiThread { textView.text = outputStr }
                        // If you have an array of labels corresponding to the classes,
                        // you can display the corresponding label instead of the index.sj
                        // For example:
                        // val labels = arrayOf("Label1", "Label2", ... , "Label44")
                        // textView.text = "Predicted class: ${labels[maxIndex]}"


                    }
                    time += 1
                    updateGraph("respeck", liveData.accelX, liveData.accelY, liveData.accelZ)

                }

            }
        }
        // register receiver on another thread
        val handlerThreadRespeck = HandlerThread("bgThreadRespeckLive")
        handlerThreadRespeck.start()
        looperRespeck = handlerThreadRespeck.looper
        val handlerRespeck = Handler(looperRespeck)
        this.registerReceiver(respeckReceiver, filterTestRespeck, null, handlerRespeck);
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
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(respeckReceiver)

        looperRespeck.quit()

    }


    }





class MyTFLiteInference(context: Context, modelFilePath: String = "c2_res_accel_1104.tflite") {

    private var interpreter: Interpreter

    init {
        // 初始化 TensorFlow Lite 解释器
        interpreter = Interpreter(loadModelFile(context, modelFilePath))
    }

    // 加载模型文件
    private fun loadModelFile(context: Context, modelFilePath: String): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(modelFilePath) // 替换为你的模型文件名
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    // 执行推理
    fun runInference(inputData: Array<FloatArray>): FloatArray {
        val inputBuffer = ByteBuffer.allocateDirect(4 * 25 * 3)
        inputBuffer.order(ByteOrder.nativeOrder())

        // Converting 2D array data into ByteBuffer format
        for (i in inputData.indices) {
            for (j in inputData[i].indices) {
                inputBuffer.putFloat(inputData[i][j])
            }
        }

        val outputBuffer = ByteBuffer.allocateDirect(4 * 37) // Assuming your output tensor remains the same size

        interpreter.run(inputBuffer, outputBuffer)

        val outputData = FloatArray(37)
        outputBuffer.rewind()
        outputBuffer.asFloatBuffer().get(outputData)

        return outputData
    }


    // 关闭 TensorFlow Lite 解释器
    fun close() {
        interpreter.close()
    }
}
