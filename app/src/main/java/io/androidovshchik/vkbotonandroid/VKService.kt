package io.androidovshchik.vkbotonandroid

import android.Manifest
import android.app.Service
import android.content.Intent
import com.github.androidovshchik.BaseService
import com.github.androidovshchik.utils.PermissionUtil
import com.github.androidovshchik.utils.PhoneUtil
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageException.ERROR_OBJECT_NOT_FOUND
import durdinapps.rxfirebase2.RxFirebaseStorage
import durdinapps.rxfirebase2.RxFirestore
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import rf.androidovshchik.photoboom.*
import rf.androidovshchik.photoboom.models.AppBot
import rf.androidovshchik.photoboom.models.InfoSettings
import rf.androidovshchik.vk_bot_android_sdk.callbacks.Callback
import rf.androidovshchik.vk_bot_android_sdk.clients.Client
import rf.androidovshchik.vk_bot_android_sdk.clients.User
import rf.androidovshchik.vk_bot_android_sdk.objects.Message
import rf.androidovshchik.vk_bot_android_sdk.utils.vkapi.API
import timber.log.Timber
import java.util.*
import java.util.concurrent.Executors
import java.util.regex.Pattern

class VKService : BaseService() {

    private var client: User? = null

    private var requests: ArrayList<Int> = ArrayList()

    private val patternCode = Pattern.compile("^[0-9a-z]+\$", Pattern.CASE_INSENSITIVE)

    override fun onCreate() {
        super.onCreate()
        acquireWakeLock()
        Client.service = Executors.newCachedThreadPool()
        Client.scheduler = Executors.newSingleThreadScheduledExecutor()
        API.executionStarted = false
        startForeground(NOTIFICATION_VK_ID, "Фоновая переписка VK", R.drawable.ic_vk)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (!hasConditions()) {
            if (isChiefBot()) {
                showToast("Требуются данные для работы с VK")
            }
            stopWork()
            return Service.START_NOT_STICKY
        }
        disposable.add(RxFirestore.observeDocumentRef(FirebaseFirestore.getInstance()
            .collection("info")
            .document("settings"))
            .subscribe({
                val info = it.toObject(InfoSettings::class.java) ?: return@subscribe
                Timber.d(info.toString())
                preferences.saveAppSettings(info)
                if (!isChiefBot() || info.version > BuildConfig.VERSION_CODE) {
                    preferences.putBoolean(PREFERENCE_ENABLE_VK, false)
                    stopWork()
                }
            }) { throwable ->
                Timber.e(throwable)
            })
        disposable.add(Observable.fromCallable {
            client = User(preferences.getString(PREFERENCE_VK_ID).toInt(), preferences.getVKToken())
            client!!.enableLoggingUpdates(true)
            client!!.enableTyping(true)
            client!!.onMessage { message ->
                if (message.isMessageFromChat) {
                    Message().from(client)
                        .to(message.chatIdLong)
                        .text("Извините, работаю только в личных сообщениях")
                        .send()
                    client!!.chat(message.chatIdLong)
                        .kickUser(client!!.id)
                    return@onMessage
                }
                val text = message.text.trim()
                if (text.length == 6 && patternCode.matcher(text).matches()) {
                    if (requests.size >= 3) {
                        Message().from(client)
                            .to(message.authorId())
                            .text("Подождите, много запросов накопилось...")
                            .forwardedMessages(message.messageId)
                            .send()
                    } else if (!requests.contains(message.chatIdLong)) {
                        requests.add(message.chatIdLong)
                        Message().from(client)
                            .to(message.authorId())
                            .text("Подождите, выполняю текущий запрос...")
                            .forwardedMessages(message.messageId)
                            .send()
                        tryFindGif(true, message, text.toLowerCase())
                    } else {
                        Message().from(client)
                            .to(message.authorId())
                            .text("Подождите, обрабатываю предыдущий запрос...")
                            .forwardedMessages(message.messageId)
                            .send()
                    }
                } else {
                    Message().from(client)
                        .to(message.authorId())
                        .text(preferences.getString(PREFERENCE_TEMPLATE_DEFAULT_VK))
                        .send()
                }
            }
        }.subscribeOn(Schedulers.io())
            .subscribe())
        return Service.START_NOT_STICKY
    }

    private fun tryFindGif(firstAttempt: Boolean, message: Message, code: String) {
        Timber.d("Trying to find gif")
        val calendar = Calendar.getInstance()
        var year = calendar.get(Calendar.YEAR)
        var month = calendar.get(Calendar.MONTH)
        if (!firstAttempt) {
            if (month == Calendar.JANUARY) {
                month = Calendar.UNDECIMBER
                year--
            }
        } else {
            month++
        }
        RxFirebaseStorage.getDownloadUrl(FirebaseStorage.getInstance().reference
            .child(year.toString())
            .child(month.toString())
            .child(code)
            .child("image.gif"))
            .subscribe({
                Timber.d("Found gif")
                getGifMetadata(year, month, message, code, it.toString())
            }, {
                if ((it as StorageException).errorCode == ERROR_OBJECT_NOT_FOUND) {
                    if (!firstAttempt) {
                        requests.remove(message.chatIdLong)
                        onError(it, message)
                    } else {
                        Timber.e(it)
                        tryFindGif(false, message, code)
                    }
                } else {
                    requests.remove(message.chatIdLong)
                    onError(it, message)
                }
            })
    }

    private fun getGifMetadata(year: Int, month: Int, message: Message, code: String, gifLink: String) {
        Timber.d("Getting gif metadata")
        RxFirebaseStorage.getMetadata(FirebaseStorage.getInstance().reference
            .child(year.toString())
            .child(month.toString())
            .child(code)
            .child("image.gif"))
            .observeOn(Schedulers.io())
            .subscribe({
                val botImei = it.getCustomMetadata("bot")
                if (botImei != null) {
                    Timber.d("Bot imei exists $botImei")
                    getBotSettings(year, month, message, code, gifLink, botImei)
                } else {
                    Timber.d("Not found bot imei")
                    sendAnswer(year, month, message, code, gifLink, "")
                }
            }, {
                Timber.e(it)
                sendAnswer(year, month, message, code, gifLink, "")
            })
    }

    private fun getBotSettings(year: Int, month: Int, message: Message, code: String, gifLink: String, botImei: String) {
        Timber.d("Getting bot settings")
        RxFirestore.getDocument(FirebaseFirestore.getInstance()
            .collection("bots")
            .document(botImei))
            .observeOn(Schedulers.io())
            .subscribe({
                val bot = it.toObject(AppBot::class.java) ?: return@subscribe
                Timber.d(bot.toString())
                sendAnswer(year, month, message, code, gifLink, bot.templateVk ?: "")
            }) {
                Timber.e(it)
                sendAnswer(year, month, message, code, gifLink, "")
            }
    }

    private fun sendAnswer(year: Int, month: Int, message: Message, code: String, gifLink: String, botTemplate: String) {
        Message().from(client)
            .to(message.authorId())
            .text(preferences.getTemplateVk(botTemplate, year, month, code, gifLink))
            .forwardedMessages(message.messageId)
            .send(Callback<Any> {
                Timber.d("Finished request")
                requests.remove(message.chatIdLong)
            })
    }

    private fun onError(throwable: StorageException, message: Message) {
        Timber.e(throwable)
        when (throwable.errorCode) {
            StorageException.ERROR_OBJECT_NOT_FOUND -> {
                Message().from(client)
                    .to(message.authorId())
                    .text(preferences.getString(PREFERENCE_TEMPLATE_ERROR_VK))
                    .send()
            }
            else -> {
                Message().from(client)
                    .to(message.authorId())
                    .text("Не удалось выполнить запрос. Попробуйте еще раз")
                    .send()
            }
        }
    }

    private fun hasConditions(): Boolean {
        return isChiefBot() && preferences.hasPreferences(PREFERENCE_VK_ID, PREFERENCE_VK_TOKEN,
            PREFERENCE_TEMPLATE_DEFAULT_VK, PREFERENCE_TEMPLATE_SUCCESS_VK, PREFERENCE_TEMPLATE_ERROR_VK) &&
            contentResolver.isSystemTimeValid()
    }

    private fun isChiefBot(): Boolean {
        return PermissionUtil.isGranted(applicationContext, Manifest.permission.READ_PHONE_STATE) &&
            preferences.getBoolean(PREFERENCE_ENABLE_VK) && preferences.getString(PREFERENCE_CHIEF) ==
            PhoneUtil.imei(applicationContext)
    }

    override fun onDestroy() {
        super.onDestroy()
        client?.longPoll()?.off()
        Client.service.shutdownNow()
        Client.scheduler.shutdownNow()
        Client.service = null
        Client.scheduler = null
        Client.api = null
        API.executor = null
    }
}