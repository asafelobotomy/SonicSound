package app.sonicsound.youtube

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Official YouTube IFrame Player.
 * Plays with audio (unmuted). [onEnded] fires when the video finishes so the queue can advance.
 */
class YoutubeIframeController(context: Context) {
    val webView: WebView = WebView(context)
    private var ready = false
    private var pendingId: String? = null
    private var pendingStart = 0f
    private var pendingPlay = true
    var onReady: (() -> Unit)? = null
    var onEnded: (() -> Unit)? = null

    init {
        configureWebView()
        webView.loadDataWithBaseURL(
            "https://www.youtube.com",
            PLAYER_HTML,
            "text/html",
            "UTF-8",
            null
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }
        webView.setBackgroundColor(Color.BLACK)
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = WebViewClient()
        webView.isFocusable = false
        webView.isFocusableInTouchMode = false
        webView.isClickable = false
        // Block native YouTube chrome / touch gestures; SonicSound owns playback UI.
        webView.setOnTouchListener { _, _ -> true }
        webView.addJavascriptInterface(Bridge(), "SonicSoundBridge")
        webView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    fun load(videoId: String, startSeconds: Float, playing: Boolean) {
        pendingId = videoId
        pendingStart = startSeconds
        pendingPlay = playing
        if (!ready) return
        eval(
            "loadVideo('${escape(videoId)}', $startSeconds, ${if (playing) "true" else "false"})"
        )
    }

    fun play() = eval("playVideo()")
    fun pause() = eval("pauseVideo()")
    fun seekTo(seconds: Float) = eval("seekTo($seconds)")
    fun mute() = eval("muteVideo()")
    fun unmute() = eval("unmuteVideo()")

    fun destroy() {
        webView.destroy()
    }

    private fun eval(script: String) {
        webView.post { webView.evaluateJavascript(script, null) }
    }

    private fun escape(s: String) = s.replace("'", "\\'")

    inner class Bridge {
        @JavascriptInterface
        fun onReady() {
            ready = true
            val id = pendingId
            if (id != null) {
                val start = pendingStart
                val play = pendingPlay
                webView.post {
                    eval("loadVideo('${escape(id)}', $start, ${if (play) "true" else "false"})")
                }
            }
            webView.post { onReady?.invoke() }
        }

        @JavascriptInterface
        fun onEnded() {
            webView.post { onEnded?.invoke() }
        }
    }

    companion object {
        private const val PLAYER_HTML = """
<!DOCTYPE html>
<html><head><meta name="viewport" content="width=device-width, initial-scale=1"/>
<style>html,body,#player{margin:0;padding:0;width:100%;height:100%;background:#000;overflow:hidden}</style>
</head><body><div id="player"></div>
<script>
var player, pending=null;
function onYouTubeIframeAPIReady(){
  player=new YT.Player('player',{
    width:'100%',height:'100%',
    playerVars:{autoplay:1,controls:0,modestbranding:1,rel:0,playsinline:1,fs:0},
    events:{
      onReady:function(e){
        if(window.SonicSoundBridge) SonicSoundBridge.onReady();
        if(pending){loadVideo(pending.id,pending.start,pending.play);pending=null;}
      },
      onStateChange:function(e){
        if(e.data===0 && window.SonicSoundBridge) SonicSoundBridge.onEnded();
      }
    }
  });
}
function loadVideo(id,start,play){
  if(!player||!player.loadVideoById){pending={id:id,start:start,play:play};return;}
  player.loadVideoById({videoId:id,startSeconds:start||0});
  player.unMute();
  player.setVolume(100);
  if(play){player.playVideo();}else{player.pauseVideo();}
}
function playVideo(){if(player){player.unMute();player.playVideo();}}
function pauseVideo(){if(player)player.pauseVideo();}
function seekTo(s){if(player)player.seekTo(s,true);}
function muteVideo(){if(player)player.mute();}
function unmuteVideo(){if(player){player.unMute();player.setVolume(100);}}
</script>
<script src="https://www.youtube.com/iframe_api"></script>
</body></html>
"""
    }
}
