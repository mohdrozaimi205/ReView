package com.review.mediamanager.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.review.mediamanager.AppContainer
import com.review.mediamanager.ReViewApp

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as ReViewApp).container

@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer) -> VM): VM {
    val c = appContainer()
    return viewModel(factory = viewModelFactory { initializer { create(c) } })
}

/** Small helper so composables can find the hosting activity. */
fun android.content.Context.findActivity(): android.app.Activity? {
    var c = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}
