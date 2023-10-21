package com.example.realtime_detection

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.preference.PreferenceManager
import android.util.Log
import android.util.Range
import android.view.Surface
import android.view.TextureView
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.realtime_detection.ml.SsdMobilenetV11Metadata1
import net.majorkernelpanic.streaming.SessionBuilder
import net.majorkernelpanic.streaming.gl.SurfaceView
import net.majorkernelpanic.streaming.rtsp.RtspServer
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {
    lateinit var bitMap: Bitmap;
    lateinit var imageView: ImageView;
    lateinit var textureView : TextureView;
    lateinit var cameraManager: CameraManager;
    lateinit var handler: Handler;
    lateinit var cameraDevice: CameraDevice;
    lateinit var model:SsdMobilenetV11Metadata1;
    lateinit var imageProcessor: ImageProcessor;
    lateinit var labels : List<String>;
    var udp_client : SocketClient = SocketClient();
    val minAccuracy:Double = 0.5;
    val paint = Paint();
    val colors = listOf<Int>(Color.BLUE, Color.GREEN, Color.RED, Color.CYAN, Color.GRAY, Color.BLACK, Color.DKGRAY,
        Color.MAGENTA, Color.YELLOW);

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.imageView);
        textureView = findViewById(R.id.textureView);
        model = SsdMobilenetV11Metadata1.newInstance(this@MainActivity)
        imageProcessor = ImageProcessor.Builder().add(ResizeOp(300, 300, ResizeOp.ResizeMethod.BILINEAR)).build();
        labels = FileUtil.loadLabels(this, "labels.txt");


        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {

            override fun onSurfaceTextureAvailable(p0: SurfaceTexture, p1: Int, p2: Int) {
                //when available we just need to open camera
                openCamera();
            }
            override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) {
            }

            override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean {
                return false;
            }

            override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {
                bitMap = textureView.bitmap!!;
                // Creates inputs for reference.
                var image = TensorImage.fromBitmap(bitMap)
                // resize the image using the image processor because the model expects a 300*300 image
                image = imageProcessor.process(image);
                var buffer = image.buffer;
                Log.d("StreamTag", "Making byte array ${buffer.remaining()}");
                var byteArray = ByteArray(buffer.remaining());
                Log.d("StreamTag", "copying byte array");
                buffer.get(byteArray);

                // Print the first 20 bytes in hexadecimal format
                val first20 = byteArray.sliceArray(0 until 20)
                val hexStringBuilder = StringBuilder("Hex: ")
                for (byte in first20) {
                    val hexValue = String.format("%02X", byte)
                    hexStringBuilder.append("$hexValue ")
                }
                Log.d("StreamTag", hexStringBuilder.toString())

                byteArray = "FRAME_START".toByteArray() + byteArray;
                udp_client.queue.add(byteArray);
                Log.d("StreamTag", "byte array added to queue");

                // predict();
                imageView.setImageBitmap(image.bitmap);

            }
        }

        val handlerThread = HandlerThread("streamThread");
        handlerThread.start();
        handler = Handler(handlerThread.looper);
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager;
        udp_client.run();

    }

    override fun onDestroy() {
        super.onDestroy()
        model.close();
    }

    private fun predict(){
        // Creates inputs for reference.
        var image = TensorImage.fromBitmap(bitMap)
        // resize the image using the image processor because the model expects a 300*300 image
        image = imageProcessor.process(image);

        // Runs model inference and gets result.
        val outputs = model.process(image)
        val locations = outputs.locationsAsTensorBuffer.floatArray
        val classes = outputs.classesAsTensorBuffer.intArray
        val scores = outputs.scoresAsTensorBuffer.floatArray
        val numberOfDetections = outputs.numberOfDetectionsAsTensorBuffer.intArray

        var mutableBitMap = bitMap.copy(Bitmap.Config.ARGB_8888, true);
        var canvas = Canvas(mutableBitMap);

        val h = mutableBitMap.height;
        val w = mutableBitMap.width;

        paint.textSize = h/15f;


        var x = 0;
        scores.forEachIndexed { i, score ->
            // each 4 consecutive numbers in locations array correspond to same classified object (top, left, bottom, right]
            // so we will increment x by 4 times the index each time
            x = i * 4;
            if(score > minAccuracy){
                paint.setColor(colors.get(i))

                paint.style = Paint.Style.STROKE;
                // this the RectF(float left, float top, float right, float bottom) constructor
                // and this the locations array [top, left, bottom, right] also the values are float between 0 and 1
                // so they need to be scaled to the full width and height of the actual bitMap on the screen
                // for every left and right we will scale it by width and for every top and bottom scale by height
                // so we need to reorder and scale the locations array for the Rectf constructor
                val left = locations.get(x+1)*w;
                val top =  locations.get(x)*h;
                val bottom = locations.get(x+3)*w;
                val right =  locations.get(x+2)*h;
                canvas.drawRect(RectF(left, top, right, bottom), paint);

                paint.style = Paint.Style.FILL;
                canvas.drawText(labels.get(classes.get(i)) + " " + score.toString(), left, top, paint);
            }
        }
        imageView.setImageBitmap(mutableBitMap);
    }

    private fun openCamera() {
        if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 101);

        cameraManager.openCamera(cameraManager.cameraIdList[0], object: CameraDevice.StateCallback() {
            override fun onOpened(p0: CameraDevice) {
                //save the cameraDevice in the current activity state (for later) as soon as it gets passed
                cameraDevice = p0;

                var surface = Surface(textureView.surfaceTexture);

                var captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                captureRequest.set(CaptureRequest.JPEG_QUALITY, 10)
                val range = Range(24, 24)
                captureRequest.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, range)
                captureRequest.addTarget(surface);

                cameraDevice.createCaptureSession(listOf(surface), object:CameraCaptureSession.StateCallback(){
                    override fun onConfigured(p0: CameraCaptureSession) {
                        p0.setRepeatingRequest(captureRequest.build(), null, null);
                    }

                    override fun onConfigureFailed(p0: CameraCaptureSession) {
                    }

                }, handler)
            }

            override fun onDisconnected(p0: CameraDevice) {
                TODO("Not yet implemented")
            }

            override fun onError(p0: CameraDevice, p1: Int) {
                TODO("Not yet implemented")
            }

        }, handler)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(grantResults[0] != PackageManager.PERMISSION_GRANTED){
            Toast.makeText(this, "permission not granted", Toast.LENGTH_SHORT).show();
        }
    }


}
