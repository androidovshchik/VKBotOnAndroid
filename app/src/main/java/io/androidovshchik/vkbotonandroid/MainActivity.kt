package io.androidovshchik.vkbotonandroid

import android.annotation.SuppressLint
import android.os.Bundle
import com.github.androidovshchik.support.BaseV7PActivity

@SuppressLint("ExportedPreferenceActivity")
class MainActivity : BaseV7PActivity() {

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = SettingsFragment()
        fragmentManager.beginTransaction()
            .replace(android.R.id.content, settings)
            .commit()
        /*
        /what_i_want_to_say
/when_i_want_to_meet
/where_i_want_to_meet
/why_i_am_offline
/why_android_is_the_best
/what_are_latest_news
/will_i_be_back
/especially_for_anton
/especially_for_kabanchik
/especially_for_roman
/especially_for_kostya
/especially_for_dmitry
/especially_for_kostya
/especially_for_all
        */
    }
}
