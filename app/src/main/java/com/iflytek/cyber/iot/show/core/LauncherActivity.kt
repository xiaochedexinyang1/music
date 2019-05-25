/*
 * Copyright (C) 2018 iFLYTEK CO.,LTD.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.iflytek.cyber.iot.show.core

import android.Manifest
import android.content.*
import android.graphics.Bitmap
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.*
import android.support.v4.app.ActivityCompat
import android.support.v4.content.PermissionChecker
import android.support.v4.view.animation.FastOutSlowInInterpolator
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.animation.*
import android.widget.ImageView
import android.widget.TextView
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import cn.iflyos.iace.core.PlatformInterface
import cn.iflyos.iace.iflyos.AuthProvider
import cn.iflyos.iace.iflyos.IflyosClient
import com.iflytek.cyber.iot.show.core.impl.AuthProvider.AuthProviderHandler
import com.iflytek.cyber.iot.show.core.impl.Logger.LogEntry
import com.iflytek.cyber.iot.show.core.impl.Logger.LoggerHandler
import com.iflytek.cyber.iot.show.core.impl.SpeechRecognizer.SpeechRecognizerHandler
import com.iflytek.cyber.iot.show.core.retrofit.SSLSocketFactoryCompat
import com.iflytek.cyber.iot.show.core.utils.ConnectivityUtils
import com.iflytek.cyber.iot.show.core.widget.RecognizeWaveView
import jp.wasabeef.blurry.Blurry
import kotlinx.android.synthetic.main.activity_launcher.*
import java.lang.ref.SoftReference
import java.lang.ref.WeakReference
import java.security.cert.X509Certificate
import java.util.*
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.X509TrustManager
import kotlin.collections.HashSet

class LauncherActivity : AppCompatActivity(), Observer {
    private var ivBlur: ImageView? = null
    private var tvTipsTitle: TextView? = null
    private var tvTipsSimple: TextView? = null
    private val tipsSet = java.util.HashSet<TextView?>()
    private var tvIat: TextView? = null
    private var ivIatLogo: ImageView? = null
    private var ivLogo: ImageView? = null
    private var recognizeWaveView: RecognizeWaveView? = null

    private var latestIat = System.currentTimeMillis()
    private var currentAudioState = SpeechRecognizerHandler.AudioCueState.END

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val counterHandler = CounterHandler(this)

    var mEngineService: EngineService? = null

    private val observerSet = HashSet<Observer>()

    private val longPressHandler = LongPressHandler(this)
    private var isNetworkAvailable: Boolean = false
        get() {
            return if (Build.VERSION.SDK_INT < 21) {
                ConnectivityUtils.isNetworkAvailable(this)
            } else {
                field
            }
        }

    private var mStatusType = StatusType.NORMAL
    private var mStatusHidden = false
    private val networkErrorClickListener = View.OnClickListener {
        val navController = findNavController(R.id.fragment)
        val currentDestinationId = navController.currentDestination?.id
        if (mStatusHidden || (currentDestinationId != R.id.main_fragment &&
                        currentDestinationId != R.id.player_fragment)) {
            return@OnClickListener
        }
        val arguments = Bundle()
        arguments.putBoolean("RESET_WIFI", true)
        navController.navigate(R.id.action_main_to_wifi_fragment, arguments)
    }
    private val authorizeErrorClickListener = View.OnClickListener {
        val navController = findNavController(R.id.fragment)
        val currentDestinationId = navController.currentDestination?.id
        if (mStatusHidden || (currentDestinationId != R.id.main_fragment &&
                        currentDestinationId != R.id.player_fragment)) {
            return@OnClickListener
        }
        val authProvider = mEngineService?.getHandler("AuthProvider")
        if (authProvider is AuthProviderHandler)
            authProvider.onAuthStateChanged(AuthProvider.AuthState.UNINITIALIZED,
                    AuthProvider.AuthError.NO_ERROR)
        val arguments = Bundle()
        arguments.putBoolean("shouldShowTips", true)
        navController.navigate(R.id.action_main_to_pair_fragment, arguments)
    }

    private val tapToTalkClickListener = View.OnClickListener {
        if (mStatusHidden) {
            return@OnClickListener
        }
        val intent = Intent(this, EngineService::class.java)
        intent.action = EngineService.ACTION_TAP_TO_TALK
        startService(intent)
        handleVoiceStart()
    }

    companion object {
        const val EXTRA_TEMPLATE = "template"

        private const val IAT_OFFSET = 2000L
        private const val sTag = "LauncherActivity"

        private const val REQUEST_PERMISSION_CODE = 1001
    }

    enum class StatusType {
        AUTHORIZE_ERROR,
        NETWORK_ERROR,
        NORMAL,
        RETRYING,
        SERVER_ERROR,
        TIMEOUT_ERROR,
        UNKNOWN_ERROR,
        UNSUPPORTED_DEVICE_ERROR,
    }

    fun addObserver(observer: Observer) {
        observerSet.add(observer)
    }

    fun deleteObserver(observer: Observer) {
        observerSet.remove(observer)
    }

    override fun update(o: Observable?, arg: Any?) {
        try {
            val fragment = fragment
            observerSet.map {
                it.update(o, arg)
            }
            if (o is LoggerHandler.LoggerObservable) {
                if (arg is LogEntry) {
                    val json = arg.json
                    val template = json.getJSONObject("template")
                    when (arg.type) {
                        LoggerHandler.AUTH_LOG -> {
                            val type = template.optInt("type")
                            when (type) {
                                LoggerHandler.AUTH_LOG_STATE -> {
                                    try {
                                        val authState = template.optString("auth_state")
                                        val authError = template.optString("auth_error")
                                        if (authState == AuthProvider.AuthState.REFRESHED.toString()) {
                                            mStatusType = StatusType.NORMAL
                                            val navController = findNavController(R.id.fragment)
                                            when (navController.currentDestination?.id) {
                                                R.id.wifi_fragment, R.id.about_fragment -> {

                                                }
                                                else -> {
                                                    runOnUiThread {
                                                        showSimpleTips()
                                                    }
                                                }
                                            }
                                            val controller = NavHostFragment.findNavController(fragment)
                                            if (controller.currentDestination?.id == R.id.splash_fragment)
                                                NavHostFragment.findNavController(fragment).navigate(R.id.action_to_main_fragment)
                                        } else if (authState == AuthProvider.AuthState.UNINITIALIZED.toString()) {
                                            if (mEngineService?.getLocalAccessToken().isNullOrEmpty()) {
                                                val controller = NavHostFragment.findNavController(fragment)
                                                if (controller.currentDestination?.id == R.id.splash_fragment)
                                                    NavHostFragment.findNavController(fragment).navigate(R.id.action_to_welcome_fragment)
                                            } else {
                                                val navController = findNavController(R.id.fragment)
                                                if (navController.currentDestination?.id == R.id.splash_fragment) {
                                                    navController.navigate(R.id.action_to_main_fragment)
                                                }
                                                val error = AuthProvider.AuthError.valueOf(authError)
                                                when (error) {
                                                    AuthProvider.AuthError.AUTHORIZATION_EXPIRED -> {
                                                        mStatusType = StatusType.AUTHORIZE_ERROR
                                                    }
                                                    AuthProvider.AuthError.AUTHORIZATION_FAILED -> {
                                                        mStatusType = if (isNetworkAvailable)
                                                            StatusType.AUTHORIZE_ERROR
                                                        else
                                                            StatusType.NETWORK_ERROR
                                                    }
                                                    AuthProvider.AuthError.SERVER_ERROR,
                                                    AuthProvider.AuthError.INTERNAL_ERROR -> {
                                                        mStatusType = StatusType.SERVER_ERROR
                                                    }
                                                    AuthProvider.AuthError.UNAUTHORIZED_CLIENT -> {
                                                        mStatusType = StatusType.UNSUPPORTED_DEVICE_ERROR
                                                    }
                                                    AuthProvider.AuthError.UNKNOWN_ERROR -> {
                                                        mStatusType = StatusType.UNKNOWN_ERROR
                                                    }
                                                    AuthProvider.AuthError.NO_ERROR -> {
                                                        // ignore
                                                    }
                                                    else -> {
                                                        mStatusType = StatusType.UNKNOWN_ERROR
                                                    }
                                                }
                                                runOnUiThread {
                                                    updateBottomBar()
                                                }
                                            }
                                        } else {

                                        }
                                    } catch (e: Exception) {

                                    }
                                }
                            }
                        }
                        LoggerHandler.RECORD_VOLUME -> {
                            val volume = template.optDouble("volume")
                            runOnUiThread {
                                recognizeWaveView?.updateVolume(volume)
                            }
                        }
                        LoggerHandler.IAT_LOG -> {
                            val text = template.optString("text")
                            latestIat = System.currentTimeMillis()
                            runOnUiThread { tvIat?.text = text }
                            tvIat?.postDelayed(iatRunnable, IAT_OFFSET)
                        }
                        LoggerHandler.CONNECTION_STATE -> {
                            val status = template.optString("status")
                            val reason = template.optString("reason")
                            when (status) {
                                IflyosClient.ConnectionStatus.CONNECTED.toString() -> {
                                    runOnUiThread {
                                        mStatusType = StatusType.NORMAL
                                        updateBottomBar()
                                    }
                                }
                                IflyosClient.ConnectionStatus.DISCONNECTED.toString() -> {
                                    if (reason != IflyosClient.ConnectionChangedReason.ACL_CLIENT_REQUEST.toString()) {
                                        val navController = findNavController(R.id.fragment)
                                        if (navController.currentDestination?.id != R.id.main_fragment) {
                                            val id = navController.currentDestination?.id
                                            when (id) {
                                                R.id.body_template_fragment,
                                                R.id.body_template_3_fragment, R.id.weather_fragment,
                                                R.id.list_fragment, R.id.about_fragment -> {
                                                    navController.navigateUp()
                                                }
                                            }
                                        }
                                        when (navController.currentDestination?.id) {
                                            R.id.wifi_fragment, R.id.about_fragment -> {

                                            }
                                            else -> {
                                                runOnUiThread {
                                                    mStatusType = StatusType.NETWORK_ERROR
                                                    updateBottomBar()
                                                }
                                            }
                                        }
                                    }
                                }
                                IflyosClient.ConnectionStatus.PENDING.toString() -> {
                                    if (reason == IflyosClient.ConnectionChangedReason.SERVER_SIDE_DISCONNECT.toString()) {
                                        val navController = findNavController(R.id.fragment)
                                        if (navController.currentDestination?.id != R.id.main_fragment) {
                                            val id = navController.currentDestination?.id
                                            when (id) {
                                                R.id.body_template_fragment,
                                                R.id.body_template_3_fragment, R.id.weather_fragment,
                                                R.id.list_fragment, R.id.about_fragment -> {
                                                    navController.navigateUp()
                                                }
                                            }
                                        }
                                        when (navController.currentDestination?.id) {
                                            R.id.wifi_fragment, R.id.about_fragment -> {

                                            }
                                            else -> {
                                                runOnUiThread {
                                                    mStatusType = StatusType.NETWORK_ERROR
                                                    updateBottomBar()
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        LoggerHandler.DIALOG_STATE -> {
                            val state = template.optString("state")
                            when (state) {
                                IflyosClient.DialogState.LISTENING.toString() -> {
                                    mEngineService?.stopAlert()
                                    try {
                                        val nav = findNavController(R.id.fragment)
                                        when (nav.currentDestination?.id) {
                                            R.id.weather_fragment, R.id.body_template_fragment,
                                            R.id.body_template_3_fragment, R.id.list_fragment,
                                            R.id.about_fragment, R.id.wifi_fragment -> {
                                                nav.navigateUp()
                                            }
                                        }
                                    } catch (e: IllegalStateException) {
                                        e.printStackTrace()
                                    }
                                    handleVoiceStart()
                                }
                                IflyosClient.DialogState.IDLE.toString() -> {
                                    handleVoiceEnd()
                                }
                            }
                        }
                        LoggerHandler.WEATHER_TEMPLATE -> {
                            showTemplate(R.id.weather_fragment, template.toString(), true)
                        }
                        LoggerHandler.BODY_TEMPLATE1, LoggerHandler.BODY_TEMPLATE2 -> {
                            showTemplate(R.id.body_template_fragment, template.toString())
                        }
                        LoggerHandler.BODY_TEMPLATE3 -> {
                            showTemplate(R.id.body_template_3_fragment, template.toString(), true)
                        }
                        LoggerHandler.LIST_TEMPLATE1 -> {
                            showTemplate(R.id.list_fragment, template.toString())
                        }
                        LoggerHandler.EXCEPTION_LOG -> {
                            val code = template.getString("code")
                            when (code) {
                                "UNAUTHORIZED_EXCEPTION" -> {
                                    // Token 过期或已取消绑定
                                    mStatusType = StatusType.AUTHORIZE_ERROR
                                    runOnUiThread {
                                        updateBottomBar()
                                    }
                                }
                            }
                        }
                    }
                } else
                    Log.d(sTag, "Logger null")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateBottomBar() {
        when (mStatusType) {
            StatusType.NORMAL -> {
                tvTipsSimple?.setText(R.string.tips_sentence_simple)
                tvTipsSimple?.setOnClickListener(tapToTalkClickListener)
                ivLogo?.setOnClickListener(tapToTalkClickListener)
                ivLogo?.setImageResource(R.drawable.ic_voice_bar_regular_white_32dp)
            }
            StatusType.AUTHORIZE_ERROR -> {
                tvTipsSimple?.setText(R.string.authorize_error)
                tvTipsSimple?.setOnClickListener(authorizeErrorClickListener)
                ivLogo?.setOnClickListener(authorizeErrorClickListener)
                ivLogo?.setImageResource(R.drawable.ic_voice_bar_exception_white_32dp)
            }
            StatusType.SERVER_ERROR -> {
                tvTipsSimple?.setText(R.string.unknown_error)
                tvTipsSimple?.setOnClickListener(null)
                ivLogo?.setImageResource(R.drawable.ic_voice_bar_exception_white_32dp)
                ivLogo?.setOnClickListener(null)
            }
            StatusType.NETWORK_ERROR -> {
                tvTipsSimple?.setText(R.string.network_error)
                tvTipsSimple?.setOnClickListener(networkErrorClickListener)
                ivLogo?.setOnClickListener(networkErrorClickListener)
                ivLogo?.setImageResource(R.drawable.ic_voice_bar_exception_white_32dp)
            }
            StatusType.UNKNOWN_ERROR -> {
                tvTipsSimple?.setText(R.string.unknown_error)
                tvTipsSimple?.setOnClickListener(null)
                ivLogo?.setImageResource(R.drawable.ic_voice_bar_exception_white_32dp)
                ivLogo?.setOnClickListener(null)
            }
            StatusType.RETRYING -> {
                tvTipsSimple?.setText(R.string.retry_connecting)
                tvTipsSimple?.setOnClickListener(null)
                ivLogo?.setImageResource(R.drawable.ic_voice_bar_exception_white_32dp)
                ivLogo?.setOnClickListener(null)
            }
            StatusType.UNSUPPORTED_DEVICE_ERROR -> {
                tvTipsSimple?.setText(R.string.unsupported_device_error)
                tvTipsSimple?.setOnClickListener(null)
                ivLogo?.setImageResource(R.drawable.ic_voice_bar_exception_white_32dp)
                ivLogo?.setOnClickListener(null)
            }
            else -> {

            }
        }
        (findViewById<View>(R.id.error_next)?.parent as View).run {
            post {
                requestLayout()
            }
        }
        if (!mStatusHidden)
            if (mStatusType == StatusType.NORMAL) {
                hideErrorBar()
            } else {
                Log.w(sTag, "show Error bar: $mStatusType")
                showErrorBar()
            }
    }

    private fun showTemplate(id: Int, template: String) {
        showTemplate(id, template, false)
    }

    private fun showTemplate(id: Int, template: String, withCount: Boolean) {
        val nav = findNavController(R.id.fragment)
        when (nav.currentDestination?.id) {
            R.id.main_fragment, R.id.player_fragment -> {
                templateToFragment(nav, id, template, withCount)
            }
            else -> {
                findViewById<View>(R.id.fragment).postDelayed({
                    templateToFragment(nav, id, template, withCount)
                }, 300)
            }
        }
    }

    private fun templateToFragment(nav: NavController, id: Int, template: String, withCount: Boolean) {
        nav.popBackStack(R.id.body_template_3_fragment, true)
        nav.popBackStack(R.id.body_template_fragment, true)
        nav.popBackStack(R.id.list_fragment, true)
        nav.popBackStack(R.id.weather_fragment, true)

        val bundle = Bundle()
        bundle.putString(EXTRA_TEMPLATE, template)

        val navOptions = NavOptions.Builder()
                .setEnterAnim(R.anim.slide_in_top)
                .setExitAnim(android.R.anim.fade_out)
                .setPopExitAnim(R.anim.slide_page_pop_exit)
                .setPopEnterAnim(android.R.anim.fade_in)
                .build()
        nav.navigate(id, bundle, navOptions)

        if (withCount) {
            counterHandler.removeCallbacksAndMessages(null)
            val message = Message.obtain()
            message.what = CounterHandler.START_COUNT
            message.arg1 = id
            counterHandler.sendMessage(message)
        }
    }

    private val iatRunnable = Runnable {
        if (currentAudioState == SpeechRecognizerHandler.AudioCueState.END)
            if (System.currentTimeMillis() >= latestIat + IAT_OFFSET) {
                handleVoiceEnd()
            }
    }

    private fun handleVoiceStart() {
        runOnUiThread {
            hideSimpleTips()
            showIatPage()
        }
    }

    private fun hideErrorBar() {
        error_bar.animate().cancel()
        error_bar.animate()
                .alpha(0f)
                .setDuration(200)
                .setInterpolator(FastOutSlowInInterpolator())
                .start()
        error_divider.animate().cancel()
        error_divider.animate()
                .alpha(0f)
                .setDuration(200)
                .setInterpolator(FastOutSlowInInterpolator())
                .start()
        error_next.animate().cancel()
        error_next.animate()
                .alpha(0f)
                .setDuration(200)
                .setInterpolator(FastOutSlowInInterpolator())
                .start()
    }

    private fun showErrorBar() {
        error_bar.animate().cancel()
        error_bar.animate()
                .alpha(1f)
                .setDuration(250)
                .setInterpolator(FastOutSlowInInterpolator())
                .start()
        error_divider.animate().cancel()
        error_divider.animate()
                .alpha(1f)
                .setDuration(250)
                .setInterpolator(FastOutSlowInInterpolator())
                .start()
        error_next.animate().cancel()
        error_next.animate()
                .alpha(1f)
                .setDuration(200)
                .setInterpolator(FastOutSlowInInterpolator())
                .start()
    }

    fun hideSimpleTips() {
        mStatusHidden = true
        hideErrorBar()
        ivLogo?.run {
            val scaleAnimation = ScaleAnimation(1f, 0f, 1f, 0f, (width / 2).toFloat(), (height / 2).toFloat())
            scaleAnimation.duration = 150
            scaleAnimation.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationRepeat(animation: Animation?) {

                }

                override fun onAnimationEnd(animation: Animation?) {
                    scaleX = 0f
                    scaleY = 0f
                }

                override fun onAnimationStart(animation: Animation?) {

                }

            })
            startAnimation(scaleAnimation)
        }
        ivLogo?.isClickable = false

        tvTipsSimple?.run {
            animate().alpha(0f).setDuration(150).start()
        }
    }

    private fun showIatPage() {
        recognizeWaveView?.startEnterAnimation()
        ivBlur?.run {
            animate().alpha(1f).setDuration(200).start()
        }
        val animationSet = AnimationSet(true)
        val translation = TranslateAnimation(((ivBlur?.width
                ?: 0) / 8).toFloat(), 0f, 0f, 0f)
        animationSet.addAnimation(translation)
        val alphaAnimation = AlphaAnimation(0f, 1f)
        animationSet.addAnimation(alphaAnimation)
        animationSet.interpolator = FastOutSlowInInterpolator()
        animationSet.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationRepeat(animation: Animation?) {
            }

            override fun onAnimationStart(animation: Animation?) {
                tvTipsTitle?.alpha = 1f
                tipsSet.map {
                    it?.alpha = 1f
                }
            }

            override fun onAnimationEnd(animation: Animation?) {
                tvTipsTitle?.alpha = 1f
                tipsSet.map {
                    it?.alpha = 1f
                }
            }

        })
        animationSet.startOffset = 100
        animationSet.duration = 300

        tvTipsTitle?.startAnimation(animationSet)
        tipsSet.map {
            it?.startAnimation(animationSet)
        }
        ivIatLogo?.isClickable = true
        ivIatLogo?.run {
            val scaleAnimation = ScaleAnimation(0f, 1f, 0f, 1f, (width / 2).toFloat(), (height / 2).toFloat())
            scaleAnimation.duration = 200
            scaleAnimation.startOffset = 150
            scaleAnimation.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationRepeat(animation: Animation?) {

                }

                override fun onAnimationEnd(animation: Animation?) {
                    scaleX = 1f
                    scaleY = 1f
                }

                override fun onAnimationStart(animation: Animation?) {

                }

            })
            startAnimation(scaleAnimation)
        }
        tvIat?.run {
            text = ""
            animate().alpha(1f).setStartDelay(150).setDuration(200).start()
        }
    }

    fun showSimpleTips() {
        mStatusHidden = false
        updateBottomBar()
        ivLogo?.run {
            if (scaleX == 0f && scaleY == 0f) {
                val scaleAnimation = ScaleAnimation(0f, 1f, 0f, 1f, (width / 2).toFloat(), (height / 2).toFloat())
                scaleAnimation.duration = 200
                scaleAnimation.startOffset = 150
                scaleAnimation.setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationStart(animation: Animation?) {
                        scaleX = 1f
                        scaleY = 1f
                    }

                    override fun onAnimationRepeat(animation: Animation?) {

                    }

                    override fun onAnimationEnd(animation: Animation?) {
                        scaleX = 1f
                        scaleY = 1f
                    }


                })
                startAnimation(scaleAnimation)
            }
        }
        ivLogo?.isClickable = true

        tvTipsSimple?.run {
            animate().alpha(1f).setStartDelay(150).setDuration(200).start()
        }
    }

    private fun hideIatPage() {
        recognizeWaveView?.startQuitAnimation()
        ivBlur?.run {
            animate().alpha(0f).setStartDelay(100).setDuration(400).start()
        }
        val animationSet = AnimationSet(true)
        val translation = TranslateAnimation(0f, -((ivBlur?.width
                ?: 0) / 8).toFloat(), 0f, 0f)
        animationSet.addAnimation(translation)
        val alphaAnimation = AlphaAnimation(1f, 0f)
        animationSet.addAnimation(alphaAnimation)
        animationSet.duration = 400
        animationSet.interpolator = FastOutSlowInInterpolator()
        animationSet.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationRepeat(animation: Animation?) {
            }

            override fun onAnimationStart(animation: Animation?) {
            }

            override fun onAnimationEnd(animation: Animation?) {
                tvTipsTitle?.alpha = 0f
                tipsSet.map {
                    it?.alpha = 0f
                }
            }

        })

        tvTipsTitle?.startAnimation(animationSet)
        tipsSet.map {
            it?.startAnimation(animationSet)
        }

        ivIatLogo?.isClickable = false
        ivIatLogo?.run {
            val scaleAnimation = ScaleAnimation(1f, 0f, 1f, 0f, (width / 2).toFloat(), (height / 2).toFloat())
            scaleAnimation.duration = 150
            scaleAnimation.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationRepeat(animation: Animation?) {

                }

                override fun onAnimationEnd(animation: Animation?) {
                    scaleX = 0f
                    scaleY = 0f
                }

                override fun onAnimationStart(animation: Animation?) {

                }

            })
            startAnimation(scaleAnimation)
        }

        tvIat?.run {
            animate().alpha(0f).setDuration(150).start()
        }
    }

    private fun handleVoiceEnd() {
        val navController = findNavController(R.id.fragment)
        when (navController.currentDestination?.id) {
            R.id.wifi_fragment, R.id.about_fragment -> {

            }
            else -> {
                runOnUiThread {
                    hideIatPage()
                    showSimpleTips()
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        setImmersiveFlags()
    }

    private fun setImmersiveFlags() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launcher)

        // check SSL
        if (Build.VERSION.SDK_INT < 21)
            try {
                val trustAllCert =
                        object : X509TrustManager {
                            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                            }

                            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                            }

                            override fun getAcceptedIssuers(): Array<X509Certificate> {
                                return emptyArray()
                            }

                        }
                val sslSocketFactory = SSLSocketFactoryCompat(trustAllCert)
                HttpsURLConnection.setDefaultSSLSocketFactory(sslSocketFactory)
            } catch (e: Exception) {
                e.printStackTrace()
            }


        setupView()

        setImmersiveFlags()

        hideIatPage()

        bindService(Intent(this, EngineService::class.java), connection, Context.BIND_AUTO_CREATE)

        isNetworkAvailable = ConnectivityUtils.isNetworkAvailable(this)

        // api 21 以上该广播过时
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT < 21) {
            val intentFilter = IntentFilter()
            intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION)
            registerReceiver(connectStateReceiver, intentFilter)
        } else {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE)
            if (connectivityManager is ConnectivityManager) {
                networkCallback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network?) {
                        super.onAvailable(network)
                        isNetworkAvailable = true
                        if (mEngineService?.connectionStatus() == IflyosClient.ConnectionStatus.DISCONNECTED) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                mEngineService?.reconnectIVS()
                            } else {
                                val service = Intent(this@LauncherActivity, EngineService::class.java)
                                service.action = EngineService.ACTION_RECONNECT
                                startService(service)
                            }

                            mStatusType = StatusType.RETRYING
                            runOnUiThread {
                                updateBottomBar()
                            }
                        }
                    }

                    override fun onLost(network: Network?) {
                        super.onLost(network)
                        isNetworkAvailable = false
                        val networkAvailable = ConnectivityUtils.isNetworkAvailable(this@LauncherActivity)
                        mStatusType = if (networkAvailable) {
                            StatusType.NORMAL
                        } else {
                            StatusType.NETWORK_ERROR
                        }
                        runOnUiThread {
                            updateBottomBar()
                        }
                    }
                }
                val request = NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
                connectivityManager.registerNetworkCallback(request, networkCallback)
            }
        }
    }

    private fun setupView() {
        ivBlur = findViewById(R.id.blur_recognize_background)
        tvTipsTitle = findViewById(R.id.recognize_tips_title)
        tvTipsSimple = findViewById(R.id.tips_simple)
        tvIat = findViewById(R.id.iat_text)
        ivIatLogo = findViewById(R.id.iat_logo)
        ivLogo = findViewById(R.id.logo)
        recognizeWaveView = findViewById(R.id.recognize_view)
        tipsSet.add(findViewById(R.id.recognize_tips_1))
        tipsSet.add(findViewById(R.id.recognize_tips_2))
        tipsSet.add(findViewById(R.id.recognize_tips_3))

        ivLogo?.setOnClickListener {
            val intent = Intent(this, EngineService::class.java)
            intent.action = EngineService.ACTION_TAP_TO_TALK
            startService(intent)
        }
        ivIatLogo?.setOnClickListener {
            val intent = Intent(this, EngineService::class.java)
            intent.action = EngineService.ACTION_STOP_CAPTURE
            startService(intent)
        }

        ivBlur?.post {
            val height = fragment_container.height

            tvTipsTitle?.setTextSize(TypedValue.COMPLEX_UNIT_PX, height * 0.047f)
            ivLogo?.setImageResource(R.drawable.ic_voice_bar_regular_white_32dp)
            tvTipsSimple?.setText(R.string.tips_sentence_simple)
            tvTipsSimple?.setTextSize(TypedValue.COMPLEX_UNIT_PX, height * 0.047f)
            tipsSet.map {
                it?.setTextSize(TypedValue.COMPLEX_UNIT_PX, height * 0.067f)
            }

            tvIat?.setTextSize(TypedValue.COMPLEX_UNIT_PX, height * 0.047f)
        }
    }

    override fun onResume() {
        super.onResume()
        if (PermissionChecker.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PermissionChecker.PERMISSION_GRANTED || PermissionChecker.checkSelfPermission(
                        this, Manifest.permission.READ_PHONE_STATE)
                != PermissionChecker.PERMISSION_GRANTED) {
            requestPermission()
        }
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.READ_PHONE_STATE),
                REQUEST_PERMISSION_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSION_CODE) {
            for (i in 0 until permissions.size) {
                if (permissions[i] == Manifest.permission.RECORD_AUDIO) {
                    if (grantResults[i] == PermissionChecker.PERMISSION_GRANTED) {
                        mEngineService?.recreate()
                        mEngineService?.addObserver(this@LauncherActivity)
                        if (mEngineService?.getAuthState() == AuthProvider.AuthState.UNINITIALIZED
                                && mEngineService?.getLocalAccessToken().isNullOrEmpty()) {
                            if (NavHostFragment.findNavController(fragment).currentDestination?.id == R.id.splash_fragment) {
                                NavHostFragment.findNavController(fragment).navigate(R.id.action_to_welcome_fragment)
                            }
                        }
                    } else {
                        AlertDialog.Builder(this)
                                .setTitle(R.string.dialog_title_permission)
                                .setMessage(getString(R.string.dialog_permission_failed,
                                        getString(R.string.dialog_permission_failed)))
                                .setPositiveButton(R.string.retry) { _, _ ->
                                    requestPermission()
                                }
                                .setNegativeButton(R.string.close) { _, _ ->
                                    finish() //TODO: As a Launcher, app wouldn't finish at fact, remove this in future
                                }
                                .show()
                    }
                }
            }
        }
    }

    override fun onBackPressed() {
        val controller = NavHostFragment.findNavController(fragment)
        when (controller.currentDestination?.id) {
            R.id.main_fragment, R.id.welcome_fragment -> {
                finish()
            }
            else -> {
                super.onBackPressed()
            }
        }
    }

    fun getHandler(namespace: String): PlatformInterface? {
        return mEngineService?.getHandler(namespace)
    }

    override fun onDestroy() {
        super.onDestroy()

        unbindService(connection)
        stopService(Intent(this, EngineService::class.java))

        if (Build.VERSION.SDK_INT < 21)
            unregisterReceiver(connectStateReceiver)
        else {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE)
            if (connectivityManager is ConnectivityManager) {
                connectivityManager.unregisterNetworkCallback(networkCallback)
            }
        }
    }

    fun updateBlurImage(bitmap: Bitmap?) {
        bitmap?.let {
            Blurry.with(this).radius(75)
                    .color(Color.parseColor("#80000000"))
                    .from(bitmap).into(ivBlur)
        }
    }

    private val connectStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            @Suppress("DEPRECATION")
            when (intent?.action) {
                ConnectivityManager.CONNECTIVITY_ACTION -> {
                    // api 21 以上应使用 networkCallback
                    if (mEngineService?.connectionStatus() == IflyosClient.ConnectionStatus.DISCONNECTED) {
                        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE)
                        if (connectivityManager is ConnectivityManager) {
                            val networkInfo = connectivityManager.activeNetworkInfo
                            if (networkInfo?.isConnected == true) {
                                val service = Intent(this@LauncherActivity, EngineService::class.java)
                                service.action = EngineService.ACTION_RECONNECT
                                startService(service)

                                mStatusType = StatusType.RETRYING
                                updateBottomBar()
                            } else {
                                mStatusType = StatusType.NETWORK_ERROR
                                updateBottomBar()
                            }
                        }
                    }
                }
            }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName?) {
            mEngineService?.deleteObserver(this@LauncherActivity)
            mEngineService = null
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service is EngineService.EngineBinder) {
                mEngineService = service.getService()
                mEngineService?.recreate()
                mEngineService?.addObserver(this@LauncherActivity)
                val authState = mEngineService?.getAuthState()
                Log.d(sTag, authState.toString())
                if (authState == AuthProvider.AuthState.UNINITIALIZED
                        && mEngineService?.getLocalAccessToken().isNullOrEmpty()) {
                    NavHostFragment.findNavController(fragment).navigate(R.id.action_to_welcome_fragment)
                } else if (authState == AuthProvider.AuthState.REFRESHED) {
                    NavHostFragment.findNavController(fragment).navigate(R.id.action_to_main_fragment)
                }
            }
        }

    }

    private fun dismissTemplate(fragmentId: Int) {
        try {
            val nav = findNavController(R.id.fragment)
            if (nav.currentDestination?.id == fragmentId) {
                nav.navigateUp()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    class CounterHandler constructor(activity: LauncherActivity) : Handler() {

        private var count = 0

        private var stopped = false

        private val reference: WeakReference<LauncherActivity>?

        init {
            reference = WeakReference(activity)
        }

        override fun handleMessage(msg: Message) {
            if (stopped && count != 0) {
                stopped = false
                count = 0
                return
            }
            if (reference != null) {
                when (msg.what) {
                    START_COUNT -> {
                        stopped = false
                        count = 0
                        val continueMsg = Message.obtain()
                        continueMsg.what = CONTINUE_COUNT
                        continueMsg.arg1 = msg.arg1
                        sendMessageDelayed(continueMsg, 1000)
                    }
                    CONTINUE_COUNT -> {
                        val activity = reference.get()
                        if (activity != null) {
                            if (count >= MAX_COUNT_SECONDS) {
                                count = 0
                                val id = msg.arg1
                                reference.get()?.dismissTemplate(id)
                            } else {
                                count++
                                val newMsg = Message.obtain()
                                newMsg.what = msg.what
                                newMsg.arg1 = msg.arg1
                                sendMessageDelayed(newMsg, 1000)
                            }
                        }
                    }
                }
            }
        }

        internal fun stop() {
            stopped = true
            removeCallbacksAndMessages(null)
        }

        companion object {
            internal const val START_COUNT = 0x1
            internal const val CONTINUE_COUNT = 0x2
            internal const val MAX_COUNT_SECONDS = 20
            internal const val SHOW_COUNT_SECONDS = 10
        }
    }

    private fun offsetVolume(volume: Byte) {
        mEngineService?.offsetVolume(volume)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            longPressHandler.sendEmptyMessage(LongPressHandler.VOLUME_DOWN)
            return true
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            longPressHandler.sendEmptyMessage(LongPressHandler.VOLUME_UP)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                longPressHandler.longPressEnable = false
                longPressHandler.sendEmptyMessage(LongPressHandler.VOLUME_REPORT)
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                longPressHandler.longPressEnable = true
                longPressHandler.sendEmptyMessage(LongPressHandler.VOLUME_DOWN)
                return true
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                longPressHandler.longPressEnable = true
                longPressHandler.sendEmptyMessage(LongPressHandler.VOLUME_UP)
                return true
            }
        }
        return super.onKeyLongPress(keyCode, event)
    }

    private class LongPressHandler(activity: LauncherActivity) : Handler() {
        val softReference = SoftReference<LauncherActivity>(activity)
        var longPressEnable = false

        companion object {
            const val VOLUME_UP = 1
            const val VOLUME_DOWN = 0
            const val VOLUME_REPORT = 2

            private const val VOLUME_OFFSET_UP: Byte = 10
            private const val VOLUME_OFFSET_DOWN: Byte = -10
        }

        override fun handleMessage(msg: Message?) {
            val activity = softReference.get()
            activity?.let {
                when (msg?.what) {
                    VOLUME_DOWN -> {
                        activity.offsetVolume(VOLUME_OFFSET_DOWN)
                        if (longPressEnable) {
                            sendEmptyMessageDelayed(VOLUME_DOWN, 200)
                        } else {

                        }
                    }
                    VOLUME_UP -> {
                        activity.offsetVolume(VOLUME_OFFSET_UP)
                        if (longPressEnable) {
                            sendEmptyMessageDelayed(VOLUME_UP, 200)
                        } else {

                        }
                    }
                    VOLUME_REPORT -> {

                    }
                    else -> {
                        // ignore
                    }
                }
            }
        }
    }

}