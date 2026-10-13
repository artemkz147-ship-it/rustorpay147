package ru.anime.bridge

import android.content.Intent
import android.app.Activity
import android.os.Handler
import android.os.Looper
import org.apache.cordova.*
import org.json.JSONArray
import org.json.JSONObject
import ru.rustore.sdk.pay.RuStorePayClient
import ru.rustore.sdk.pay.model.SdkTheme

class AnimeRuStore : CordovaPlugin() {
    private var listener: CallbackContext? = null
    private var price: String? = null
    private var busy = false
    private var message: String? = null
    private var payments: PaymentController? = null
    private var review: ReviewController? = null
    private var downloadCallback: CallbackContext? = null
    private var downloadSource: String? = null
    private val prefs get() = cordova.activity.getSharedPreferences("anime-rustore-ledger",0)
    private fun legacy() = LegacyMigration.decode(cordova.activity.getSharedPreferences("moon_save",0).getString("state",null))
    private fun migrate() {
        if(prefs.getBoolean("native-imported",false))return
        val old=legacy()
        val ids=prefs.getStringSet("paid",emptySet())!!.toMutableSet().apply { old?.purchases?.let { addAll(it) } }
        check(prefs.edit().putStringSet("paid",ids).putBoolean("review",prefs.getBoolean("review",false)||(old?.review==true)).putBoolean("native-imported",true).commit())
    }
    private fun publish() {
        val data=JSONObject().put("paid",prefs.getStringSet("paid",emptySet())!!.size).put("busy",busy).put("price",price ?: JSONObject.NULL)
        legacy()?.let { old -> data.put("legacyState",JSONObject().put("coins",old.coins).put("scene",old.scene).put("unlocked",old.unlocked).put("completed",old.completed).put("upgrades",old.upgrades).put("review",old.review).put("sound",old.sound)) }
        data.put("review",prefs.getBoolean("review",false))
        message?.let { data.put("message",it) }; message=null
        listener?.sendPluginResult(PluginResult(PluginResult.Status.OK,data).apply { keepCallback=true })
    }
    private fun initControllers() {
        if(payments!=null)return
        migrate()
        payments=PaymentController(
            grant={ id ->
                val ids=prefs.getStringSet("paid",emptySet())!!.toMutableSet()
                val saved=if(id in ids)true else prefs.edit().putStringSet("paid",ids.apply { add(id) }).commit()
                if(saved)publish()
                saved
            }, onPrice={ price=it;publish() }, onBusy={ busy=it;publish() }, onMessage={ message=it;publish() })
        review=ReviewController(cordova.activity.applicationContext)
    }
    override fun execute(action: String,args: JSONArray,callback: CallbackContext): Boolean {
        if(action !in listOf("subscribe","purchase","review","download"))return false
        cordova.activity.runOnUiThread {
            try {
                initControllers()
                when(action) {
                    "subscribe" -> { listener=callback;publish();payments!!.refresh();review!!.prepare() }
                    "purchase" -> { if(busy||price==null)callback.error("Оплата пока недоступна") else {payments!!.purchase();callback.success()} }
                    "review" -> {
                        if(prefs.getBoolean("review",false))callback.success()
                        else review!!.start(onBonus={
                            if(prefs.edit().putBoolean("review",true).commit())callback.success() else callback.error("Не удалось сохранить бонус")
                        },onUnavailable={callback.error("Окно оценки RuStore сейчас недоступно")})
                    }
                    "download" -> {
                        val file=args.getString(0)
                        if(downloadCallback!=null)callback.error("Сохранение уже открыто")
                        else if(file.contains("..")||file.contains(":")||file.startsWith("/")||!file.endsWith(".mp4"))callback.error("Недопустимый файл")
                        else {
                            downloadCallback=callback;downloadSource=file
                            cordova.startActivityForResult(this,Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE);type="video/mp4";putExtra(Intent.EXTRA_TITLE,args.getString(1))
                            },7124)
                        }
                    }
                }
            } catch(error:Exception){callback.error(error.message ?: "Ошибка RuStore")}
        }
        return true
    }
    override fun onActivityResult(requestCode:Int,resultCode:Int,intent:Intent?) {
        if(requestCode!=7124)return
        val callback=downloadCallback ?: return
        val source=downloadSource
        downloadCallback=null;downloadSource=null
        val uri=intent?.data
        if(resultCode!=Activity.RESULT_OK||uri==null||source==null){callback.error("Сохранение отменено");return}
        val context=cordova.activity.applicationContext
        cordova.threadPool.execute {
            try {
                context.assets.open("www/$source").use { input ->
                    requireNotNull(context.contentResolver.openOutputStream(uri,"w")).use { input.copyTo(it) }
                }
                callback.success()
            }catch(error:Exception){runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver,uri) };callback.error("Не удалось сохранить видео")}
        }
    }
    override fun onNewIntent(intent:Intent) { runCatching { RuStorePayClient.instance.getIntentInteractor().proceedIntent(intent,sdkTheme=SdkTheme.DARK) } }
    override fun onResume(multitasking:Boolean) { payments?.refresh() }
    override fun onReset(){listener=null}
    override fun onDestroy(){payments?.close();review?.close();listener=null}
}
