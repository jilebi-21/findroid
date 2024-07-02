package dev.jdtech.jellyfin.utils

import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import dev.jdtech.jellyfin.AppNavigationDirections
import timber.log.Timber

fun Fragment.checkIfLoginRequired(error: String?) {
    if (error != null) {
        if (error.contains("401")) {
            Timber.d("Login required!")
            findNavController().navigate(AppNavigationDirections.actionGlobalLoginFragment(reLogin = true))
        }
    }
}

fun View.hideByFade(duration: Long = 150) {
    animate()
        .alpha(0f)
        .setDuration(duration)
        .withEndAction {
            isVisible = false
        }
        .start()
}

fun View.showByFade(duration: Long = 150) {
    isVisible = true
    animate()
        .alpha(1f)
        .setDuration(duration)
        .start()
}
