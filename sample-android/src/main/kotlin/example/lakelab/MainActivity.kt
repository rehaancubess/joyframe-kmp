package example.lakelab

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import example.Playground
import io.github.rehaancubess.joyframe.JoyframeAndroid
import io.github.rehaancubess.joyframe.input.PlatformGamepad

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        JoyframeAndroid.initialize(this)
        setContent { Playground() }
    }
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        PlatformGamepad.handleMotionEvent(event) || super.dispatchGenericMotionEvent(event)
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        PlatformGamepad.handleKeyEvent(event) || super.dispatchKeyEvent(event)
}
